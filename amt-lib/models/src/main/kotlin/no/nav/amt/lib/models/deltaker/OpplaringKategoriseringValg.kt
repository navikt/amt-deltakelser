package no.nav.amt.lib.models.deltaker

import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

/**
 * Representerer valgte kategoriseringer og sertifiseringer for en deltaker i opplæring.
 *
 * Kategoriseringer og sertifiseringer holdes separat fordi de representerer ulike typer valg.
 * Benyttes både i API-er mot frontend og i interne API-er.
 *
 * @property valgteKategoriseringer valgte verdier gruppert etter kategoriseringstype.
 * @property valgteSertifiseringer sertifiseringene som er valgt.
 */
data class OpplaringKategoriseringValg(
    val valgteKategoriseringer: Set<ValgteFelt>,
    val valgteSertifiseringer: Set<SertifiseringValg>,
) {
    /**
     * Representerer valgte verdier for en kategoriseringstype.
     *
     * Sertifiseringer representeres separat gjennom [OpplaringKategoriseringValg.valgteSertifiseringer]
     * og kan derfor ikke angis som [representerer].
     *
     * @property representerer kategoriseringstypen de valgte verdiene tilhører.
     * @property valg mapping mellom ID-en til det valgte alternativet og visningsverdien.
     */
    data class ValgteFelt(
        val representerer: OpplaringKategoriseringType,
        val valg: Map<UUID, String>,
    ) {
        init {
            // Sertifiseringer finnes i valgteSertifiseringer. Denne sjekken er primært for
            // å unngå feil testoppsett.
            require(representerer != OpplaringKategoriseringType.SERTIFISERINGER) {
                "Sertifiseringer kan ikke representeres av ValgteFelt"
            }

            require(valg.isNotEmpty()) { "ValgteFelt må inneholde minst ett valg" }
        }
    }

    /**
     * Henter ut alle verdier for en gitt kategoriseringstype.
     *
     * @param representerer kategoriseringstype
     * @param throwIfEmpty whether to throw if no values found (default: true)
     * @return liste med verdier
     * @throws IllegalArgumentException hvis throwIfEmpty=true og ingen verdier funnet
     */
    fun hentVerdier(
        representerer: OpplaringKategoriseringType,
        throwIfEmpty: Boolean = true,
    ): List<String> {
        val verdier = if (representerer == OpplaringKategoriseringType.SERTIFISERINGER) {
            valgteSertifiseringer.map { it.navn }
        } else {
            valgteKategoriseringer
                .singleOrNull { it.representerer == representerer }
                ?.valg
                ?.values
                ?.toList()
                ?: emptyList()
        }

        if (verdier.isEmpty() && throwIfEmpty) {
            throw IllegalArgumentException("Ingen verdier funnet for representerer: $representerer")
        }

        return verdier
    }

    /**
     * Henter kategoriseringstypene det er valgt verdier for.
     *
     * @return et sett med kategoriseringstyper som har valgte verdier.
     */
    fun hentRepresenterer(): Set<OpplaringKategoriseringType> = valgteKategoriseringer
        .map { it.representerer }
        .toSet()
}
