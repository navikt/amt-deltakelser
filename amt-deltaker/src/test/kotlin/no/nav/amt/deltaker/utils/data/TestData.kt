package no.nav.amt.deltaker.utils.data

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.model.Vedtaksinformasjon
import no.nav.amt.internapi.deltaker.Innsok
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengderResponse
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.NavBrukerResponse
import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto
import no.nav.amt.lib.ktor.clients.arrangor.ArrangorResponse
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltaker.Vurdering
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.dto.NavEnhetDto
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import no.nav.amt.lib.testing.utils.TestData.lagDeltakerRegistreringInnhold
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

object TestData {
    fun lagArrangorResponse(arrangor: Arrangor = lagArrangor()): ArrangorResponse {
        val overordnetArrangor = arrangor.overordnetArrangorId?.let {
            lagArrangor(id = it)
        }
        return ArrangorResponse(
            id = arrangor.id,
            navn = arrangor.navn,
            organisasjonsnummer = arrangor.organisasjonsnummer,
            overordnetArrangor = overordnetArrangor,
        )
    }

    private val tiltakstypeCache = mutableMapOf<Tiltakskode, Tiltakstype>()

    fun lagTiltakstype(
        tiltakskode: Tiltakskode = Tiltakskode.OPPFOLGING,
        id: UUID = UUID.randomUUID(),
        navn: String = "Test tiltak $tiltakskode",
        innsatsgrupper: Set<Innsatsgruppe> = setOf(Innsatsgruppe.STANDARD_INNSATS),
        innhold: DeltakerRegistreringInnhold? = lagDeltakerRegistreringInnhold(),
    ): Tiltakstype {
        val tiltak = tiltakstypeCache[tiltakskode] ?: Tiltakstype(
            id,
            navn,
            tiltakskode,
            innsatsgrupper,
            innhold,
        )
        val nyttTiltak = tiltak.copy(navn = navn, innhold = innhold, innsatsgrupper = innsatsgrupper)
        tiltakstypeCache[tiltak.tiltakskode] = nyttTiltak

        return nyttTiltak
    }

    fun lagDeltakerliste(
        id: UUID = UUID.randomUUID(),
        arrangor: Arrangor? = lagArrangor(),
        tiltakstype: Tiltakstype = lagTiltakstype(),
        navn: String = "Test Deltakerliste ${tiltakstype.tiltakskode}",
        gjennomforingstype: GjennomforingType = GjennomforingType.Gruppe,
        status: GjennomforingStatusType = GjennomforingStatusType.GJENNOMFORES,
        startDato: LocalDate? = LocalDate.now().minusMonths(1),
        sluttDato: LocalDate? = LocalDate.now().plusYears(1),
        antallPlasser: Int? = null,
        oppstart: Oppstartstype = finnOppstartstype(tiltakstype.tiltakskode),
        oppmoteSted: String? = "~oppmoteSted~",
        apentForPamelding: Boolean = true,
        pameldingType: GjennomforingPameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
        prisinformasjon: String? = null,
        opplaringKategorisering: OpplaringKategoriseringValg? = null,
    ) = Deltakerliste(
        id = id,
        tiltakstype = tiltakstype,
        gjennomforingstype = gjennomforingstype,
        navn = navn,
        status = status,
        startDato = startDato,
        sluttDato = sluttDato,
        antallPlasser = antallPlasser,
        oppstart = oppstart,
        apentForPamelding = apentForPamelding,
        oppmoteSted = oppmoteSted,
        arrangor = arrangor,
        pameldingstype = pameldingType,
        prisinformasjon = prisinformasjon,
        opplaringKategorisering = opplaringKategorisering,
    )

    fun lagDeltakerlisteMedDirekteVedtak(
        tiltakstype: Tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
    ) = lagDeltakerliste(
        tiltakstype = tiltakstype,
        pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
    )

