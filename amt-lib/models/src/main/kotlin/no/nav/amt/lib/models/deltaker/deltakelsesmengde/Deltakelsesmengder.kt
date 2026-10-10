package no.nav.amt.lib.models.deltaker.deltakelsesmengde

import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import java.time.LocalDate
import java.util.Objects

/**
 * Deltakelsesmengder er en liste av alle gyldige deltakelsesmengder, både frem og tilbake i tid, den er sortert på gyldig-fra stigende.
 *
 * For samme gyldig-fra-dato beholdes den sist opprettede endringen. Endringer med senere gyldig-fra-dato
 * beholdes slik at en tilbakedatert endring ikke fjerner framtidige perioder.
 */
@Deprecated("Ikke bruk denne")
class Deltakelsesmengder(
    mengder: List<Deltakelsesmengde>,
    startdatoer: List<LocalDate> = emptyList(),
) : List<Deltakelsesmengde> {
    private val deltakelsesmengder = mengder
        .let(::sorterMengder)
        .let(::finnGyldigeDeltakelsesmengder)
        .let { avgrensPeriodeTilSisteStartdato(it, startdatoer) }

    val gjeldende = deltakelsesmengder.lastOrNull { it.gyldigFra <= LocalDate.now() } ?: deltakelsesmengder.firstOrNull()

    val nesteGjeldende: Deltakelsesmengde?
        get() {
            val nesteGjeldendeIndex = deltakelsesmengder.indexOf(gjeldende) + 1

            if (deltakelsesmengder.size > nesteGjeldendeIndex) {
                return deltakelsesmengder[nesteGjeldendeIndex]
            }

            return null
        }

    fun avgrensPeriodeTilStartdato(startdato: LocalDate?) = Deltakelsesmengder(
        mengder = deltakelsesmengder,
        startdatoer = listOfNotNull(startdato),
    )

    /**
     * Finner hvilke deltakelsesmengder som var gjeldende for perioden f.o.m. t.o.m.
     */
    fun periode(
        fraOgMed: LocalDate,
        tilOgMed: LocalDate?,
    ) = Deltakelsesmengder(
        mengder = periode(
            deltakelsesmengder = deltakelsesmengder,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
        ),
    )

    /**
     * Validerer om ny deltakelsesmengde fører til en endring av gjeldende deltakelsesmengder for hele deltakelsen eller ikke.
     */
    fun validerNyDeltakelsesmengde(deltakelsesmengde: Deltakelsesmengde): Boolean {
        val aktivIDag = deltakelsesmengder.lastOrNull { it.gyldigFra <= LocalDate.now() }
            ?: return true
        val aktivPaNyDato = deltakelsesmengder.lastOrNull {
            it.gyldigFra <= deltakelsesmengde.gyldigFra
        }
            ?: return true

        val mengdeErEndretPaNyDato = !(
            Objects.equals(
                aktivPaNyDato.dagerPerUke,
                deltakelsesmengde.dagerPerUke,
            ) && Objects.equals(aktivPaNyDato.deltakelsesprosent, deltakelsesmengde.deltakelsesprosent)
        )

        return mengdeErEndretPaNyDato || deltakelsesmengde.gyldigFra < aktivIDag.gyldigFra
    }

    private fun finnGyldigeDeltakelsesmengder(deltakelsesmengder: List<Deltakelsesmengde>): List<Deltakelsesmengde> {
        val sistePerGyldigFra = deltakelsesmengder
            .sortedBy { it.opprettet }
            .associateBy { it.gyldigFra }

        return sistePerGyldigFra.values
            .sortedBy { it.gyldigFra }
            .fold(mutableListOf<Deltakelsesmengde>()) { gyldigeDeltakelsesmengder, periode ->
                val forrige = gyldigeDeltakelsesmengder.lastOrNull()

                if (forrige == null ||
                    !Objects.equals(forrige.deltakelsesprosent, periode.deltakelsesprosent) ||
                    !Objects.equals(forrige.dagerPerUke, periode.dagerPerUke)
                ) {
                    gyldigeDeltakelsesmengder.add(periode)
                }

                gyldigeDeltakelsesmengder
            }
    }

    /**
     * Perioder skal ikke ha en gyldig fra før startdato til deltaker.
     *
     * Hvis startdato endres tilbake i tid skal den deltakelsesmengden som var gjeldende før startdatoendringen
     * ha en gyldig fra lik ny startdato, selv om det kan finnes en gyldig deltakelsesmengde som er før den gjeldende
     */
    private fun avgrensPeriodeTilSisteStartdato(
        deltakelsesmengder: List<Deltakelsesmengde>,
        startdatoer: List<LocalDate>,
    ): List<Deltakelsesmengde> {
        if (deltakelsesmengder.isEmpty() || startdatoer.isEmpty()) return deltakelsesmengder

        val sisteStartdato = startdatoer.lastOrNull() ?: return deltakelsesmengder
        return justerGyldigFra(
            deltakelsesmengder = deltakelsesmengder,
            startdato = sisteStartdato,
        )
    }

    private fun justerGyldigFra(
        deltakelsesmengder: List<Deltakelsesmengde>,
        startdato: LocalDate,
    ): List<Deltakelsesmengde> {
        val perioderForEllerPaStartdato = deltakelsesmengder
            .filter { it.gyldigFra <= startdato }
        val initial = perioderForEllerPaStartdato.maxByOrNull { it.gyldigFra }
            ?: deltakelsesmengder.minByOrNull { it.gyldigFra }
            ?: return emptyList()

        return listOf(initial.copy(gyldigFra = startdato)) +
            deltakelsesmengder.filter { it !== initial && it.gyldigFra > startdato }
    }

    private fun periode(
        deltakelsesmengder: List<Deltakelsesmengde>,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate?,
    ): List<Deltakelsesmengde> {
        // Finn den originale initialmengden (kan ha gyldigFra < fraOgMed)
        val originalInitial =
            deltakelsesmengder
                .filter { it.gyldigFra <= fraOgMed }
                .maxByOrNull { it.gyldigFra }

        // Juster gyldigFra til fraOgMed slik at den aldri er før startdato i det returnerte resultatet.
        // Dette håndterer tilfeller der mengden ble opprettet uten startdato (f.eks. gyldigFra = i dag)
        // og startdato senere ble satt til en fremtidig dato uten at historikk-folden justerte gyldigFra.
        val initialDeltakelsesmengde = originalInitial
            ?.let { if (it.gyldigFra < fraOgMed) it.copy(gyldigFra = fraOgMed) else it }

        val endringerIPerioden = deltakelsesmengder
            .filter {
                val mengdeErIPerioden = if (tilOgMed == null) {
                    it.gyldigFra > fraOgMed
                } else {
                    it.gyldigFra in fraOgMed..tilOgMed
                }

                it !== originalInitial && mengdeErIPerioden
            }

        return listOfNotNull(initialDeltakelsesmengde) + endringerIPerioden
    }

    /**
     * Man må sortere på opprettet her for at `finnGyldigeDeltakelsesmengder` skal gi riktig svar.
     */
    private fun sorterMengder(mengder: List<Deltakelsesmengde>): List<Deltakelsesmengde> = mengder.sortedWith(
        compareByDescending<Deltakelsesmengde> { it.opprettet }
            .thenByDescending { it.gyldigFra },
    )

    override fun equals(other: Any?): Boolean = other != null &&
        other is Deltakelsesmengder &&
        this.deltakelsesmengder == other.deltakelsesmengder

    override fun hashCode(): Int = this.deltakelsesmengder.hashCode()

    override val size: Int = deltakelsesmengder.size

    override fun contains(element: Deltakelsesmengde) = deltakelsesmengder.contains(element)

    override fun containsAll(elements: Collection<Deltakelsesmengde>) = deltakelsesmengder.containsAll(elements)

    override fun get(index: Int) = deltakelsesmengder[index]

    override fun isEmpty() = deltakelsesmengder.isEmpty()

    override fun iterator() = deltakelsesmengder.iterator()

    override fun indexOf(element: Deltakelsesmengde) = deltakelsesmengder.indexOf(element)

    override fun listIterator(): ListIterator<Deltakelsesmengde> = deltakelsesmengder.listIterator()

    override fun listIterator(index: Int): ListIterator<Deltakelsesmengde> = deltakelsesmengder.listIterator(index)

    override fun subList(
        fromIndex: Int,
        toIndex: Int,
    ) = deltakelsesmengder.subList(fromIndex, toIndex)

    override fun lastIndexOf(element: Deltakelsesmengde) = deltakelsesmengder.lastIndexOf(element)
}

