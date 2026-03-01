package no.nav.amt.distribusjon.hendelse.model

import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.lib.models.hendelse.HendelseAnsvarlig
import no.nav.amt.lib.models.hendelse.HendelseDeltaker
import no.nav.amt.lib.models.hendelse.HendelseType
import java.time.LocalDateTime
import java.util.UUID

data class Hendelse(
    val id: UUID,
    val opprettet: LocalDateTime,
    val deltaker: HendelseDeltaker,
    val ansvarlig: HendelseAnsvarlig,
    val payload: HendelseType,
    val distribusjonskanal: Distribusjonskanal,
    val manuellOppfolging: Boolean,
) {
    fun erEndringsVedtakSomSkalJournalfores(): Boolean = when (payload) {
        is HendelseType.AvsluttDeltakelse,
        is HendelseType.AvbrytDeltakelse,
        is HendelseType.EndreDeltakelsesmengde,
        is HendelseType.EndreSluttdato,
        is HendelseType.EndreStartdato,
        is HendelseType.ForlengDeltakelse,
        is HendelseType.IkkeAktuell,
        is HendelseType.EndreInnhold,
        is HendelseType.EndreBakgrunnsinformasjon,
        is HendelseType.LeggTilOppstartsdato,
        is HendelseType.FjernOppstartsdato,
        is HendelseType.Avslag,
        is HendelseType.EndreAvslutning,
        -> true

        is HendelseType.InnbyggerGodkjennUtkast,
        is HendelseType.NavGodkjennUtkast,
        is HendelseType.EndreSluttarsak,
        is HendelseType.EndreUtkast,
        is HendelseType.OpprettUtkast,
        is HendelseType.AvbrytUtkast,
        is HendelseType.DeltakerSistBesokt,
        is HendelseType.ReaktiverDeltakelse,
        is HendelseType.SettPaaVenteliste,
        is HendelseType.TildelPlass,
        -> false
    }

    fun getBegrunnelseForHovedvedtak(): String? = when (payload) {
        is HendelseType.AvsluttDeltakelse,
        is HendelseType.AvbrytDeltakelse,
        is HendelseType.EndreDeltakelsesmengde,
        is HendelseType.EndreSluttdato,
        is HendelseType.EndreStartdato,
        is HendelseType.ForlengDeltakelse,
        is HendelseType.IkkeAktuell,
        is HendelseType.EndreInnhold,
        is HendelseType.EndreBakgrunnsinformasjon,
        is HendelseType.InnbyggerGodkjennUtkast,
        is HendelseType.NavGodkjennUtkast,
        is HendelseType.EndreSluttarsak,
        is HendelseType.EndreUtkast,
        is HendelseType.OpprettUtkast,
        is HendelseType.AvbrytUtkast,
        is HendelseType.DeltakerSistBesokt,
        is HendelseType.LeggTilOppstartsdato,
        is HendelseType.FjernOppstartsdato,
        is HendelseType.SettPaaVenteliste,
        is HendelseType.TildelPlass,
        is HendelseType.Avslag,
        is HendelseType.EndreAvslutning,
        -> null
        is HendelseType.ReaktiverDeltakelse,
        -> payload.begrunnelseFraNav
    }

    fun tillattEndringUtenAktivOppfolgingsperiode() = when (payload) {
        is HendelseType.AvsluttDeltakelse,
        is HendelseType.EndreAvslutning,
        is HendelseType.AvbrytDeltakelse,
        is HendelseType.EndreSluttarsak,
        is HendelseType.EndreSluttdato,
        is HendelseType.IkkeAktuell,
        -> true

        is HendelseType.InnbyggerGodkjennUtkast,
        is HendelseType.NavGodkjennUtkast,
        is HendelseType.EndreUtkast,
        is HendelseType.OpprettUtkast,
        is HendelseType.AvbrytUtkast,
        is HendelseType.DeltakerSistBesokt,
        is HendelseType.ReaktiverDeltakelse,
        is HendelseType.EndreDeltakelsesmengde,
        is HendelseType.EndreStartdato,
        is HendelseType.ForlengDeltakelse,
        is HendelseType.EndreInnhold,
        is HendelseType.EndreBakgrunnsinformasjon,
        is HendelseType.LeggTilOppstartsdato,
        is HendelseType.FjernOppstartsdato,
        is HendelseType.SettPaaVenteliste,
        is HendelseType.TildelPlass,
        is HendelseType.Avslag,
        -> false
    }
}
