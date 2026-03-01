package no.nav.amt.deltaker.bff.tiltakskoordinator.extensions

import no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse.model.UlestHendelseType
import no.nav.amt.lib.models.hendelse.HendelseType

fun HendelseType.toUlestHendelseType() = when (val hendelseType = this) {
    is HendelseType.InnbyggerGodkjennUtkast -> UlestHendelseType.InnbyggerGodkjennUtkast
    is HendelseType.NavGodkjennUtkast -> UlestHendelseType.NavGodkjennUtkast

    is HendelseType.IkkeAktuell -> UlestHendelseType.IkkeAktuell(
        hendelseType.aarsak,
        hendelseType.begrunnelseFraNav,
        hendelseType.begrunnelseFraArrangor,
        hendelseType.endringFraForslag,
    )
    is HendelseType.AvsluttDeltakelse -> UlestHendelseType.AvsluttDeltakelse(
        hendelseType.aarsak,
        hendelseType.sluttdato,
        hendelseType.begrunnelseFraNav,
        hendelseType.begrunnelseFraArrangor,
        hendelseType.endringFraForslag,
    )
    is HendelseType.AvbrytDeltakelse -> UlestHendelseType.AvbrytDeltakelse(
        hendelseType.aarsak,
        hendelseType.sluttdato,
        hendelseType.begrunnelseFraNav,
        hendelseType.begrunnelseFraArrangor,
        hendelseType.endringFraForslag,
    )
    is HendelseType.ReaktiverDeltakelse -> UlestHendelseType.ReaktiverDeltakelse(
        hendelseType.begrunnelseFraNav,
    )
    else -> null
}
