package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.AnsvarligNavnOgEnhet
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelseType
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerEndringEndringResponse
import no.nav.amt.deltaker.bff.veileder.api.response.ForslagEndringResponse
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
    constructor(model: UlestHendelse) : this(
        id = model.id,
        opprettet = model.opprettet,
        deltakerId = model.deltakerId,
        ansvarlig = model.ansvarlig?.let(::AnsvarligNavnOgEnhetResponse),
        hendelse = UlestHendelseTypeResponse.fromModel(model.hendelse),
    )

    data class AnsvarligNavnOgEnhetResponse(
        val endretAvNavn: String,
        val endretAvEnhet: String? = null,
    ) {
        constructor(model: AnsvarligNavnOgEnhet) : this(
            endretAvNavn = model.endretAvNavn,
            endretAvEnhet = model.endretAvEnhet,
        )
    }
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
    ) : UlestHendelseTypeResponse {
        constructor(model: UlestHendelseType.LeggTilOppstartsdato) : this(
            startdato = model.startdato,
            sluttdato = model.sluttdato,
        )
    }

    data class FjernOppstartsdato(
        override val begrunnelseFraNav: String?,
        override val begrunnelseFraArrangor: String?,
        override val endringFraForslag: ForslagEndringResponse?,
    ) : HendelseMedForslagResponse {
        constructor(model: UlestHendelseType.FjernOppstartsdato) : this(
            begrunnelseFraNav = model.begrunnelseFraNav,
            begrunnelseFraArrangor = model.begrunnelseFraArrangor,
            endringFraForslag = model.endringFraForslag?.let(ForslagEndringResponse::fromModel),
        )
    }

    data class EndreStartdato(
        val startdato: LocalDate?,
        val sluttdato: LocalDate? = null,
        override val begrunnelseFraNav: String?,
        override val begrunnelseFraArrangor: String?,
        override val endringFraForslag: ForslagEndringResponse?,
    ) : HendelseMedForslagResponse {
        constructor(model: UlestHendelseType.EndreStartdato) : this(
            startdato = model.startdato,
            sluttdato = model.sluttdato,
            begrunnelseFraNav = model.begrunnelseFraNav,
            begrunnelseFraArrangor = model.begrunnelseFraArrangor,
            endringFraForslag = model.endringFraForslag?.let(ForslagEndringResponse::fromModel),
        )
    }

    data class IkkeAktuell(
        val aarsak: DeltakerEndringEndringResponse.AarsakResponse,
        override val begrunnelseFraNav: String?,
        override val begrunnelseFraArrangor: String?,
        override val endringFraForslag: ForslagEndringResponse?,
    ) : HendelseMedForslagResponse {
        constructor(model: UlestHendelseType.IkkeAktuell) : this(
            aarsak = DeltakerEndringEndringResponse.AarsakResponse(model.aarsak),
            begrunnelseFraNav = model.begrunnelseFraNav,
            begrunnelseFraArrangor = model.begrunnelseFraArrangor,
            endringFraForslag = model.endringFraForslag?.let(ForslagEndringResponse::fromModel),
        )
    }

    data class AvsluttDeltakelse(
        val aarsak: DeltakerEndringEndringResponse.AarsakResponse?,
        val sluttdato: LocalDate,
        override val begrunnelseFraNav: String?,
        override val begrunnelseFraArrangor: String?,
        override val endringFraForslag: ForslagEndringResponse?,
    ) : HendelseMedForslagResponse {
        constructor(model: UlestHendelseType.AvsluttDeltakelse) : this(
            aarsak = model.aarsak?.let { DeltakerEndringEndringResponse.AarsakResponse(it) },
            sluttdato = model.sluttdato,
            begrunnelseFraNav = model.begrunnelseFraNav,
            begrunnelseFraArrangor = model.begrunnelseFraArrangor,
            endringFraForslag = model.endringFraForslag?.let(ForslagEndringResponse::fromModel),
        )
    }

    data class AvbrytDeltakelse(
        val aarsak: DeltakerEndringEndringResponse.AarsakResponse?,
        val sluttdato: LocalDate,
        override val begrunnelseFraNav: String?,
        override val begrunnelseFraArrangor: String?,
        override val endringFraForslag: ForslagEndringResponse?,
    ) : HendelseMedForslagResponse {
        constructor(model: UlestHendelseType.AvbrytDeltakelse) : this(
            aarsak = model.aarsak?.let { DeltakerEndringEndringResponse.AarsakResponse(it) },
            sluttdato = model.sluttdato,
            begrunnelseFraNav = model.begrunnelseFraNav,
            begrunnelseFraArrangor = model.begrunnelseFraArrangor,
            endringFraForslag = model.endringFraForslag?.let(ForslagEndringResponse::fromModel),
        )
    }

    data class ReaktiverDeltakelse(
        val begrunnelseFraNav: String,
    ) : UlestHendelseTypeResponse {
        constructor(model: UlestHendelseType.ReaktiverDeltakelse) : this(
            begrunnelseFraNav = model.begrunnelseFraNav,
        )
    }

    companion object {
        fun fromModel(model: UlestHendelseType): UlestHendelseTypeResponse = when (model) {
            UlestHendelseType.InnbyggerGodkjennUtkast -> InnbyggerGodkjennUtkast
            UlestHendelseType.NavGodkjennUtkast -> NavGodkjennUtkast
            is UlestHendelseType.LeggTilOppstartsdato -> LeggTilOppstartsdato(model)
            is UlestHendelseType.FjernOppstartsdato -> FjernOppstartsdato(model)
            is UlestHendelseType.EndreStartdato -> EndreStartdato(model)
            is UlestHendelseType.IkkeAktuell -> IkkeAktuell(model)
            is UlestHendelseType.AvsluttDeltakelse -> AvsluttDeltakelse(model)
            is UlestHendelseType.AvbrytDeltakelse -> AvbrytDeltakelse(model)
            is UlestHendelseType.ReaktiverDeltakelse -> ReaktiverDeltakelse(model)
        }
    }
}
