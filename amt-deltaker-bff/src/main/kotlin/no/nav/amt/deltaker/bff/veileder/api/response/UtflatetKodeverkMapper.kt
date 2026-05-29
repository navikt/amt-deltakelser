package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.deltaker.bff.commonresponse.DeltakerlisteResponse
import no.nav.amt.lib.ktor.clients.kodeverk.OpplaringKategoriseringResponse
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

// Begge bruker valgt verdi som tittel — ikke kategorinavnet
private val REPRESENTERER_SOM_BRUKER_VALGT_VERDI_SOM_TITTEL = setOf(
    OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
    OpplaringKategoriseringResponse.Representerer.KURSTYPE_ID,
)

fun OpplaringKategoriseringResponse.tilUtflatetKodeverk(
    kodeverkValg: Set<UUID>,
    sertifiseringValg: Set<SertifiseringValg>,
): DeltakerlisteResponse.UtflatetKodeverk {
    val tittelOgValg = settValgt(kodeverkValg, sertifiseringValg)
        .alternativer
        .map { it.tilTittelOgValg(sertifiseringValg) }

    return DeltakerlisteResponse.UtflatetKodeverk(
        tiltakskode = tiltakskode,
        tittel = tittelOgValg.firstOrNull { it.tittel != null }?.tittel,
        valg = tittelOgValg.flatMap { it.valg },
        valgteKodeverkIder = kodeverkValg,
        valgteSertifiseringer = sertifiseringValg,
    )
}

private fun OpplaringKategoriseringResponse.Alternativ.Container.tilTittelOgValg(sertifiseringValg: Set<SertifiseringValg>): TittelOgValg =
    when (this) {
        is OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe -> tilTittelOgValg()
        is OpplaringKategoriseringResponse.Alternativ.Verdigruppe -> TittelOgValg(
            tittel = tittel(),
            valg = if (representerer in REPRESENTERER_SOM_BRUKER_VALGT_VERDI_SOM_TITTEL) emptyList() else valgteVisningsnavn(),
        )

        is OpplaringKategoriseringResponse.Alternativ.VerdigruppeSok -> TittelOgValg(
            tittel = null,
            valg = sertifiseringValg.map { it.navn },
        )
    }

private fun OpplaringKategoriseringResponse.Alternativ.UtdanningGruppe.tilTittelOgValg(): TittelOgValg {
    val valgtToppnivaaGruppe = utdanninger
        .firstOrNull { utdanningValg ->
            utdanningValg.larefag.alternativer.any { verdi -> verdi.valgt }
        }

    return TittelOgValg(
        tittel = valgtToppnivaaGruppe?.visningsnavn,
        valg = valgtToppnivaaGruppe
            ?.larefag
            ?.valgteVisningsnavn()
            ?: emptyList(),
    )
}

private fun OpplaringKategoriseringResponse.Alternativ.Verdigruppe.tittel(): String? =
    if (representerer in REPRESENTERER_SOM_BRUKER_VALGT_VERDI_SOM_TITTEL) {
        alternativer.firstOrNull { it.valgt }?.visningsnavn
    } else {
        null
    }

private fun OpplaringKategoriseringResponse.Alternativ.Verdigruppe.valgteVisningsnavn() = alternativer
    .filter { it.valgt }
    .map { it.visningsnavn }

private data class TittelOgValg(
    val tittel: String?,
    val valg: List<String>,
)
