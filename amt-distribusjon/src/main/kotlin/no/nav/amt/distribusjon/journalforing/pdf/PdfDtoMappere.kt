package no.nav.amt.distribusjon.journalforing.pdf

import no.nav.amt.distribusjon.hendelse.model.Hendelse
import no.nav.amt.distribusjon.journalforing.person.model.NavBruker
import no.nav.amt.internapi.hendelse.HendelseAnsvarlig
import no.nav.amt.internapi.hendelse.HendelseDeltaker
import no.nav.amt.internapi.hendelse.UtkastDto
import no.nav.amt.internapi.journalforing.pdf.ArrangorDto
import no.nav.amt.internapi.journalforing.pdf.AvsenderDto
import no.nav.amt.internapi.journalforing.pdf.EndringsvedtakPdfDto
import no.nav.amt.internapi.journalforing.pdf.HovedvedtakPdfDto
import no.nav.amt.internapi.journalforing.pdf.HovedvedtakVedTildeltPlassPdfDto
import no.nav.amt.internapi.journalforing.pdf.InnsokingsbrevPdfDto
import no.nav.amt.internapi.journalforing.pdf.VentelistebrevPdfDto
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype.Companion.tiltakMedDeltakelsesmengder
import java.time.LocalDate

fun lagHovedvedtakPdfDto(
    deltaker: HendelseDeltaker,
    navBruker: NavBruker,
    utkast: UtkastDto,
    veileder: HendelseAnsvarlig.NavVeileder,
    vedtaksdato: LocalDate,
    begrunnelseFraNav: String?,
): HovedvedtakPdfDto {
    val visningsnavn = deltaker.deltakerliste.visningsnavn()
    return HovedvedtakPdfDto(
        deltaker = HovedvedtakPdfDto.DeltakerDto(
            fornavn = navBruker.fornavn,
            mellomnavn = navBruker.mellomnavn,
            etternavn = navBruker.etternavn,
            personident = deltaker.personident,
            innhold = utkast.innhold?.map { it.toInnhold() }?.toInnholdPdfDto(deltaker.deltakerliste.tiltak.ledetekst),
            bakgrunnsinformasjon = utkast.bakgrunnsinformasjon,
            deltakelsesmengdeTekst = if (deltaker.deltakerliste.tiltak.tiltakskode in tiltakMedDeltakelsesmengder) {
                utkast.deltakelsesprosent?.let {
                    deltakelsesmengdeTekst(
                        deltakelsesprosent = it.toInt(),
                        dagerPerUke = utkast.dagerPerUke?.toInt(),
                        erEnkeltplass = deltaker.deltakerliste.erEnkeltplass,
                    )
                }
            } else {
                null
            },
            adresseDelesMedArrangor = adresseDelesMedArrangor(deltaker, navBruker),
        ),
        deltakerliste = HovedvedtakPdfDto.DeltakerlisteDto(
            navn = visningsnavn.tittel,
            tiltakskode = deltaker.deltakerliste.tiltak.tiltakskode,
            ledetekst = deltaker.deltakerliste.tiltak.ledetekst ?: "", // skal fases ut for innholdV2
            arrangor = HovedvedtakPdfDto.ArrangorDto(navn = deltaker.deltakerliste.arrangorVisningsnavn()),
            forskriftskapittel = deltaker.deltakerliste.forskriftskapittel(),
            oppmoteSted = deltaker.deltakerliste.oppmoteSted?.trimOgFjernAvsluttendePunktum(),
        ),
        avsender = HovedvedtakPdfDto.AvsenderDto(
            navn = veileder.navn,
            enhet = navBruker.navEnhet?.navn ?: "NAV",
        ),
        vedtaksdato = vedtaksdato,
        begrunnelseFraNav = begrunnelseFraNav,
        sidetittel = visningsnavn.tittel,
        ingressnavn = visningsnavn.ingressTekst,
        opprettetDato = vedtaksdato,
    )
}

