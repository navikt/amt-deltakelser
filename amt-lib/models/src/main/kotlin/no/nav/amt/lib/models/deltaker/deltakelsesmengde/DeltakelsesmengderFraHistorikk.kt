package no.nav.amt.lib.models.deltaker.deltakelsesmengde

import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import java.time.LocalDate

/**
 * Bygger tidslinjen med deltakelsesmengder fra deltakerhistorikken.
 *
 * Historikken behandles etter tidspunktet den sist ble endret, slik at startdatoendringer
 * påvirker både gjeldende og framtidige perioder. Valget av intern eller ekstern tidslinje
 * bestemmer hvordan deltakelsesmengder fra historikken konverteres.
 *
 * @receiver historikkoppføringer som skal flettes til én tidslinje
 * @param isForDeltakerExternalTopic om tidslinjen skal bygges for eksternt deltakeremne
 * @return tidslinjen med deltakelsesmengder og gjeldende startdato
 */
internal fun List<DeltakerHistorikk>.toDeltakelsesmengderFraHistorikk(isForDeltakerExternalTopic: Boolean): Deltakelsesmengder {
    val state = sortedBy { it.sistEndret }
        .fold(DeltakelsesmengderHistoryState()) { state, historikk ->
            state.oppdater(historikk, isForDeltakerExternalTopic)
        }

    return Deltakelsesmengder.fraHistorikk(
        mengder = state.deltakelsesmengder.perioder,
        rawMengder = state.rawMengder,
        raaperioderForTidslinje = state.raaperioderForTidslinje,
        startdato = state.startdato,
    )
}

/**
 * Holder tilstanden mens historikken flettes kronologisk.
 *
 * Råmengdene bevares separat fra periodene i tidslinjen, slik at endringer i startdato
 * kan avgrense eller gjenopprette perioder uten å miste historikk.
 */
private data class DeltakelsesmengderHistoryState(
    /** Alle deltakelsesmengder fra historikken, også perioder som er avgrenset bort. */
    val rawMengder: List<Deltakelsesmengde> = emptyList(),
    /** Råperiodene som brukes til å bygge tidslinjen etter startdatoendringer. */
    val raaperioderForTidslinje: List<Deltakelsesmengde> = emptyList(),
    /** Startdatoen som sist ble lest fra historikken. */
    val startdato: LocalDate? = null,
    /** Tidslinjen etter at startdatoen er brukt som avgrensning. */
    val deltakelsesmengder: Deltakelsesmengder = Deltakelsesmengder(emptyList()),
) {
    /**
     * Fletter én historikkoppføring inn i tilstanden og beregner oppdaterte perioder.
     *
     * @param historikk oppføringen som skal behandles
     * @param isForDeltakerExternalTopic om mengden skal konverteres for eksternt deltakeremne
     * @return ny tilstand med oppdatert historikk, startdato og tidslinje
     */
    fun oppdater(
        historikk: DeltakerHistorikk,
        isForDeltakerExternalTopic: Boolean,
    ): DeltakelsesmengderHistoryState {
        val nyMengde = if (isForDeltakerExternalTopic) {
            historikk.toDeltakelsesmengdeEkstern()
        } else {
            historikk.toDeltakelsesmengde()
        }

        val startdatoOppdatering = historikk.toStartdatoOppdatering()
        val nyStartdato = startdatoOppdatering?.startdato
        val effektivStartdato = if (startdatoOppdatering != null) nyStartdato else startdato
        // Behold full historikk separat fra periodene som fortsatt gjelder etter startdatojusteringer.
        val alleMengder = rawMengder + listOfNotNull(nyMengde)
        val raaperioder = raaperioderForTidslinje + listOfNotNull(nyMengde)
        val oppdaterteMengder = when {
            startdatoOppdatering != null && nyStartdato == null ->
                Deltakelsesmengder(alleMengder)

            nyMengde != null ->
                raaperioder.oppdaterEtterDeltakelsesmengde(
                    alleMengder = alleMengder,
                    nyDeltakelsesmengde = nyMengde,
                    startdato = effektivStartdato,
                )

            nyStartdato != null ->
                // Ved tilbakedatering beholdes mengden som gjaldt før endringen og senere perioder.
                if (startdato != null && nyStartdato < startdato) {
                    deltakelsesmengder.flyttStartdatoTilbake(
                        forrigeStartdato = startdato,
                        nyStartdato = nyStartdato,
                    )
                } else {
                    deltakelsesmengder.avgrensPeriodeTilStartdato(nyStartdato)
                }

            else -> deltakelsesmengder
        }
        val oppdaterteRaaperioderForTidslinje = when {
            startdatoOppdatering != null && startdatoOppdatering.startdato == null -> alleMengder
            nyMengde != null && effektivStartdato != null &&
                nyMengde.gyldigFra <= effektivStartdato ->
                listOfNotNull(oppdaterteMengder.perioder.firstOrNull()) +
                    raaperioder.filter { it.gyldigFra > effektivStartdato }

            nyMengde != null -> raaperioder
            else -> nyStartdato?.let {
                raaperioder.oppdaterEtterStartdato(
                    oppdaterteMengder = oppdaterteMengder,
                    forrigeStartdato = startdato,
                    nyStartdato = it,
                )
            } ?: raaperioder
        }

        return DeltakelsesmengderHistoryState(
            rawMengder = alleMengder,
            raaperioderForTidslinje = oppdaterteRaaperioderForTidslinje,
            startdato = if (startdatoOppdatering != null) startdatoOppdatering.startdato else startdato,
            deltakelsesmengder = oppdaterteMengder,
        )
    }
}

