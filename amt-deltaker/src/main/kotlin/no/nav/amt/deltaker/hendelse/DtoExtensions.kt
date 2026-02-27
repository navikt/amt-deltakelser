package no.nav.amt.deltaker.hendelse

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.hendelse.HendelseDeltaker
import no.nav.amt.lib.models.hendelse.InnholdDto
import no.nav.amt.lib.models.hendelse.UtkastDto
import java.time.LocalDate

fun Deltaker.toUtkastDto() = UtkastDto(
    startdato,
    sluttdato,
    dagerPerUke,
    deltakelsesprosent,
    bakgrunnsinformasjon,
    deltakelsesinnhold?.innhold?.toInnholdDtoList(),
)

fun Deltaker.toHendelseDeltaker(overordnetArrangor: Arrangor?, forsteVedtakFattet: LocalDate?) = HendelseDeltaker(
    id = id,
    personident = navBruker.personident,
    deltakerliste = HendelseDeltaker.Deltakerliste(
        id = deltakerliste.id,
        navn = deltakerliste.navn,
        arrangor = deltakerliste.arrangor.toHendelseArrangor(overordnetArrangor?.toHendelseArrangor()),
        startdato = deltakerliste.startDato,
        sluttdato = deltakerliste.sluttDato,
        oppstartstype = deltakerliste.oppstart
            ?.let { HendelseDeltaker.Deltakerliste.Oppstartstype.valueOf(it.name) }
            ?: HendelseDeltaker.Deltakerliste.Oppstartstype.LOPENDE, // opplæringstiltak uten rammeavtale mangler oppstartstype
        tiltak = HendelseDeltaker.Deltakerliste.Tiltak(
            navn = deltakerliste.tiltakstype.visningsnavn,
            ledetekst = deltakerliste.tiltakstype.innhold?.ledetekst,
            tiltakskode = deltakerliste.tiltakstype.tiltakskode,
        ),
        oppmoteSted = deltakerliste.oppmoteSted,
        pameldingstype = deltakerliste.pameldingstype
            ?.let { GjennomforingPameldingType.valueOf(it.name) }
            ?: throw IllegalStateException("Pameldingstype kan ikke være null i hendelse"),
    ),
    forsteVedtakFattet = forsteVedtakFattet,
    opprettetDato = opprettet.toLocalDate(),
)

private fun List<Innhold>.toInnholdDtoList() = this.map {
    InnholdDto(
        tekst = it.tekst,
        innholdskode = it.innholdskode,
        beskrivelse = it.beskrivelse,
    )
}

private fun Arrangor.toHendelseArrangor(overordnetArrangor: HendelseDeltaker.Deltakerliste.Arrangor? = null) =
    HendelseDeltaker.Deltakerliste.Arrangor(
        id,
        organisasjonsnummer,
        navn,
        overordnetArrangor,
    )
