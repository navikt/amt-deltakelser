package no.nav.amt.lib.models.deltaker

import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

/**
 * Benyttes både i APIer mot frontend, og i intern-APIer.
 *
 */
data class OpplaringKategoriseringValg(
    val valgteKategoriseringer: Set<ValgteFelt>,
    val valgteSertifiseringer: Set<SertifiseringValg>,
) {
    data class ValgteFelt(
        val representerer: OpplaringKategoriseringType,
        val valg: Map<UUID, String>,
    )

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

    fun hentRepresenterer(): Set<OpplaringKategoriseringType> = valgteKategoriseringer
        .map { it.representerer }
        .toSet()
}
