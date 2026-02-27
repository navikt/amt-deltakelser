package no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse.model

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import java.time.LocalDate

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface UlestHendelseType {
    sealed interface HendelseMedForslag : UlestHendelseType {
        val begrunnelseFraNav: String?
        val begrunnelseFraArrangor: String?
        val endringFraForslag: Forslag.Endring?
    }

    data object InnbyggerGodkjennUtkast : UlestHendelseType

    data object NavGodkjennUtkast : UlestHendelseType

    @Suppress("unused")
    data class LeggTilOppstartsdato(
        val startdato: LocalDate,
        val sluttdato: LocalDate?,
    ) : UlestHendelseType

    @Suppress("unused")
    data class FjernOppstartsdato(
        override val begrunnelseFraNav: String?,
        override val begrunnelseFraArrangor: String?,
        override val endringFraForslag: Forslag.Endring?,
    ) : HendelseMedForslag

    @Suppress("unused")
    data class EndreStartdato(
        val startdato: LocalDate?,
        val sluttdato: LocalDate? = null,
        override val begrunnelseFraNav: String?,
        override val begrunnelseFraArrangor: String?,
        override val endringFraForslag: Forslag.Endring?,
    ) : HendelseMedForslag

    data class IkkeAktuell(
        val aarsak: DeltakerEndring.Aarsak,
        override val begrunnelseFraNav: String?,
        override val begrunnelseFraArrangor: String?,
        override val endringFraForslag: Forslag.Endring?,
    ) : HendelseMedForslag

    data class AvsluttDeltakelse(
        val aarsak: DeltakerEndring.Aarsak?,
        val sluttdato: LocalDate,
        override val begrunnelseFraNav: String?,
        override val begrunnelseFraArrangor: String?,
        override val endringFraForslag: Forslag.Endring?,
    ) : HendelseMedForslag

    data class AvbrytDeltakelse(
        val aarsak: DeltakerEndring.Aarsak?,
        val sluttdato: LocalDate,
        override val begrunnelseFraNav: String?,
        override val begrunnelseFraArrangor: String?,
        override val endringFraForslag: Forslag.Endring?,
    ) : HendelseMedForslag

    data class ReaktiverDeltakelse(
        val begrunnelseFraNav: String,
    ) : UlestHendelseType
}
