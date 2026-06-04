package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.deltaker.bff.commonresponse.DeltakerlisteResponse
import no.nav.amt.lib.ktor.clients.kodeverk.OpplaringKategoriseringResponse
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

fun OpplaringKategoriseringResponse.tilUtflatetKodeverk(
    kodeverkValg: Set<UUID>,
    sertifiseringValg: Set<SertifiseringValg>,
): DeltakerlisteResponse.UtflatetKodeverk = if (kodeverkValg.isEmpty()) {
    DeltakerlisteResponse.UtflatetKodeverk(
        valgteKategoriseringer = emptySet(),
        valgteSertifiseringer = sertifiseringValg,
    )
} else {
    val kategoriseringResponseMedValgteElementer = settValgt(kodeverkValg, sertifiseringValg)

    DeltakerlisteResponse.UtflatetKodeverk(
        valgteKategoriseringer = kategoriseringResponseMedValgteElementer.alternativer.flatMap { it.tilValgteFelt() }.toSet(),
        valgteSertifiseringer = sertifiseringValg,
    )
}

private fun OpplaringKategoriseringResponse.Alternativ.Container.tilValgteFelt(): Set<DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt> =
    when (this) {
        is OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe -> tilValgteFeltInternal()
        is OpplaringKategoriseringResponse.Alternativ.Verdigruppe -> tilValgteFeltInternal()
        is OpplaringKategoriseringResponse.Alternativ.VerdigruppeSok -> emptySet()
    }

private fun OpplaringKategoriseringResponse.Alternativ.Verdigruppe.tilValgteFeltInternal():
    Set<DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt> {
    if (alternativer.none { it.valgt }) return emptySet()

    return setOf(
        DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
            representerer = representerer,
            valg = alternativer
                .filter { verdi -> verdi.valgt }
                .associate { verdi -> verdi.id to verdi.visningsnavn },
        ),
    )
}

private fun OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.tilValgteFeltInternal():
    Set<DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt> {
    val valgtUtdanningsgruppe = utdanninger
        .firstOrNull { utdanningValg -> utdanningValg.larefag.alternativer.any { verdi -> verdi.valgt } }
        ?: return emptySet()

    val valgtUtdanningsprogram = DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
        representerer = OpplaringKategoriseringResponse.Representerer.UTDANNINGSPROGRAM_ID,
        valg = mapOf(valgtUtdanningsgruppe.id to valgtUtdanningsgruppe.visningsnavn),
    )

    val valgteLarefag = DeltakerlisteResponse.UtflatetKodeverk.ValgteFelt(
        representerer = OpplaringKategoriseringResponse.Representerer.LAREFAG,
        valg = valgtUtdanningsgruppe.larefag.alternativer
            .filter { verdi -> verdi.valgt }
            .associate { verdi -> verdi.id to verdi.visningsnavn },
    )

    return setOf(valgtUtdanningsprogram, valgteLarefag)
}