// Filtrerer ut deltakelsesmengder og returnerer et Deltakelsesmengder-objekt
fun List<DeltakerHistorikk>.toDeltakelsesmengder(isForDeltakerExternalTopic: Boolean = false): Deltakelsesmengder {
    val historyState = sortedBy { it.sistEndret }
        .fold(DeltakelsesmengderHistoryState()) { state, historikk ->
            val deltakelsesmengde = if (isForDeltakerExternalTopic) {
                historikk.toDeltakelsesmengdeEkstern()
            } else {
                historikk.toDeltakelsesmengde()
            }

            val startdatoOppdatering = historikk.toStartdatoOppdatering()
            val nyStartdato = startdatoOppdatering?.startdato
            val effektivStartdato = nyStartdato ?: state.startdato
            // Behold full historikk separat fra periodene som fortsatt gjelder etter startdatojusteringer.
            val rawMengder = state.rawMengder + listOfNotNull(deltakelsesmengde)
            val raaperioderForTidslinje = state.raaperioderForTidslinje + listOfNotNull(deltakelsesmengde)
            val oppdaterteMengder = when {
                deltakelsesmengde != null ->
                    raaperioderForTidslinje.oppdaterEtterDeltakelsesmengde(
                        alleMengder = rawMengder,
                        nyDeltakelsesmengde = deltakelsesmengde,
                        startdato = effektivStartdato,
                    )

                startdatoOppdatering != null -> when {
                    // Fjerning av startdato opphever avgrensningen, så bygg tidslinjen fra råperiodene.
                    startdatoOppdatering.startdato == null ->
                        Deltakelsesmengder(rawMengder)

                    // Ved tilbakedatering beholdes mengden som gjaldt før endringen og senere perioder.
                    state.startdato != null && startdatoOppdatering.startdato < state.startdato ->
                        state.deltakelsesmengder.flyttStartdatoTilbake(
                            forrigeStartdato = state.startdato,
                            nyStartdato = startdatoOppdatering.startdato,
                        )

                    else ->
                        state.deltakelsesmengder.avgrensPeriodeTilStartdato(startdatoOppdatering.startdato)
                }

                else -> state.deltakelsesmengder
            }
            val oppdaterteRaaperioderForTidslinje = when {
                startdatoOppdatering != null && startdatoOppdatering.startdato == null -> rawMengder
                nyStartdato != null && deltakelsesmengde == null ->
                    raaperioderForTidslinje.oppdaterEtterStartdato(
                        oppdaterteMengder = oppdaterteMengder,
                        forrigeStartdato = state.startdato,
                        nyStartdato = nyStartdato,
                    )

                else -> raaperioderForTidslinje
            }

            DeltakelsesmengderHistoryState(
                rawMengder = rawMengder,
                raaperioderForTidslinje = oppdaterteRaaperioderForTidslinje,
                startdato = if (startdatoOppdatering != null) {
                    startdatoOppdatering.startdato
                } else {
                    state.startdato
                },
                deltakelsesmengder = oppdaterteMengder,
            )
        }

    return historyState.deltakelsesmengder
}

