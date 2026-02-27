package no.nav.amt.lib.models.deltaker

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.arrangor.melding.Forslag
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
/*
    Dette objektet representerer en endring på deltaker
    Det brukes til å populere deltakerhistorikk(sammen med EndringFraArrangor), og
    som intern domenemodell i amt-deltaker-bff (blant annet)
    https://www.figma.com/board/AIPGnJY7UEFfADbShTPBWh/Domenemodell-Endringer?node-id=0-1&p=f&t=QDpzR8FzLQuPDWiY-0
    OBS! Deltakerhistorikk lagres som json i amt-deltaker-bff db så nye felter her må være optional
 */
data class DeltakerEndring(
    val id: UUID,
    val deltakerId: UUID,
    val endring: Endring,
    val endretAv: UUID,
    val endretAvEnhet: UUID,
    val endret: LocalDateTime,
    val forslag: Forslag?,
) {
    data class Aarsak(
        val type: Type,
        val beskrivelse: String? = null,
    ) {
        init {
            if (beskrivelse != null && type != Type.ANNET) {
                error("Aarsak $type skal ikke ha beskrivelse")
            }
        }

        enum class Type {
            SYK,
            FATT_JOBB,
            TRENGER_ANNEN_STOTTE,
            UTDANNING,
            IKKE_MOTT,
            ANNET,
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    sealed interface Endring {
        data class EndreBakgrunnsinformasjon(
            val bakgrunnsinformasjon: String?,
        ) : Endring

        data class EndreInnhold(
            val ledetekst: String?,
            val innhold: List<Innhold>,
        ) : Endring

        data class EndreDeltakelsesmengde(
            val deltakelsesprosent: Float?,
            val dagerPerUke: Float?,
            val gyldigFra: LocalDate?,
            val begrunnelse: String?,
        ) : Endring

        data class EndreStartdato(
            val startdato: LocalDate?,
            val sluttdato: LocalDate?,
            val begrunnelse: String?,
        ) : Endring

        data class EndreSluttdato(
            val sluttdato: LocalDate,
            val begrunnelse: String?,
        ) : Endring

        data class ForlengDeltakelse(
            val sluttdato: LocalDate,
            val begrunnelse: String?,
        ) : Endring

        data class IkkeAktuell(
            val aarsak: Aarsak,
            val begrunnelse: String?,
        ) : Endring

        data class AvsluttDeltakelse(
            val aarsak: Aarsak?,
            val sluttdato: LocalDate,
            val begrunnelse: String?,
            val harFullfort: Boolean?,
        ) : Endring

        data class EndreAvslutning(
            val aarsak: Aarsak?,
            val harFullfort: Boolean?,
            val sluttdato: LocalDate? = null,
            val begrunnelse: String?,
        ) : Endring

        data class AvbrytDeltakelse(
            val aarsak: Aarsak,
            val sluttdato: LocalDate,
            val begrunnelse: String?,
        ) : Endring

        data class EndreSluttarsak(
            val aarsak: Aarsak,
            val begrunnelse: String?,
        ) : Endring

        data class ReaktiverDeltakelse(
            val reaktivertDato: LocalDate,
            val begrunnelse: String,
        ) : Endring

        data class FjernOppstartsdato(
            val begrunnelse: String?,
        ) : Endring
    }
}
