package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.AnsvarligNavnOgEnhet
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelseType
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerEndringEndringResponse
import no.nav.amt.deltaker.bff.veileder.api.response.ForslagEndringResponse
import no.nav.amt.deltaker.bff.veileder.api.response.toResponse
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class UlestHendelseResponse(
    val id: UUID,
    val opprettet: LocalDateTime,
    val deltakerId: UUID,
    val ansvarlig: AnsvarligNavnOgEnhetResponse?,
    val hendelse: UlestHendelseTypeResponse,
) {
    data class AnsvarligNavnOgEnhetResponse(
        val endretAvNavn: String,
        val endretAvEnhet: String? = null,
    )
}

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface UlestHendelseTypeResponse {
    sealed interface HendelseMedForslagResponse : UlestHendelseTypeResponse {
        val begrunnelseFraNav: String?
        val begrunnelseFraArrangor: String?
        val endringFraForslag: ForslagEndringResponse?
    }

    data object InnbyggerGodkjennUtkast : UlestHendelseTypeResponse

    data object NavGodkjennUtkast : UlestHendelseTypeResponse

    data class LeggTilOppstartsdato(
        val startdato: LocalDate,
        val sluttdato: LocalDate?,
    ) : UlestHendelseTypeResponse

    data class FjernOppstartsdato(
        override val begrunnelseFraNav: String?,
        override val begrunnelseFraArrangor: String?,
        override val endringFraForslag: ForslagEndringResponse?,
    ) : HendelseMedForslagResponse

    data class EndreStartdato(
        val startdato: LocalDate?,
        val sluttdato: LocalDate? = null,
        override val begrunnelseFraNav: String?,
        override val begrunnelseFraArrangor: String?,
        override val endringFraForslag: ForslagEndringResponse?,
    ) : HendelseMedForslagResponse

    data class IkkeAktuell(
        val aarsak: DeltakerEndringEndringResponse.AarsakResponse,
        override val begrunnelseFraNav: String?,
        override val begrunnelseFraArrangor: String?,
        override val endringFraForslag: ForslagEndringResponse?,
    ) : HendelseMedForslagResponse

    data class AvsluttDeltakelse(
        val aarsak: DeltakerEndringEndringResponse.AarsakResponse?,
        val sluttdato: LocalDate,
        override val begrunnelseFraNav: String?,
        override val begrunnelseFraArrangor: String?,
        override val endringFraForslag: ForslagEndringResponse?,
    ) : HendelseMedForslagResponse

    data class AvbrytDeltakelse(
        val aarsak: DeltakerEndringEndringResponse.AarsakResponse?,
        val sluttdato: LocalDate,
        override val begrunnelseFraNav: String?,
        override val begrunnelseFraArrangor: String?,
        override val endringFraForslag: ForslagEndringResponse?,
    ) : HendelseMedForslagResponse

    data class ReaktiverDeltakelse(
        val begrunnelseFraNav: String,
    ) : UlestHendelseTypeResponse
}

fun UlestHendelse.toResponse() = UlestHendelseResponse(
    id = id,
    opprettet = opprettet,
    deltakerId = deltakerId,
    ansvarlig = ansvarlig?.toResponse(),
    hendelse = hendelse.toResponse(),
)

private fun AnsvarligNavnOgEnhet.toResponse() = UlestHendelseResponse.AnsvarligNavnOgEnhetResponse(
    endretAvNavn = endretAvNavn,
    endretAvEnhet = endretAvEnhet,
)

private fun UlestHendelseType.toResponse(): UlestHendelseTypeResponse = when (this) {
    UlestHendelseType.InnbyggerGodkjennUtkast -> UlestHendelseTypeResponse.InnbyggerGodkjennUtkast
    UlestHendelseType.NavGodkjennUtkast -> UlestHendelseTypeResponse.NavGodkjennUtkast
    is UlestHendelseType.LeggTilOppstartsdato -> UlestHendelseTypeResponse.LeggTilOppstartsdato(startdato, sluttdato)
    is UlestHendelseType.FjernOppstartsdato -> UlestHendelseTypeResponse.FjernOppstartsdato(
        begrunnelseFraNav = begrunnelseFraNav,
        begrunnelseFraArrangor = begrunnelseFraArrangor,
        endringFraForslag = endringFraForslag?.toResponse(),
    )

    is UlestHendelseType.EndreStartdato -> UlestHendelseTypeResponse.EndreStartdato(
        startdato = startdato,
        sluttdato = sluttdato,
        begrunnelseFraNav = begrunnelseFraNav,
        begrunnelseFraArrangor = begrunnelseFraArrangor,
        endringFraForslag = endringFraForslag?.toResponse(),
    )

    is UlestHendelseType.IkkeAktuell -> UlestHendelseTypeResponse.IkkeAktuell(
        aarsak = aarsak.toResponse(),
        begrunnelseFraNav = begrunnelseFraNav,
        begrunnelseFraArrangor = begrunnelseFraArrangor,
        endringFraForslag = endringFraForslag?.toResponse(),
    )

    is UlestHendelseType.AvsluttDeltakelse -> UlestHendelseTypeResponse.AvsluttDeltakelse(
        aarsak = aarsak?.toResponse(),
        sluttdato = sluttdato,
        begrunnelseFraNav = begrunnelseFraNav,
        begrunnelseFraArrangor = begrunnelseFraArrangor,
        endringFraForslag = endringFraForslag?.toResponse(),
    )

    is UlestHendelseType.AvbrytDeltakelse -> UlestHendelseTypeResponse.AvbrytDeltakelse(
        aarsak = aarsak?.toResponse(),
        sluttdato = sluttdato,
        begrunnelseFraNav = begrunnelseFraNav,
        begrunnelseFraArrangor = begrunnelseFraArrangor,
        endringFraForslag = endringFraForslag?.toResponse(),
    )

    is UlestHendelseType.ReaktiverDeltakelse -> UlestHendelseTypeResponse.ReaktiverDeltakelse(begrunnelseFraNav)
}

private fun DeltakerEndring.Aarsak.toResponse() = DeltakerEndringEndringResponse.AarsakResponse(
    type = type,
    beskrivelse = beskrivelse,
)