private data class DeltakelsesmengderHistoryState(
    val rawMengder: List<Deltakelsesmengde> = emptyList(),
    val raaperioderForTidslinje: List<Deltakelsesmengde> = emptyList(),
    val startdato: LocalDate? = null,
    val deltakelsesmengder: Deltakelsesmengder = Deltakelsesmengder(emptyList()),
)

private fun List<Deltakelsesmengde>.oppdaterEtterDeltakelsesmengde(
    alleMengder: List<Deltakelsesmengde>,
    nyDeltakelsesmengde: Deltakelsesmengde,
    startdato: LocalDate?,
): Deltakelsesmengder {
    if (startdato == null || nyDeltakelsesmengde.gyldigFra > startdato) {
        return Deltakelsesmengder(
            mengder = this + nyDeltakelsesmengde,
            startdatoer = listOfNotNull(startdato),
        )
    }

    val grunnmengde = Deltakelsesmengder(
        mengder = alleMengder,
        startdatoer = listOf(startdato),
    ).firstOrNull()
    val senereMengder = filter { it.gyldigFra > startdato }

    return Deltakelsesmengder(
        mengder = listOfNotNull(grunnmengde) + senereMengder,
        startdatoer = listOf(startdato),
    )
}

private fun List<Deltakelsesmengde>.oppdaterEtterStartdato(
    oppdaterteMengder: Deltakelsesmengder,
    forrigeStartdato: LocalDate?,
    nyStartdato: LocalDate,
): List<Deltakelsesmengde> {
    val startdatoGrense = maxOf(forrigeStartdato ?: nyStartdato, nyStartdato)
    return oppdaterteMengder + filter { raamengde ->
        raamengde.gyldigFra > startdatoGrense &&
            oppdaterteMengder.none {
                it == raamengde ||
                    (
                        it.opprettet == raamengde.opprettet &&
                            it.deltakelsesprosent == raamengde.deltakelsesprosent &&
                            it.dagerPerUke == raamengde.dagerPerUke
                    )
            }
    }
}

