package no.nav.amt.lib.models.journalforing.pdf

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import java.time.LocalDate

data class EnkeltplassInnsokingsbrevPdfDto(
    val deltaker: DeltakerDto,
    val deltakerliste: DeltakerlisteDto,
    val avsender: AvsenderDto,
    val opprettetDato: LocalDate,
    val innhold: EnkeltplassInnhold,
    val innholdFritekst: String,
    val tiltaksnavn: String,
    val arrangorNavn: String,
    val deltakelsesmengdeAntallDager: Int,
) {
    data class DeltakerDto(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
        val personident: String,
    )

    data class DeltakerlisteDto(
        val startdato: LocalDate?,
        val sluttdato: LocalDate?,
        val oppstartstype: Oppstartstype,
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    sealed interface EnkeltplassInnhold {
        data class Arbeidsmarkedsopplaering(
            val bransje: String,
            val forerkortOgSertifiseringer: List<String>,
        ) : EnkeltplassInnhold

        data class FagOgYrkesopplaering(
            val utdanningsprogram: String,
            val laerefag: List<String>,
        ) : EnkeltplassInnhold

        object UtenInnhold : EnkeltplassInnhold
    }
}
