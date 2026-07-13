package no.nav.amt.internapi.journalforing.pdf

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import java.time.LocalDate

data class EnkeltplassPdfDto(
    val deltaker: DeltakerDto,
    val deltakerliste: DeltakerlisteDto,
    val avsender: AvsenderDto,
    val opprettetDato: LocalDate,
    val innholdFritekst: String,
    val deltakelsesmengdeAntallDager: Int?,
    val innhold: EnkeltplassInnhold,
    val prisinformasjon: Prisinformasjon,
) {
    data class DeltakerDto(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
        val personident: String,
    )

    data class DeltakerlisteDto(
        val tiltaksnavn: String,
        val arrangornavn: String,
        val startdato: LocalDate,
        val sluttdato: LocalDate,
        val oppstartstype: Oppstartstype,
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    sealed interface Prisinformasjon {
        data class Anskaffelse(
            val pris: Int,
        ) : Prisinformasjon

        data class Tilskudd(
            val tilskudd: List<TilskuddInfo>,
            val tilleggsopplysninger: String?,
        ) : Prisinformasjon {
            data class TilskuddInfo(
                val type: String,
                val pris: Int,
            )

            @get:JsonProperty
            val totalpris: Int get() = tilskudd.sumOf { it.pris }
        }

        object IngenKostnader : Prisinformasjon

        data class Innbyggerfinansiert(
            val tilleggsopplysninger: String,
        ) : Prisinformasjon
    }

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
