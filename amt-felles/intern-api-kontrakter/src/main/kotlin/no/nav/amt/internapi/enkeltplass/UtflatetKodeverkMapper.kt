package no.nav.amt.internapi.enkeltplass

import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

fun OpplaringKategoriseringResponse.tilUtflatetKodeverk(
    kodeverkValg: Set<UUID>,
    sertifiseringValg: Set<SertifiseringValg>,
): UtflatetKodeverk = if (kodeverkValg.isEmpty()) {
    UtflatetKodeverk(
        valgteKategoriseringer = emptySet(),
        valgteSertifiseringer = sertifiseringValg,
    )
} else {
    val kategoriseringResponseMedValgteElementer = settValgt(
        kodeverkValg = kodeverkValg,
        sertifiseringValg = sertifiseringValg,
    )

    UtflatetKodeverk(
        valgteKategoriseringer = kategoriseringResponseMedValgteElementer.alternativer.flatMap { it.tilValgteFelt() }.toSet(),
        valgteSertifiseringer = sertifiseringValg,
    )
}

private fun OpplaringKategoriseringResponse.Alternativ.Container.tilValgteFelt(): Set<UtflatetKodeverk.ValgteFelt> = when (this) {
    is OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe -> tilValgteFeltInternal()
    is OpplaringKategoriseringResponse.Alternativ.Verdigruppe -> tilValgteFeltInternal()
    is OpplaringKategoriseringResponse.Alternativ.VerdigruppeSok -> emptySet()
}

private fun OpplaringKategoriseringResponse.Alternativ.Verdigruppe.tilValgteFeltInternal(): Set<UtflatetKodeverk.ValgteFelt> {
    if (alternativer.none { it.valgt }) return emptySet()

    return setOf(
        UtflatetKodeverk.ValgteFelt(
            representerer = representerer,
            valg = alternativer
                .filter { verdi -> verdi.valgt }
                .associate { verdi -> verdi.id to verdi.visningsnavn },
        ),
    )
}

private fun OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.tilValgteFeltInternal(): Set<UtflatetKodeverk.ValgteFelt> {
    val valgtUtdanningsgruppe = utdanninger
        .firstOrNull { utdanningValg ->
            utdanningValg.valgt || utdanningValg.larefag.alternativer.any { verdi -> verdi.valgt }
        }
        ?: return emptySet()

    val valgtUtdanningsprogram = UtflatetKodeverk.ValgteFelt(
        representerer = OpplaringKategoriseringResponse.Representerer.UTDANNINGSPROGRAM_ID,
        valg = mapOf(valgtUtdanningsgruppe.id to valgtUtdanningsgruppe.visningsnavn),
    )

    val valgteLarefag = UtflatetKodeverk.ValgteFelt(
        representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
        valg = valgtUtdanningsgruppe.larefag.alternativer
            .filter { verdi -> verdi.valgt }
            .associate { verdi -> verdi.id to verdi.visningsnavn },
    )

    return setOf(valgtUtdanningsprogram, valgteLarefag)
}
