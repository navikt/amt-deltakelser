package no.nav.amt.lib.models.deltaker.deltakelsesmengde

import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import java.time.LocalDate
import java.util.Objects

/**
 * Deltakelsesmengder er en tidslinje med alle gyldige deltakelsesmengder, sortert på gyldig-fra stigende.
 *
 * For samme gyldig-fra-dato beholdes den sist opprettede endringen. Endringer med senere gyldig-fra-dato
 * beholdes slik at en tilbakedatert endring ikke fjerner framtidige perioder. Når en startdato er satt,
 * justeres den første perioden slik at den ikke begynner før startdatoen.
 */
class Deltakelsesmengder private constructor(
    mengder: List<Deltakelsesmengde>,
    private val startdato: LocalDate?,
    private val rawMengder: List<Deltakelsesmengde>,
    private val raaperioderForTidslinje: List<Deltakelsesmengde>,
) {
    /**
     * Oppretter en tidslinje fra deltakelsesmengdene og en valgfri startdato.
     *
     * @param mengder deltakelsesmengdene som skal sorteres og normaliseres
     * @param startdato startdatoen som avgrenser den første perioden, hvis satt
     */
    constructor(
        mengder: List<Deltakelsesmengde>,
        startdato: LocalDate? = null,
    ) : this(mengder, startdato, mengder, mengder)

    /** Gyldige perioder i tidslinjen, sortert etter gyldig-fra. */
    val perioder: List<Deltakelsesmengde> = mengder
        .let(::finnGyldigeDeltakelsesmengder)
        .let { avgrensPeriodeTilStartdato(it, startdato) }
        .toList()

    /**
     * Perioden som gjelder i dag, eller den første perioden dersom alle periodene ligger fram i tid.
     */
    val gjeldende = perioder.aktivPaaDato(LocalDate.now()) ?: perioder.firstOrNull()

    /** Perioden etter [gjeldende], eller `null` hvis det ikke finnes en senere periode. */
    val nesteGjeldende: Deltakelsesmengde?
        get() {
            val nesteGjeldendeIndex = perioder.indexOf(gjeldende) + 1

            if (perioder.size > nesteGjeldendeIndex) {
                return perioder[nesteGjeldendeIndex]
            }

            return null
        }

    /**
     * Returnerer en tidslinje der periodene er avgrenset til den angitte startdatoen.
     *
     * Når [startdato] er `null`, gjenopprettes perioder som tidligere ble avgrenset bort.
     *
     * @param startdato startdatoen som første periode skal gjelde fra, eller `null` for ingen avgrensning
     * @return en ny tidslinje avgrenset til startdatoen
     */
    fun avgrensPeriodeTilStartdato(startdato: LocalDate?) = Deltakelsesmengder(
        mengder = if (startdato == null) rawMengder else perioder,
        startdato = startdato,
        rawMengder = rawMengder,
        raaperioderForTidslinje = if (startdato == null) rawMengder else raaperioderForTidslinje,
    )

    /**
     * Legger til en deltakelsesmengde og beregner tidslinjen på nytt uten å miste senere perioder.
     *
     * @param deltakelsesmengde mengden som skal legges til
     * @return en ny tidslinje med den oppdaterte mengden
     */
    fun medNyDeltakelsesmengde(deltakelsesmengde: Deltakelsesmengde) = raaperioderForTidslinje.oppdaterEtterDeltakelsesmengde(
        alleMengder = rawMengder + deltakelsesmengde,
        nyDeltakelsesmengde = deltakelsesmengde,
        startdato = startdato,
    )

    /**
     * Finner periodene som gjelder fra og med [fraOgMed] til og med [tilOgMed].
     *
     * Den første perioden i resultatet begynner på [fraOgMed]. Hvis [tilOgMed] er `null`,
     * tas alle senere perioder med.
     *
     * @param fraOgMed første dato i perioden
     * @param tilOgMed siste dato i perioden, eller `null` for ingen sluttdato
     * @return tidslinjen avgrenset til den angitte perioden
     */
    fun periode(
        fraOgMed: LocalDate,
        tilOgMed: LocalDate?,
    ) = Deltakelsesmengder(
        mengder = periode(
            deltakelsesmengder = perioder,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
        ),
    )

    /**
     * Vurderer om en ny deltakelsesmengde endrer den gjeldende tidslinjen.
     *
     * @param deltakelsesmengde mengden som skal vurderes
     * @return `true` hvis det ikke finnes en aktiv mengde å sammenligne med, mengden er endret
     *         på gyldig-fra-datoen, eller den er tilbakedatert før den aktive perioden
     */
    fun validerNyDeltakelsesmengde(deltakelsesmengde: Deltakelsesmengde): Boolean {
        val aktivIDag = perioder.aktivPaaDato(LocalDate.now()) ?: return true
        val aktivPaNyDato = perioder.aktivPaaDato(deltakelsesmengde.gyldigFra) ?: return true

        val mengdeErEndretPaNyDato = !aktivPaNyDato.sammeMengdeSom(deltakelsesmengde)

        return mengdeErEndretPaNyDato || deltakelsesmengde.gyldigFra < aktivIDag.gyldigFra
    }

    private fun finnGyldigeDeltakelsesmengder(deltakelsesmengder: List<Deltakelsesmengde>): List<Deltakelsesmengde> {
        // Stabil sortering gjør at siste registrerte mengde vinner når opprettet og gyldigFra er like.
        val sistePerGyldigFra = deltakelsesmengder
            .sortedBy { it.opprettet }
            .associateBy { it.gyldigFra }

        return sistePerGyldigFra.values
            .sortedBy { it.gyldigFra }
            .fold(mutableListOf()) { perioder, periode ->
                if (perioder.lastOrNull()?.sammeMengdeSom(periode) != true) perioder.add(periode)
                perioder
            }
    }

    /**
     * Perioder skal ikke ha en gyldig fra før startdato til deltaker.
     *
     * Hvis startdato endres tilbake i tid skal den deltakelsesmengden som var gjeldende før startdatoendringen
     * ha en gyldig fra lik ny startdato, selv om det kan finnes en gyldig deltakelsesmengde som er før den gjeldende
     */
    private fun avgrensPeriodeTilStartdato(
        deltakelsesmengder: List<Deltakelsesmengde>,
        startdato: LocalDate?,
    ): List<Deltakelsesmengde> {
        if (deltakelsesmengder.isEmpty() || startdato == null) return deltakelsesmengder
        return justerGyldigFra(
            deltakelsesmengder = deltakelsesmengder,
            startdato = startdato,
        )
    }

    private fun justerGyldigFra(
        deltakelsesmengder: List<Deltakelsesmengde>,
        startdato: LocalDate,
    ): List<Deltakelsesmengde> {
        val initial = deltakelsesmengder.aktivPaaDato(startdato)
            ?: deltakelsesmengder.firstOrNull()
            ?: return emptyList()

        return listOf(initial.fraOgMed(startdato)) +
            deltakelsesmengder.filter { it !== initial && it.gyldigFra > startdato }
    }

    private fun periode(
        deltakelsesmengder: List<Deltakelsesmengde>,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate?,
    ): List<Deltakelsesmengde> {
        val originalInitial = deltakelsesmengder.aktivPaaDato(fraOgMed)

        // Juster gyldigFra til fraOgMed slik at den aldri er før startdato i det returnerte resultatet.
        // Dette håndterer tilfeller der mengden ble opprettet uten startdato (f.eks. gyldigFra = i dag)
        // og startdato senere ble satt til en fremtidig dato uten at historikk-folden justerte gyldigFra.
        val initialDeltakelsesmengde = originalInitial?.fraOgMed(fraOgMed)

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

    companion object {
        /**
         * Oppretter en tidslinje fra den ferdigbehandlede deltakerhistorikken.
         *
         * @param mengder periodene som vises i tidslinjen
         * @param rawMengder alle mengdene fra historikken, også de som er avgrenset bort
         * @param raaperioderForTidslinje råperiodene som brukes ved senere oppdateringer
         * @param startdato gjeldende startdato, hvis den finnes
         */
        internal fun fraHistorikk(
            mengder: List<Deltakelsesmengde>,
            rawMengder: List<Deltakelsesmengde>,
            raaperioderForTidslinje: List<Deltakelsesmengde>,
            startdato: LocalDate?,
        ) = Deltakelsesmengder(
            mengder = mengder,
            startdato = startdato,
            rawMengder = rawMengder,
            raaperioderForTidslinje = raaperioderForTidslinje,
        )
    }
}

/**
 * Lager en tidslinje av deltakelsesmengdene i historikken.
 *
 * @receiver historikkoppføringene som skal behandles
 * @param isForDeltakerExternalTopic om mengdene skal tilpasses den eksterne deltakerstrømmen
 * @return tidslinjen med gyldige deltakelsesmengder og eventuell startdatoavgrensning
 */
fun List<DeltakerHistorikk>.toDeltakelsesmengder(isForDeltakerExternalTopic: Boolean = false): Deltakelsesmengder =
    toDeltakelsesmengderFraHistorikk(isForDeltakerExternalTopic)

/**
 * Finner mengden som gjelder på datoen i en liste sortert etter gyldig-fra.
 *
 * @param dato datoen mengden skal gjelde på
 * @return siste mengde med gyldig-fra på eller før datoen, eller `null` hvis ingen gjelder ennå
 */
private fun List<Deltakelsesmengde>.aktivPaaDato(dato: LocalDate) = lastOrNull { it.gyldigFra <= dato }

/**
 * Returnerer mengden med gyldig-fra justert til den angitte datoen.
 *
 * Returnerer samme instans hvis gyldig-fra allerede er datoen; ellers returneres en kopi.
 *
 * @param dato datoen mengden skal gjelde fra
 * @return mengden med gyldig-fra lik datoen
 */
private fun Deltakelsesmengde.fraOgMed(dato: LocalDate) = if (gyldigFra == dato) this else copy(gyldigFra = dato)

/**
 * Sammenligner deltakelsesprosent og dager per uke, uten å sammenligne datoer eller opprettelsestidspunkt.
 *
 * @param annen mengden som skal sammenlignes med denne
 * @return `true` hvis begge mengdefeltene er like
 */
internal fun Deltakelsesmengde.sammeMengdeSom(annen: Deltakelsesmengde) =
    Objects.equals(deltakelsesprosent, annen.deltakelsesprosent) && Objects.equals(dagerPerUke, annen.dagerPerUke)

/**
 * Legger en ny mengde til råperiodene og beregner tidslinjen med hensyn til startdatoen.
 *
 * Hvis den nye mengden gjelder på eller før startdatoen, beregnes grunnmengden fra alle historiske
 * mengder, mens senere perioder beholdes fra råperiodene for tidslinjen.
 *
 * @param alleMengder alle kjente mengder fra historikken
 * @param nyDeltakelsesmengde mengden som legges til
 * @param startdato gjeldende startdato, hvis den finnes
 * @return den oppdaterte tidslinjen
 */
internal fun List<Deltakelsesmengde>.oppdaterEtterDeltakelsesmengde(
    alleMengder: List<Deltakelsesmengde>,
    nyDeltakelsesmengde: Deltakelsesmengde,
    startdato: LocalDate?,
): Deltakelsesmengder {
    if (startdato == null || nyDeltakelsesmengde.gyldigFra > startdato) {
        return Deltakelsesmengder(
            mengder = this + nyDeltakelsesmengde,
            startdato = startdato,
        )
    }

    val grunnmengde = Deltakelsesmengder(
        mengder = alleMengder,
        startdato = startdato,
    ).perioder.firstOrNull()
    val senereMengder = filter { it.gyldigFra > startdato }

    return Deltakelsesmengder(
        mengder = listOfNotNull(grunnmengde) + senereMengder,
        startdato = startdato,
    )
}