    fun lagDeltakerlisteMedTrengerGodkjenning(
        tiltakstype: Tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING),
    ) = lagDeltakerliste(
        tiltakstype = tiltakstype,
        pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
    )

    fun lagEnkeltplassDeltakerlistePayload(
        arrangor: Arrangor = lagArrangor(),
        deltakerliste: Deltakerliste = lagDeltakerliste(arrangor = arrangor),
    ) = GjennomforingV2KafkaPayload.Enkeltplass(
        id = deltakerliste.id,
        lopenummer = null,
        status = deltakerliste.status,
        tiltakskode = deltakerliste.tiltakstype.tiltakskode,
        arrangor = GjennomforingV2KafkaPayload.Arrangor(deltakerliste.arrangor!!.organisasjonsnummer),
        oppdatertTidspunkt = OffsetDateTime.now(),
        opprettetTidspunkt = OffsetDateTime.now(),
        pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
        oppstart = Oppstartstype.ENKELTPLASS,
        prisinformasjon = deltakerliste.prisinformasjon,
    )

    fun lagDeltakerlistePayload(
        arrangor: Arrangor = lagArrangor(),
        deltakerliste: Deltakerliste = lagDeltakerliste(arrangor = arrangor),
    ) = GjennomforingV2KafkaPayload.Gruppe(
        id = deltakerliste.id,
        lopenummer = "2026-01",
        navn = deltakerliste.navn,
        tiltakskode = deltakerliste.tiltakstype.tiltakskode,
        startDato = deltakerliste.startDato!!,
        sluttDato = deltakerliste.sluttDato,
        status = deltakerliste.status,
        oppstart = deltakerliste.oppstart,
        apentForPamelding = deltakerliste.apentForPamelding,
        oppmoteSted = deltakerliste.oppmoteSted,
        tilgjengeligForArrangorFraOgMedDato = null,
        antallPlasser = deltakerliste.antallPlasser ?: 0,
        deltidsprosent = 42.0,
        arrangor = GjennomforingV2KafkaPayload.Arrangor(deltakerliste.arrangor!!.organisasjonsnummer),
        oppdatertTidspunkt = OffsetDateTime.now(),
        opprettetTidspunkt = OffsetDateTime.now(),
        pameldingType = deltakerliste.pameldingstype,
    )

    fun lagNavEnhetDto(navEnhet: NavEnhet) = NavEnhetDto(
        id = navEnhet.id,
        enhetId = navEnhet.enhetsnummer,
        navn = navEnhet.navn,
    )

    fun lagDeltaker(
        id: UUID = UUID.randomUUID(),
        navBruker: NavBruker = lagNavBruker(),
        deltakerliste: Deltakerliste = lagDeltakerliste(),
        startdato: LocalDate? = LocalDate.now().minusMonths(3),
        sluttdato: LocalDate? = LocalDate.now().minusDays(1),
        dagerPerUke: Float? = 5F,
        deltakelsesprosent: Float? = 100F,
        bakgrunnsinformasjon: String? = "Søkes inn fordi...",
        innhold: Deltakelsesinnhold? = Deltakelsesinnhold("ledetekst", emptyList()),
        status: DeltakerStatus = lagDeltakerStatus(statusType = DeltakerStatus.Type.HAR_SLUTTET),
        vedtaksinformasjon: Vedtaksinformasjon? = null,
        sistEndret: LocalDateTime = LocalDateTime.now(),
        kilde: Kilde = Kilde.KOMET,
        erManueltDeltMedArrangor: Boolean = false,
    ) = Deltaker(
        id = id,
        navBruker = navBruker,
        deltakerliste = deltakerliste,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        deltakelsesinnhold = innhold,
        status = status,
        vedtaksinformasjon = vedtaksinformasjon,
        sistEndret = sistEndret,
        kilde = kilde,
        erManueltDeltMedArrangor = erManueltDeltMedArrangor,
        opprettet = LocalDateTime.now(),
    )

    fun lagDeltakerStatus(
        statusType: DeltakerStatus.Type = DeltakerStatus.Type.DELTAR,
        id: UUID = UUID.randomUUID(),
        aarsakType: DeltakerStatus.Aarsak.Type? = null,
        beskrivelse: String? = null,
        gyldigFra: LocalDateTime = LocalDate.now().atStartOfDay(),
        gyldigTil: LocalDateTime? = null,
        opprettet: LocalDateTime = LocalDateTime.now(),
    ) = DeltakerStatus(
        id,
        statusType,
        aarsakType?.let { DeltakerStatus.Aarsak(it, beskrivelse) },
        gyldigFra,
        gyldigTil,
        opprettet,
    )

    fun lagDeltakerEndring(
        id: UUID = UUID.randomUUID(),
        deltakerId: UUID = UUID.randomUUID(),
        endring: DeltakerEndring.Endring = DeltakerEndring.Endring.EndreBakgrunnsinformasjon("Oppdatert bakgrunnsinformasjon"),
        endretAv: UUID = UUID.randomUUID(),
        endretAvEnhet: UUID = UUID.randomUUID(),
        endret: LocalDateTime = LocalDateTime.now(),
        forslag: Forslag? = null,
    ) = DeltakerEndring(id, deltakerId, endring, endretAv, endretAvEnhet, endret, forslag)

    fun lagForslag(
        id: UUID = UUID.randomUUID(),
        deltakerId: UUID = UUID.randomUUID(),
        opprettetAvArrangorAnsattId: UUID = UUID.randomUUID(),
        opprettet: LocalDateTime = LocalDateTime.now(),
        begrunnelse: String = "Begrunnelse fra arrangør",
        endring: Forslag.Endring = Forslag.ForlengDeltakelse(LocalDate.now().plusWeeks(2)),
        status: Forslag.Status = Forslag.Status.VenterPaSvar,
    ) = Forslag(id, deltakerId, opprettetAvArrangorAnsattId, opprettet, begrunnelse, endring, status)

    fun lagEndringFraArrangor(
        id: UUID = UUID.randomUUID(),
        deltakerId: UUID = UUID.randomUUID(),
        opprettetAvArrangorAnsattId: UUID = UUID.randomUUID(),
        opprettet: LocalDateTime = LocalDateTime.now(),
        endring: EndringFraArrangor.Endring = EndringFraArrangor.LeggTilOppstartsdato(
            LocalDate.now().plusDays(2),
            LocalDate.now().plusMonths(3),
        ),
    ) = EndringFraArrangor(id, deltakerId, opprettetAvArrangorAnsattId, opprettet, endring)

    fun lagVedtak(
        id: UUID = UUID.randomUUID(),
        deltakerVedVedtak: Deltaker = lagDeltaker(
            status = lagDeltakerStatus(statusType = DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        ),
        deltakerId: UUID = deltakerVedVedtak.id,
        fattet: LocalDateTime? = null,
        gyldigTil: LocalDateTime? = null,
        fattetAvNav: Boolean = false,
        opprettet: LocalDateTime = fattet ?: LocalDateTime.now(),
        opprettetAv: NavAnsatt = lagNavAnsatt(),
        opprettetAvEnhet: NavEnhet = lagNavEnhet(),
        sistEndret: LocalDateTime = opprettet,
        sistEndretAv: NavAnsatt = opprettetAv,
        sistEndretAvEnhet: NavEnhet = opprettetAvEnhet,
    ) = Vedtak(
        id,
        deltakerId,
        fattet,
        gyldigTil,
        deltakerVedVedtak.toDeltakerVedVedtak(),
        fattetAvNav,
        opprettet,
        opprettetAv.id,
        opprettetAvEnhet.id,
        sistEndret,
        sistEndretAv.id,
        sistEndretAvEnhet.id,
    )

    fun lagInnsok(
        id: UUID = UUID.randomUUID(),
        deltakerId: UUID = UUID.randomUUID(),
        innsokt: LocalDateTime = LocalDateTime.now(),
        innsoktAv: UUID = UUID.randomUUID(),
        innsoktAvEnhet: UUID = UUID.randomUUID(),
        startdato: LocalDate? = null,
        sluttdato: LocalDate? = null,
        deltakelsesinnholdVedInnsok: Deltakelsesinnhold = Deltakelsesinnhold("", emptyList()),
        opplaringKategoriseringValg: OpplaringKategoriseringValg? = null,
        utkastDelt: LocalDateTime = LocalDateTime.now().minusDays(2),
        utkastGodkjentAvNav: Boolean = false,
        prisinformasjonVedInnsok: PrisinformasjonDto? = null,
    ) = Innsok(
        id = id,
        deltakerId = deltakerId,
        innsokt = innsokt,
        innsoktAv = innsoktAv,
        innsoktAvEnhet = innsoktAvEnhet,
        startdato = startdato,
        sluttdato = sluttdato,
        deltakelsesinnholdVedInnsok = deltakelsesinnholdVedInnsok,
        utkastDelt = utkastDelt,
        utkastGodkjentAvNav = utkastGodkjentAvNav,
        opplaringKategoriseringVedInnsok = opplaringKategoriseringValg,
        prisinformasjonVedInnsok = prisinformasjonVedInnsok,
    )

    fun lagVurdering(
        id: UUID = UUID.randomUUID(),
        deltakerId: UUID,
        vurderingstype: Vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
        begrunnelse: String? = null,
        opprettetAvArrangorAnsattId: UUID = UUID.randomUUID(),
        gyldigFra: LocalDateTime = LocalDateTime.now(),
    ) = Vurdering(
        id = id,
        deltakerId = deltakerId,
        vurderingstype = vurderingstype,
        begrunnelse = begrunnelse,
        opprettetAvArrangorAnsattId = opprettetAvArrangorAnsattId,
        gyldigFra = gyldigFra,
    )

    private fun finnOppstartstype(tiltakskode: Tiltakskode) = when (tiltakskode) {
        Tiltakskode.JOBBKLUBB,
        Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
        Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
        -> Oppstartstype.FELLES
        Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
        Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
        Tiltakskode.HOYERE_UTDANNING,
        -> Oppstartstype.ENKELTPLASS

        else -> Oppstartstype.LOPENDE
    }

    fun lagDeltakerResponse(deltaker: Deltaker): DeltakerResponse = DeltakerResponse(
        id = deltaker.id,
        status = deltaker.status,
        navBruker = lagNavBrukerResponse(deltaker.navBruker),
        gjennomforing = lagGjennomforingResponse(deltaker.deltakerliste),
        startdato = deltaker.startdato,
        sluttdato = deltaker.sluttdato,
        dagerPerUke = deltaker.dagerPerUke,
        deltakelsesprosent = deltaker.deltakelsesprosent,
        bakgrunnsinformasjon = deltaker.bakgrunnsinformasjon,
        deltakelsesinnhold = deltaker.deltakelsesinnhold,
        vedtaksinformasjon = null,
        erManueltDeltMedArrangor = deltaker.erManueltDeltMedArrangor,
        kilde = deltaker.kilde,
        sistEndret = deltaker.sistEndret,
        opprettet = deltaker.opprettet,
        soktInnDato = null,
        deltakelsesmengder = DeltakelsesmengderResponse(
            nesteDeltakelsesmengde = null,
            sisteDeltakelsesmengde = null,
        ),
        erLaastForEndringer = false,
        endringsforslagFraArrangor = emptyList(),
        prisinformasjon = deltaker.deltakerliste.prisinformasjon,
        sisteVurdering = null,
        importertFraArena = null,
    )

    fun lagNavBrukerResponse(navBruker: NavBruker): NavBrukerResponse = NavBrukerResponse(
        personident = navBruker.personident,
        fornavn = navBruker.fornavn,
        mellomnavn = navBruker.mellomnavn,
        etternavn = navBruker.etternavn,
        telefon = navBruker.telefon,
        epost = navBruker.epost,
        erSkjermet = navBruker.erSkjermet,
        adresse = navBruker.adresse,
        adressebeskyttelse = navBruker.adressebeskyttelse,
        oppfolgingsperioder = navBruker.oppfolgingsperioder,
        innsatsgruppe = navBruker.innsatsgruppe,
        navVeileder = null,
        navEnhet = null,
        erDigital = true,
    )

    fun lagGjennomforingResponse(deltakerliste: Deltakerliste): GjennomforingResponse = GjennomforingResponse(
        id = deltakerliste.id,
        type = deltakerliste.gjennomforingstype,
        tiltakstype = deltakerliste.tiltakstype,
        navn = deltakerliste.navn,
        status = deltakerliste.status,
        startDato = deltakerliste.startDato,
        sluttDato = deltakerliste.sluttDato,
        antallPlasser = deltakerliste.antallPlasser,
        oppstart = deltakerliste.oppstart,
        apentForPamelding = deltakerliste.apentForPamelding,
        oppmoteSted = deltakerliste.oppmoteSted,
        arrangor = deltakerliste.arrangor?.let {
            no.nav.amt.internapi.deltaker.response.ArrangorResponse(
                navn = it.navn,
                organisasjonsnummer = it.organisasjonsnummer,
            )
        },
        pameldingstype = deltakerliste.pameldingstype,
    )

    fun lagOpplaringKategorisering(): OpplaringKategoriseringValg = OpplaringKategoriseringValg(
        valgteKategoriseringer = setOf(
            OpplaringKategoriseringValg.ValgteFelt(
                representerer = OpplaringKategoriseringType.BRANSJE_ID,
                valg = mapOf(UUID.randomUUID() to "Tralala"),
            ),
        ),
        valgteSertifiseringer = setOf(
            SertifiseringValg(id = 1, navn = "Truckfører T1"),
            SertifiseringValg(id = 2, navn = "Truckfører T2"),
        ),
    )
}
