package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class DeltakerStatusResponse(
    val id: UUID,
    val type: DeltakerStatus.Type,
    val aarsak: Aarsak?,
    val gyldigFra: LocalDateTime,
    val gyldigTil: LocalDateTime?,
    val opprettet: LocalDateTime,
) {
    data class Aarsak(
        val type: DeltakerStatus.Aarsak.Type,
        val beskrivelse: String?,
    )
}

fun DeltakerStatus.toResponse() = DeltakerStatusResponse(
    id = id,
    type = type,
    aarsak = aarsak?.let {
        DeltakerStatusResponse.Aarsak(
            type = it.type,
            beskrivelse = it.beskrivelse,
        )
    },
    gyldigFra = gyldigFra,
    gyldigTil = gyldigTil,
    opprettet = opprettet,
)

data class DeltakelsesmengdeResponse(
    val deltakelsesprosent: Float,
    val dagerPerUke: Float?,
    val gyldigFra: LocalDate,
)

data class DeltakelsesmengderResponse(
    val nesteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
    val sisteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
)

fun no.nav.amt.internapi.deltaker.response.DeltakelsesmengderResponse.toResponse() = DeltakelsesmengderResponse(
    nesteDeltakelsesmengde = nesteDeltakelsesmengde?.let {
        DeltakelsesmengdeResponse(
            deltakelsesprosent = it.deltakelsesprosent,
            dagerPerUke = it.dagerPerUke,
            gyldigFra = it.gyldigFra,
        )
    },
    sisteDeltakelsesmengde = sisteDeltakelsesmengde?.let {
        DeltakelsesmengdeResponse(
            deltakelsesprosent = it.deltakelsesprosent,
            dagerPerUke = it.dagerPerUke,
            gyldigFra = it.gyldigFra,
        )
    },
)

sealed interface EndringFraArrangorEndringResponse {
    data class LeggTilOppstartsdato(
        val startdato: LocalDate,
        val sluttdato: LocalDate?,
    ) : EndringFraArrangorEndringResponse
}

fun no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor.Endring.toResponse(): EndringFraArrangorEndringResponse = when (this) {
    is no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor.LeggTilOppstartsdato ->
        EndringFraArrangorEndringResponse.LeggTilOppstartsdato(startdato = startdato, sluttdato = sluttdato)
}

sealed interface EndringFraTiltakskoordinatorEndringResponse {
    data object DelMedArrangor : EndringFraTiltakskoordinatorEndringResponse

    data object SettPaaVenteliste : EndringFraTiltakskoordinatorEndringResponse

    data object TildelPlass : EndringFraTiltakskoordinatorEndringResponse

    data class Avslag(
        val aarsak: Aarsak,
        val begrunnelse: String?,
    ) : EndringFraTiltakskoordinatorEndringResponse {
        data class Aarsak(
            val type: EndringFraTiltakskoordinator.Avslag.Aarsak.Type,
            val beskrivelse: String? = null,
        )
    }
}

fun EndringFraTiltakskoordinator.Endring.toResponse(): EndringFraTiltakskoordinatorEndringResponse = when (this) {
    EndringFraTiltakskoordinator.DelMedArrangor ->
        EndringFraTiltakskoordinatorEndringResponse.DelMedArrangor

    EndringFraTiltakskoordinator.SettPaaVenteliste ->
        EndringFraTiltakskoordinatorEndringResponse.SettPaaVenteliste

    EndringFraTiltakskoordinator.TildelPlass ->
        EndringFraTiltakskoordinatorEndringResponse.TildelPlass

    is EndringFraTiltakskoordinator.Avslag ->
        EndringFraTiltakskoordinatorEndringResponse.Avslag(
            aarsak = EndringFraTiltakskoordinatorEndringResponse.Avslag.Aarsak(
                type = aarsak.type,
                beskrivelse = aarsak.beskrivelse,
            ),
            begrunnelse = begrunnelse,
        )
}

sealed interface EndringAarsakResponse {
    data object Syk : EndringAarsakResponse

    data object FattJobb : EndringAarsakResponse

    data object TrengerAnnenStotte : EndringAarsakResponse

    data object Utdanning : EndringAarsakResponse

    data object IkkeMott : EndringAarsakResponse

    data class Annet(
        val beskrivelse: String,
    ) : EndringAarsakResponse
}