fun lagHovedopptakForTildeltPlass(
    deltaker: HendelseDeltaker,
    navBruker: NavBruker,
    ansvarlig: HendelseAnsvarlig.NavTiltakskoordinator,
    opprettetDato: LocalDate,
    deltakelseInnhold: Deltakelsesinnhold?,
): HovedvedtakVedTildeltPlassPdfDto {
    val visningsnavn = deltaker.deltakerliste.visningsnavn()
    return HovedvedtakVedTildeltPlassPdfDto(
        deltaker = HovedvedtakVedTildeltPlassPdfDto.DeltakerDto(
            fornavn = navBruker.fornavn,
            mellomnavn = navBruker.mellomnavn,
            etternavn = navBruker.etternavn,
            personident = deltaker.personident,
            innhold = deltakelseInnhold?.innhold?.toInnholdPdfDto(deltaker.deltakerliste.tiltak.ledetekst),
        ),
        deltakerliste = HovedvedtakVedTildeltPlassPdfDto.DeltakerlisteDto(
            tiltakskode = deltaker.deltakerliste.tiltak.tiltakskode,
            ledetekst = deltaker.deltakerliste.tiltak.ledetekst ?: "", // skal fjernes
            tittelNavn = visningsnavn.tittel,
            ingressNavn = visningsnavn.ingressTekst,
            startdato = deltaker.deltakerliste.startdato,
            sluttdato = deltaker.deltakerliste.sluttdato,
            forskriftskapittel = deltaker.deltakerliste.forskriftskapittel(),
            harKursetStartet = deltaker.deltakerliste.startdato?.isBefore(LocalDate.now()) == true,
            arrangor = ArrangorDto(navn = deltaker.deltakerliste.arrangorVisningsnavn()),
            oppmoteSted = deltaker.deltakerliste.oppmoteSted?.trimOgFjernAvsluttendePunktum(),
            harKlagerett = deltaker.deltakerliste.harKlagerett(),
            oppstartstype = deltaker.deltakerliste.oppstartstype!!,
        ),
        avsender = HovedvedtakVedTildeltPlassPdfDto.AvsenderDto(
            navn = ansvarlig.navn,
            enhet = ansvarlig.enhet.navn,
        ),
        opprettetDato = opprettetDato,
    )
}

fun lagInnsokingsbrevPdfDto(
    deltaker: HendelseDeltaker,
    navBruker: NavBruker,
    veileder: HendelseAnsvarlig.NavVeileder,
    opprettetDato: LocalDate,
    utkast: UtkastDto,
): InnsokingsbrevPdfDto {
    val visningsnavn = deltaker.deltakerliste.visningsnavn()
    return InnsokingsbrevPdfDto(
        deltaker = InnsokingsbrevPdfDto.DeltakerDto(
            fornavn = navBruker.fornavn,
            mellomnavn = navBruker.mellomnavn,
            etternavn = navBruker.etternavn,
            personident = deltaker.personident,
            innhold = utkast.innhold?.map { it.toInnhold() }?.toInnholdPdfDto(deltaker.deltakerliste.tiltak.ledetekst),
        ),
        deltakerliste = InnsokingsbrevPdfDto.DeltakerlisteDto(
            navn = visningsnavn.tittel,
            tiltakskode = deltaker.deltakerliste.tiltak.tiltakskode,
            ledetekst = deltaker.deltakerliste.tiltak.ledetekst ?: "",
            arrangor = ArrangorDto(navn = deltaker.deltakerliste.arrangorVisningsnavn()),
            startdato = deltaker.deltakerliste.startdato,
            sluttdato = deltaker.deltakerliste.sluttdato,
            oppmoteSted = deltaker.deltakerliste.oppmoteSted?.trimOgFjernAvsluttendePunktum(),
            oppstartstype = deltaker.deltakerliste.oppstartstype!!,
        ),
        avsender = AvsenderDto(
            navn = veileder.navn,
            enhet = navBruker.navEnhet?.navn ?: "NAV",
        ),
        sidetittel = visningsnavn.tittel,
        ingressnavn = visningsnavn.ingressTekst,
        opprettetDato = opprettetDato,
    )
}