private fun Deltakelsesmengder.flyttStartdatoTilbake(
    forrigeStartdato: LocalDate,
    nyStartdato: LocalDate,
): Deltakelsesmengder {
    val aktivMengde = lastOrNull { it.gyldigFra <= forrigeStartdato } ?: firstOrNull()
        ?: return Deltakelsesmengder(emptyList())

    return Deltakelsesmengder(
        mengder = listOf(aktivMengde.copy(gyldigFra = nyStartdato)) +
            filter { it !== aktivMengde && it.gyldigFra > nyStartdato },
    )
}

private fun DeltakerHistorikk.toDeltakelsesmengde() = when (this) {
    is DeltakerHistorikk.ImportertFraArena -> this.importertFraArena.toDeltakelsesmengde()
    is DeltakerHistorikk.Endring -> this.endring.toDeltakelsesmengde()
    is DeltakerHistorikk.Vedtak -> this.vedtak.toDeltakelsesmengde()
    else -> null
}

private fun DeltakerHistorikk.toStartdatoOppdatering(): StartdatoOppdatering? = when (this) {
    is DeltakerHistorikk.Endring -> when (val endring = this.endring.endring) {
        is DeltakerEndring.Endring.EndreStartdato -> StartdatoOppdatering(endring.startdato)
        is DeltakerEndring.Endring.FjernOppstartsdato -> StartdatoOppdatering(null)
        else -> null
    }

    is DeltakerHistorikk.EndringFraArrangor ->
        if (this.endringFraArrangor.endring is EndringFraArrangor.LeggTilOppstartsdato) {
            StartdatoOppdatering(this.endringFraArrangor.endring.startdato)
        } else {
            null
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

private data class StartdatoOppdatering(
    val startdato: LocalDate?,
)