/**
 * Oppdaterer råperiodene for tidslinjen etter en startdatoendring.
 *
 * Perioder som allerede er avgrenset til tidslinjen beholdes, og senere råperioder
 * legges til når de ikke allerede er representert.
 */
private fun List<Deltakelsesmengde>.oppdaterEtterStartdato(
    oppdaterteMengder: Deltakelsesmengder,
    forrigeStartdato: LocalDate?,
    nyStartdato: LocalDate,
): List<Deltakelsesmengde> {
    val startdatoGrense = maxOf(forrigeStartdato ?: nyStartdato, nyStartdato)
    return oppdaterteMengder.perioder + filter { raamengde ->
        raamengde.gyldigFra > startdatoGrense &&
            oppdaterteMengder.perioder.none {
                it == raamengde || (it.opprettet == raamengde.opprettet && it.sammeMengdeSom(raamengde))
            }
    }
}

/**
 * Flytter startdatoen bakover og beholder mengden som gjaldt ved den forrige startdatoen
 * sammen med senere perioder.
 */
private fun Deltakelsesmengder.flyttStartdatoTilbake(
    forrigeStartdato: LocalDate,
    nyStartdato: LocalDate,
): Deltakelsesmengder {
    val aktivMengde = perioder.lastOrNull { it.gyldigFra <= forrigeStartdato } ?: perioder.firstOrNull()
        ?: return Deltakelsesmengder(emptyList())

    return Deltakelsesmengder(
        mengder = listOf(aktivMengde.copy(gyldigFra = nyStartdato)) +
            perioder.filter { it !== aktivMengde && it.gyldigFra > nyStartdato },
    )
}

/**
 * Henter deltakelsesmengden fra et vedtak, en arenaimport eller en historikkendring.
 *
 * Oppføringer uten deltakelsesprosent og dager per uke gir ingen mengde.
 */
private fun DeltakerHistorikk.toDeltakelsesmengde() = when (this) {
    is DeltakerHistorikk.ImportertFraArena -> this.importertFraArena.toDeltakelsesmengde()
    is DeltakerHistorikk.Endring -> when (val endring = this.endring.endring) {
        is DeltakerEndring.Endring.EndreDeltakelsesmengde ->
            endring
                .takeUnless { it.deltakelsesprosent == null && it.dagerPerUke == null }
                ?.let { this.endring.toDeltakelsesmengde() }

        else -> null
    }

    is DeltakerHistorikk.Vedtak -> this.vedtak.toDeltakelsesmengde()
    else -> null
}

/**
 * Henter startdatoendringen fra historikkoppføringen.
 *
 * En oppdatering med `null` som startdato betyr at startdatoen er fjernet.
 */
private fun DeltakerHistorikk.toStartdatoOppdatering(): StartdatoOppdatering? = when (this) {
    is DeltakerHistorikk.Endring -> when (val endring = this.endring.endring) {
        is DeltakerEndring.Endring.EndreStartdato -> StartdatoOppdatering(endring.startdato)
        is DeltakerEndring.Endring.FjernOppstartsdato -> StartdatoOppdatering(null)
        else -> null
    }

    is DeltakerHistorikk.EndringFraArrangor -> when (val endring = this.endringFraArrangor.endring) {
        is EndringFraArrangor.LeggTilOppstartsdato -> StartdatoOppdatering(endring.startdato)
    }

    is DeltakerHistorikk.InnsokPaaFellesOppstart -> null
    is DeltakerHistorikk.Forslag -> null
    is DeltakerHistorikk.ImportertFraArena ->
        StartdatoOppdatering(this.importertFraArena.deltakerVedImport.startdato)

    is DeltakerHistorikk.Vedtak -> StartdatoOppdatering(this.vedtak.deltakerVedVedtak.startdato)
    is DeltakerHistorikk.VurderingFraArrangor -> null
    is DeltakerHistorikk.EndringFraTiltakskoordinator -> null
    is DeltakerHistorikk.EnkeltplassOkonomiGodkjent -> null
}

/** Representerer en ny startdato, eller fjerning når [startdato] er `null`. */
private data class StartdatoOppdatering(
    val startdato: LocalDate?,
)