fun lagVentelistebrevPdfDto(
    deltaker: HendelseDeltaker,
    navBruker: NavBruker,
    endretAv: HendelseAnsvarlig.NavTiltakskoordinator,
    hendelseOpprettetDato: LocalDate,
): VentelistebrevPdfDto {
    val visningsnavn = deltaker.deltakerliste.visningsnavn()
    return VentelistebrevPdfDto(
        deltaker = VentelistebrevPdfDto.DeltakerDto(
            fornavn = navBruker.fornavn,
            mellomnavn = navBruker.mellomnavn,
            etternavn = navBruker.etternavn,
            personident = deltaker.personident,
            opprettetDato = deltaker.opprettetDato!!,
        ),
        deltakerliste = VentelistebrevPdfDto.DeltakerlisteDto(
            tittelNavn = visningsnavn.tittel,
            ingressNavn = visningsnavn.ingressTekst,
            arrangor = ArrangorDto(navn = deltaker.deltakerliste.arrangorVisningsnavn()),
            startdato = deltaker.deltakerliste.startdato,
            sluttdato = deltaker.deltakerliste.sluttdato,
            oppmoteSted = deltaker.deltakerliste.oppmoteSted?.trimOgFjernAvsluttendePunktum(),
            oppstartstype = deltaker.deltakerliste.oppstartstype!!,
        ),
        avsender = AvsenderDto(
            navn = endretAv.navn,
            enhet = endretAv.enhet.navn,
        ),
        opprettetDato = hendelseOpprettetDato,
    )
}

fun lagEndringsvedtakPdfDto(
    deltaker: HendelseDeltaker,
    navBruker: NavBruker,
    ansvarlig: HendelseAnsvarlig,
    hendelser: List<Hendelse>,
    opprettetDato: LocalDate,
): EndringsvedtakPdfDto {
    val endringer = fjernEldreHendelserAvSammeType(hendelser).map { it.payload }

    val visningsnavn = deltaker.deltakerliste.visningsnavn()
    return EndringsvedtakPdfDto(
        deltaker = EndringsvedtakPdfDto.DeltakerDto(
            fornavn = navBruker.fornavn,
            mellomnavn = navBruker.mellomnavn,
            etternavn = navBruker.etternavn,
            personident = deltaker.personident,
            opprettetDato = deltaker.opprettetDato,
        ),
        deltakerliste = EndringsvedtakPdfDto.DeltakerlisteDto(
            navn = visningsnavn.tittel,
            ledetekst = deltaker.deltakerliste.tiltak.ledetekst ?: "",
            arrangor = EndringsvedtakPdfDto.ArrangorDto(navn = deltaker.deltakerliste.arrangorVisningsnavn()),
            forskriftskapittel = deltaker.deltakerliste.forskriftskapittel(),
            pameldingstype = deltaker.deltakerliste.pameldingstype
                ?: throw IllegalStateException("deltakerliste ${deltaker.deltakerliste.id} må ha påmeldingstype for å lage endringsvedtak"),
            harKlagerett = deltaker.deltakerliste.harKlagerett(),
        ),
        endringer = endringer.map {
            tilEndringDto(
                hendelseType = it,
                tiltakskode = deltaker.deltakerliste.tiltak.tiltakskode,
                erEnkeltplass = deltaker.deltakerliste.erEnkeltplass,
                harFellesAvslutning = deltaker.deltakerliste.oppstartstype == Oppstartstype.FELLES ||
                    deltaker.deltakerliste.tiltak.tiltakskode
                        .erOpplaeringstiltak(),
            )
        },
        avsender = EndringsvedtakPdfDto.AvsenderDto(
            navn = ansvarlig.getAvsendernavn(),
            enhet = navBruker.navEnhet?.navn ?: "NAV",
        ),
        vedtaksdato = opprettetDato,
        forsteVedtakFattet = deltaker.forsteVedtakFattet,
        sidetittel = visningsnavn.tittel,
        ingressnavn = visningsnavn.ingressTekst,
        opprettetDato = opprettetDato,
    )
}