fun no.nav.amt.lib.models.arrangor.melding.EndringAarsak.toResponse(): EndringAarsakResponse = when (this) {
    no.nav.amt.lib.models.arrangor.melding.EndringAarsak.Syk -> EndringAarsakResponse.Syk
    no.nav.amt.lib.models.arrangor.melding.EndringAarsak.FattJobb -> EndringAarsakResponse.FattJobb
    no.nav.amt.lib.models.arrangor.melding.EndringAarsak.TrengerAnnenStotte -> EndringAarsakResponse.TrengerAnnenStotte
    no.nav.amt.lib.models.arrangor.melding.EndringAarsak.Utdanning -> EndringAarsakResponse.Utdanning
    no.nav.amt.lib.models.arrangor.melding.EndringAarsak.IkkeMott -> EndringAarsakResponse.IkkeMott
    is no.nav.amt.lib.models.arrangor.melding.EndringAarsak.Annet -> EndringAarsakResponse.Annet(beskrivelse)
}

sealed interface ForslagEndringResponse {
    data class ForlengDeltakelse(
        val sluttdato: LocalDate,
    ) : ForslagEndringResponse

    data class AvsluttDeltakelse(
        val sluttdato: LocalDate?,
        val aarsak: EndringAarsakResponse?,
        val harDeltatt: Boolean?,
        val harFullfort: Boolean?,
    ) : ForslagEndringResponse

    data class EndreAvslutning(
        val aarsak: EndringAarsakResponse?,
        val harDeltatt: Boolean?,
        val harFullfort: Boolean?,
        val sluttdato: LocalDate? = null,
    ) : ForslagEndringResponse

    data class IkkeAktuell(
        val aarsak: EndringAarsakResponse,
    ) : ForslagEndringResponse

    data class Deltakelsesmengde(
        val deltakelsesprosent: Int,
        val dagerPerUke: Int?,
        val gyldigFra: LocalDate?,
    ) : ForslagEndringResponse

    data class Startdato(
        val startdato: LocalDate,
        val sluttdato: LocalDate?,
    ) : ForslagEndringResponse

    data class Sluttdato(
        val sluttdato: LocalDate,
    ) : ForslagEndringResponse

    data class Sluttarsak(
        val aarsak: EndringAarsakResponse,
    ) : ForslagEndringResponse

    data object FjernOppstartsdato : ForslagEndringResponse
}

fun no.nav.amt.lib.models.arrangor.melding.Forslag.Endring.toResponse(): ForslagEndringResponse = when (this) {
    is no.nav.amt.lib.models.arrangor.melding.Forslag.ForlengDeltakelse ->
        ForslagEndringResponse.ForlengDeltakelse(sluttdato = sluttdato)

    is no.nav.amt.lib.models.arrangor.melding.Forslag.AvsluttDeltakelse ->
        ForslagEndringResponse.AvsluttDeltakelse(
            sluttdato = sluttdato,
            aarsak = aarsak?.toResponse(),
            harDeltatt = harDeltatt,
            harFullfort = harFullfort,
        )

    is no.nav.amt.lib.models.arrangor.melding.Forslag.EndreAvslutning ->
        ForslagEndringResponse.EndreAvslutning(
            aarsak = aarsak?.toResponse(),
            harDeltatt = harDeltatt,
            harFullfort = harFullfort,
            sluttdato = sluttdato,
        )

    is no.nav.amt.lib.models.arrangor.melding.Forslag.IkkeAktuell ->
        ForslagEndringResponse.IkkeAktuell(aarsak = aarsak.toResponse())

    is no.nav.amt.lib.models.arrangor.melding.Forslag.Deltakelsesmengde ->
        ForslagEndringResponse.Deltakelsesmengde(
            deltakelsesprosent = deltakelsesprosent,
            dagerPerUke = dagerPerUke,
            gyldigFra = gyldigFra,
        )

    is no.nav.amt.lib.models.arrangor.melding.Forslag.Startdato ->
        ForslagEndringResponse.Startdato(startdato = startdato, sluttdato = sluttdato)

    is no.nav.amt.lib.models.arrangor.melding.Forslag.Sluttdato ->
        ForslagEndringResponse.Sluttdato(sluttdato = sluttdato)

    is no.nav.amt.lib.models.arrangor.melding.Forslag.Sluttarsak ->
        ForslagEndringResponse.Sluttarsak(aarsak = aarsak.toResponse())

    no.nav.amt.lib.models.arrangor.melding.Forslag.FjernOppstartsdato -> ForslagEndringResponse.FjernOppstartsdato
}
