package no.nav.amt.deltaker.innbygger

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.internapi.hendelse.HendelseDeltaker
import no.nav.amt.internapi.hendelse.InnholdDto
import no.nav.amt.internapi.hendelse.UtkastDto
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.utils.toTitleCase
import java.time.LocalDate

fun Deltaker.toUtkastDto() = UtkastDto(
    startdato = startdato,
    sluttdato = sluttdato,
    dagerPerUke = dagerPerUke,
    deltakelsesprosent = deltakelsesprosent,
    bakgrunnsinformasjon = bakgrunnsinformasjon,
    innhold = deltakelsesinnhold?.innhold?.toInnholdDtoList(),
)

fun Deltaker.toHendelseDeltaker(
    forsteVedtakFattet: LocalDate?,
    opplaringKategoriseringValg: OpplaringKategoriseringValg?,
    prisinformasjon: PrisinformasjonDto?,
) = HendelseDeltaker(
    id = id,
    personident = navBruker.personident,
    forsteVedtakFattet = forsteVedtakFattet,
    opprettetDato = opprettet.toLocalDate(),
    startdato = startdato,
    sluttdato = sluttdato,
    status = status,
    deltakerliste = HendelseDeltaker.Deltakerliste(
        id = gjennomforing.id,
        navn = gjennomforing.navn,
        arrangor =
            gjennomforing.arrangor?.toHendelseArrangor()
                ?: throw IllegalStateException("kan ikke publisere deltaker $id som mangler arrangor til hendelse topic"),
        startdato = gjennomforing.startDato,
        sluttdato = gjennomforing.sluttDato,
        oppstartstype = gjennomforing.oppstart,
        tiltak = HendelseDeltaker.Deltakerliste.Tiltak(
            navn = gjennomforing.tiltakstype.visningsnavn,
            ledetekst = gjennomforing.tiltakstype.innhold?.ledetekst,
            tiltakskode = gjennomforing.tiltakstype.tiltakskode,
        ),
        oppmoteSted = gjennomforing.oppmoteSted,
        pameldingstype = gjennomforing.pameldingstype.let { GjennomforingPameldingType.valueOf(it.name) },
        erEnkeltplass = gjennomforing.gjennomforingstype == GjennomforingType.Enkeltplass,
        opplaringKategoriseringValg = opplaringKategoriseringValg,
        prisinformasjon = prisinformasjon,
    ),
)

private fun List<Innhold>.toInnholdDtoList() = this.map {
    InnholdDto(
        tekst = it.tekst,
        innholdskode = it.innholdskode,
        beskrivelse = it.beskrivelse,
    )
}

private fun Arrangor.toHendelseArrangor() = HendelseDeltaker.Deltakerliste.Arrangor(
    id,
    organisasjonsnummer,
    navn.toTitleCase(),
)
