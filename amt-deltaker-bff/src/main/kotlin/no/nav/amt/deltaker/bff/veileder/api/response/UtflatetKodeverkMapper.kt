package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.lib.ktor.clients.kodeverk.KodeverkResponse
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

private const val UTDANNINGSPROGRAM_VISNINGSNAVN = "Utdanningsprogram"
private const val BRANSJER_REPRESENTERER = "bransjer"
private const val KURSTYPE_REPRESENTERER = "kurstype"

private val REPRESENTERER_SOM_SKAL_VISE_TITTEL = setOf(
    BRANSJER_REPRESENTERER,
    KURSTYPE_REPRESENTERER,
)

fun KodeverkResponse.tilUtflatetKodeverk(
    kodeverkValg: Set<UUID>,
    sertifiseringValg: Set<SertifiseringValg>,
): DeltakerlisteResponse.UtflatetKodeverk {
    val tittelOgValg = settValgt(kodeverkValg, sertifiseringValg)
        .alternativer
        .map { it.tilTittelOgValg(sertifiseringValg) }

    return DeltakerlisteResponse.UtflatetKodeverk(
        tittel = tittelOgValg.firstOrNull { it.tittel.isNotBlank() }?.tittel ?: "",
        valg = tittelOgValg.flatMap { it.valg },
    )
}

private fun KodeverkResponse.Alternativ.Container.tilTittelOgValg(sertifiseringValg: Set<SertifiseringValg>): TittelOgValg = when (this) {
    is KodeverkResponse.Alternativ.Gruppe -> tilTittelOgValg()
    is KodeverkResponse.Alternativ.Verdigruppe -> TittelOgValg(
        tittel = if (representerer in REPRESENTERER_SOM_SKAL_VISE_TITTEL) visningsnavn else "",
        valg = valgteVisningsnavn(),
    )

    is KodeverkResponse.Alternativ.VerdigruppeSok -> TittelOgValg(
        tittel = "",
        valg = sertifiseringValg.map { it.navn },
    )
}

private fun KodeverkResponse.Alternativ.Gruppe.tilTittelOgValg(): TittelOgValg = if (visningsnavn == UTDANNINGSPROGRAM_VISNINGSNAVN) {
    TittelOgValg(
        tittel = visningsnavn,
        valg = alternativer
            .filterIsInstance<KodeverkResponse.Alternativ.Verdigruppe>()
            .flatMap { it.valgteVisningsnavn() },
    )
} else {
    TittelOgValg.empty()
}

private fun KodeverkResponse.Alternativ.Verdigruppe.valgteVisningsnavn() = alternativer
    .filter { it.valgt }
    .map { it.visningsnavn }

private data class TittelOgValg(
    val tittel: String,
    val valg: List<String>,
) {
    companion object {
        fun empty() = TittelOgValg(
            tittel = "",
            valg = emptyList(),
        )
    }
}
