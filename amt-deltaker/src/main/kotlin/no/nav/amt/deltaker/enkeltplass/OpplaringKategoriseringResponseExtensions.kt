package no.nav.amt.deltaker.enkeltplass

import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse
import no.nav.amt.internapi.enkeltplass.ValgteKategoriseringerOgSertifiseringer
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

fun OpplaringKategoriseringResponse.toValgteKategoriseringerOgSertifiseringer(
    kodeverkValg: Set<UUID>,
    sertifiseringValg: Set<SertifiseringValg>,
): ValgteKategoriseringerOgSertifiseringer = if (kodeverkValg.isEmpty()) {
    ValgteKategoriseringerOgSertifiseringer(
        valgteKategoriseringer = emptySet(),
        valgteSertifiseringer = sertifiseringValg,
    )
} else {
    val kategoriseringResponseMedValgteElementer = settValgt(
        kodeverkValg = kodeverkValg,
        sertifiseringValg = sertifiseringValg,
    )

    ValgteKategoriseringerOgSertifiseringer(
        valgteKategoriseringer = kategoriseringResponseMedValgteElementer.alternativer
            .flatMap { it.tilValgteFelt() }
            .toSet(),
        valgteSertifiseringer = sertifiseringValg,
    )
}

private fun OpplaringKategoriseringResponse.Alternativ.Container.tilValgteFelt(): Set<ValgteKategoriseringerOgSertifiseringer.ValgteFelt> =
    when (this) {
        is OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe -> tilValgteFeltInternal()
        is OpplaringKategoriseringResponse.Alternativ.Verdigruppe -> tilValgteFeltInternal()
        is OpplaringKategoriseringResponse.Alternativ.VerdigruppeSok -> emptySet()
    }

private fun OpplaringKategoriseringResponse.Alternativ.Verdigruppe.tilValgteFeltInternal():
    Set<ValgteKategoriseringerOgSertifiseringer.ValgteFelt> {
    if (alternativer.none { it.valgt }) return emptySet()

    return setOf(
        ValgteKategoriseringerOgSertifiseringer.ValgteFelt(
            representerer = representerer,
            valg = alternativer
                .filter { verdi -> verdi.valgt }
                .associate { verdi -> verdi.id to verdi.visningsnavn },
        ),
    )
}

private fun OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.tilValgteFeltInternal():
    Set<ValgteKategoriseringerOgSertifiseringer.ValgteFelt> {
    val valgtUtdanningsgruppe = utdanninger
        .firstOrNull { utdanningValg ->
            utdanningValg.valgt || utdanningValg.larefag.alternativer.any { verdi -> verdi.valgt }
        }
        ?: return emptySet()

    val valgtUtdanningsprogram = ValgteKategoriseringerOgSertifiseringer.ValgteFelt(
        representerer = OpplaringKategoriseringResponse.Representerer.UTDANNINGSPROGRAM_ID,
        valg = mapOf(valgtUtdanningsgruppe.id to valgtUtdanningsgruppe.visningsnavn),
    )

    val valgteLarefag = ValgteKategoriseringerOgSertifiseringer.ValgteFelt(
        representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
        valg = valgtUtdanningsgruppe.larefag.alternativer
            .filter { verdi -> verdi.valgt }
            .associate { verdi -> verdi.id to verdi.visningsnavn },
    )

    return setOf(valgtUtdanningsprogram, valgteLarefag)
}
