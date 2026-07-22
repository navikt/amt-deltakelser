package no.nav.amt.deltaker.bff.utils

import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.commonresponse.DeltakelsesinnholdResponse.Companion.fulltInnhold
import no.nav.amt.deltaker.bff.model.ArrangorModel
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.model.Deltakerliste
import no.nav.amt.deltaker.bff.model.GjennomforingModel
import no.nav.amt.deltaker.bff.model.NavBrukerModel
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorDeltakerlisteTilgang
import no.nav.amt.internapi.deltaker.getInnholdselementer
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengdeResponse
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengderResponse
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.NavBrukerResponse
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
import no.nav.amt.internapi.deltaker.response.VedtaksinformasjonResponse
import no.nav.amt.internapi.deltaker.toInnhold
import no.nav.amt.internapi.tiltakskoordinator.response.TiltakskoordinatorDeltakerIListeResponse
import no.nav.amt.internapi.tiltakskoordinator.response.TiltakskoordinatorNavBrukerResponse
import no.nav.amt.lib.ktor.clients.arrangor.ArrangorResponse
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerVedVedtak
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.Oppfolgingsperiode
import no.nav.amt.lib.models.person.address.Adresse
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.testing.utils.TestData.lagAdresse
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import no.nav.amt.lib.testing.utils.TestData.lagDeltakerRegistreringInnhold
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.testing.utils.TestData.lagOppfolgingsperiode
import no.nav.amt.lib.testing.utils.TestData.randomIdent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

object TestData {
    fun input(n: Int) = (1..n).map { ('a'..'z').random() }.joinToString("")

    fun lagArrangorClientResponse(arrangorInTest: Arrangor = lagArrangor()): ArrangorResponse {
        val overordnetArrangorInTest = arrangorInTest.overordnetArrangorId?.let { lagArrangor(id = it) }

        return ArrangorResponse(
            id = arrangorInTest.id,
            navn = arrangorInTest.navn,
            organisasjonsnummer = arrangorInTest.organisasjonsnummer,
            overordnetArrangor = overordnetArrangorInTest,
        )
    }

    fun DeltakerModel.toDeltakerVedVedtak() = DeltakerVedVedtak(
        id,
        startdato,
        sluttdato,
        dagerPerUke,
        deltakelsesprosent,
        bakgrunnsinformasjon,
        deltakelsesinnhold = deltakelsesinnhold?.let {
            Deltakelsesinnhold(
                ledetekst = it.ledetekst,
                innhold = fulltInnhold(
                    it.innhold,
                    getInnholdselementer(gjennomforing.tiltak.innhold?.innholdselementer, gjennomforing.tiltak.tiltakskode),
                ),
            )
        },
        null,
        status,
    )

    fun lagDeltakerliste(
        id: UUID = UUID.randomUUID(),
        overordnetArrangor: Arrangor? = null,
        arrangor: Arrangor = lagArrangor(overordnetArrangorId = overordnetArrangor?.id),
        tiltakstype: Tiltakstype = lagTiltakstype(),
        navn: String = "Test Deltakerliste ${tiltakstype.tiltakskode}",
        status: GjennomforingStatusType = GjennomforingStatusType.GJENNOMFORES,
        startDato: LocalDate = LocalDate.now().minusMonths(1),
        sluttDato: LocalDate? = LocalDate.now().plusYears(1),
        oppstart: Oppstartstype = finnOppstartstype(tiltakstype.tiltakskode),
        apentForPamelding: Boolean = true,
        antallPlasser: Int = 42,
        oppmoteSted: String = "~oppmoteSted~",
        pameldingType: GjennomforingPameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
    ) = Deltakerliste(
        id = id,
        tiltak = tiltakstype,
        navn = navn,
        status = status,
        startDato = startDato,
        sluttDato = sluttDato,
        oppstart = oppstart,
        arrangor = Deltakerliste.Arrangor(arrangor, overordnetArrangor?.navn),
        apentForPamelding = apentForPamelding,
        antallPlasser = antallPlasser,
        oppmoteSted = oppmoteSted,
        pameldingstype = pameldingType,
    )

    fun lagGjennomforingResponse(
        id: UUID = UUID.randomUUID(),
        tiltakstype: Tiltakstype = lagTiltakstype(),
        navn: String = "Test Deltakerliste ${tiltakstype.tiltakskode}",
        status: GjennomforingStatusType = GjennomforingStatusType.GJENNOMFORES,
        startDato: LocalDate = LocalDate.now().minusMonths(1),
        sluttDato: LocalDate? = LocalDate.now().plusYears(1),
        oppstart: Oppstartstype = finnOppstartstype(tiltakstype.tiltakskode),
        apentForPamelding: Boolean = true,
        oppmoteSted: String = "~oppmoteSted~",
        pameldingType: GjennomforingPameldingType? = GjennomforingPameldingType.TRENGER_GODKJENNING,
    ) = GjennomforingResponse(
        id = id,
        tiltakstype = tiltakstype,
        navn = navn,
        status = status,
        startDato = startDato,
        sluttDato = sluttDato,
        oppstart = oppstart,
        arrangor = lagArrangorResponse(),
        apentForPamelding = apentForPamelding,
        oppmoteSted = oppmoteSted,
        pameldingstype = pameldingType,
        type = GjennomforingType.Gruppe,
        antallPlasser = null,
    )

    private val tiltakstypeCache = mutableMapOf<Tiltakskode, Tiltakstype>()

    fun lagDeltakelsesinnhold(): Deltakelsesinnhold = Deltakelsesinnhold(
        ledetekst = "Beskrivelse av tiltaket",
        innhold = listOf(
            Innhold(
                tekst = "Tekst",
                innholdskode = "kode",
                valgt = true,
                beskrivelse = null,
            ),
        ),
    )

    fun lagArrangorResponse(
        navn: String = "Arrangor 1",
        organisasjonsnummer: String = no.nav.amt.lib.testing.utils.TestData
            .randomOrgnr(),
    ) = no.nav.amt.internapi.deltaker.response.ArrangorResponse(
        navn = navn,
        organisasjonsnummer = organisasjonsnummer,
    )

    fun lagTiltakstype(
        id: UUID = UUID.randomUUID(),
        tiltakskode: Tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
        navn: String = "Test tiltak $tiltakskode",
        innsatsgrupper: Set<Innsatsgruppe> = setOf(Innsatsgruppe.STANDARD_INNSATS),
        innhold: DeltakerRegistreringInnhold? = lagDeltakerRegistreringInnhold(),
    ): Tiltakstype {
        val tiltak = tiltakstypeCache[tiltakskode] ?: Tiltakstype(
            id = id,
            navn = navn,
            tiltakskode = tiltakskode,
            innsatsgrupper = innsatsgrupper,
            innhold = innhold,
        )
        val nyttTiltak = tiltak.copy(navn = navn, innhold = innhold)
        tiltakstypeCache[tiltak.tiltakskode] = nyttTiltak

        return nyttTiltak
    }

    fun lagEnkeltplassDeltakerlistePayload(
        arrangor: Arrangor = lagArrangor(),
        deltakerliste: Deltakerliste = lagDeltakerliste(arrangor = arrangor),
        pameldingType: GjennomforingPameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
    ) = GjennomforingV2KafkaPayload.Enkeltplass(
        id = deltakerliste.id,
        lopenummer = null,
        tiltakskode = deltakerliste.tiltak.tiltakskode,
        status = deltakerliste.status,
        arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangor.organisasjonsnummer),
        oppdatertTidspunkt = OffsetDateTime.now(),
        opprettetTidspunkt = OffsetDateTime.now(),
        pameldingType = pameldingType,
        oppstart = Oppstartstype.ENKELTPLASS,
    )

    fun lagGruppeDeltakerlistePayload(
        arrangor: Arrangor = lagArrangor(),
        deltakerliste: Deltakerliste = lagDeltakerliste(arrangor = arrangor),
        pameldingType: GjennomforingPameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
    ) = GjennomforingV2KafkaPayload.Gruppe(
        id = deltakerliste.id,
        lopenummer = "2026-01",
        navn = deltakerliste.navn,
        tiltakskode = deltakerliste.tiltak.tiltakskode,
        startDato = deltakerliste.startDato!!,
        sluttDato = deltakerliste.sluttDato,
        status = deltakerliste.status,
        oppstart = deltakerliste.oppstart,
        apentForPamelding = deltakerliste.apentForPamelding,
        oppmoteSted = deltakerliste.oppmoteSted,
        tilgjengeligForArrangorFraOgMedDato = null,
        antallPlasser = 42,
        deltidsprosent = 42.0,
        arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangor.organisasjonsnummer),
        pameldingType = pameldingType,
        oppdatertTidspunkt = OffsetDateTime.now(),
        opprettetTidspunkt = OffsetDateTime.now(),
    )

    fun lagDeltakerKladd(
        id: UUID = UUID.randomUUID(),
        navBruker: NavBruker = lagNavBruker(),
        deltakerliste: Deltakerliste = lagDeltakerliste(),
        sistEndret: LocalDateTime = LocalDateTime.now(),
    ) = lagDeltakerOld(
        id = id,
        navBruker = navBruker,
        deltakerliste = deltakerliste,
        startdato = null,
        sluttdato = null,
        dagerPerUke = null,
        deltakelsesprosent = null,
        bakgrunnsinformasjon = null,
        innhold = emptyList(),
        status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
        sistEndret = sistEndret,
    )

    fun lagDeltakerOld(
        id: UUID = UUID.randomUUID(),
        navBruker: NavBruker = lagNavBruker(),
        deltakerliste: Deltakerliste = lagDeltakerliste(),
        startdato: LocalDate? = LocalDate.now().minusMonths(3),
        sluttdato: LocalDate? = LocalDate.now().minusDays(1),
        dagerPerUke: Float? = 5F,
        deltakelsesprosent: Float? = 100F,
        bakgrunnsinformasjon: String? = "Søkes inn fordi...",
        innhold: List<Innhold> = deltakerliste.tiltak.innhold
            ?.innholdselementer
            ?.map { it.toInnhold() } ?: emptyList(),
        status: DeltakerStatus = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
        kanEndres: Boolean = true,
        erManueltDeltMedArrangor: Boolean = false,
        createdAt: LocalDateTime = LocalDateTime.now(),
        sistEndret: LocalDateTime = LocalDateTime.now(),
    ): Deltaker = Deltaker(
        id = id,
        navBruker = navBruker,
        deltakerliste = deltakerliste,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        deltakelsesinnhold = Deltakelsesinnhold("ledetekst", innhold),
        status = status,
        erManueltDeltMedArrangor = erManueltDeltMedArrangor,
        historikk = emptyList(),
        kanEndres = kanEndres,
        opprettet = createdAt,
        sistEndret = sistEndret,
    )

    fun lagNavBrukerModel(
        personident: String = randomIdent(),
        fornavn: String = "Fornavn",
        mellomnavn: String? = "Mellomnavn",
        etternavn: String = "Etternavn",
        navVeileder: NavVeilederResponse? = NavVeilederResponse("Nav Veiledersen", null, null),
        navEnhet: String? = "Nav Grunerløkka",
        telefon: String? = null,
        epost: String? = null,
        erSkjermet: Boolean = false,
        adresse: Adresse? = lagAdresse(),
        adressebeskyttelse: Adressebeskyttelse? = null,
        oppfolgingsperioder: List<Oppfolgingsperiode> = listOf(lagOppfolgingsperiode()),
        innsatsgruppe: Innsatsgruppe? = Innsatsgruppe.STANDARD_INNSATS,
        erDigital: Boolean = true,
    ) = NavBrukerModel(
        personident = personident,
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        etternavn = etternavn,
        navVeileder = navVeileder,
        navEnhet = navEnhet,
        telefon = telefon,
        epost = epost,
        erSkjermet = erSkjermet,
        adresse = adresse,
        adressebeskyttelse = adressebeskyttelse,
        oppfolgingsperioder = oppfolgingsperioder,
        innsatsgruppe = innsatsgruppe,
        erDigital = erDigital,
    )

    fun lagGjennomforingModel(
        id: UUID = UUID.randomUUID(),
        type: GjennomforingType = GjennomforingType.Gruppe,
        tiltak: Tiltakstype = lagTiltakstype(),
        navn: String = "Test Deltakerliste ${tiltak.tiltakskode}",
        status: GjennomforingStatusType = GjennomforingStatusType.GJENNOMFORES,
        startDato: LocalDate? = LocalDate.now().minusMonths(1),
        sluttDato: LocalDate? = LocalDate.now().plusYears(1),
        oppstart: Oppstartstype? = finnOppstartstype(tiltak.tiltakskode),
        arrangor: ArrangorModel? =
            ArrangorModel(
                navn = "Arrangor 1",
                organisasjonsnummer = no.nav.amt.lib.testing.utils.TestData
                    .randomOrgnr(),
            ),
        apentForPamelding: Boolean = true,
        oppmoteSted: String? = "~oppmoteSted~",
        pameldingstype: GjennomforingPameldingType? = GjennomforingPameldingType.DIREKTE_VEDTAK,
    ) = GjennomforingModel(
        id = id,
        type = type,
        tiltak = tiltak,
        navn = navn,
        status = status,
        startDato = startDato,
        sluttDato = sluttDato,
        oppstart = oppstart,
        arrangor = arrangor,
        apentForPamelding = apentForPamelding,
        oppmoteSted = oppmoteSted,
        pameldingstype = pameldingstype,
    )

    fun lagDeltakerModel(
        navBrukerResponse: NavBrukerResponse = lagNavBrukerResponse(),
        gjennomforingResponse: GjennomforingResponse = lagGjennomforingResponse(),
        deltakelsesinnhold: Deltakelsesinnhold? = lagDeltakelsesinnhold(),
        endringsforslagFraArrangor: List<Forslag> = emptyList(),
        status: DeltakerStatus = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
    ) = ModelMapper.toDeltaker(
        lagDeltakerResponse(
            navBruker = navBrukerResponse,
            deltakerliste = gjennomforingResponse,
            deltakelsesinnhold = deltakelsesinnhold,
            endringsforslagFraArrangor = endringsforslagFraArrangor,
            status = status,
        ),
    )

    fun lagDeltaker(
        id: UUID = UUID.randomUUID(),
        navBruker: NavBrukerModel = lagNavBrukerModel(),
        gjennomforing: GjennomforingModel = lagGjennomforingModel(),
        startdato: LocalDate? = LocalDate.now().minusMonths(3),
        sluttdato: LocalDate? = LocalDate.now().minusDays(1),
        dagerPerUke: Float? = 5F,
        deltakelsesprosent: Float? = 100F,
        bakgrunnsinformasjon: String? = "Søkes inn fordi...",
        innhold: List<Innhold> = gjennomforing.tiltak.innhold
            ?.innholdselementer
            ?.map { it.toInnhold() } ?: emptyList(),
        status: DeltakerStatus = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
        erLaastForEndringer: Boolean = false,
        erManueltDeltMedArrangor: Boolean = false,
    ): DeltakerModel = DeltakerModel(
        id = id,
        navBruker = navBruker,
        gjennomforing = gjennomforing,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        deltakelsesinnhold = Deltakelsesinnhold("ledetekst", innhold),
        status = status,
        erManueltDeltMedArrangor = erManueltDeltMedArrangor,
        vedtaksinformasjon = null,
        sistEndret = LocalDateTime.now(),
        erLaastForEndringer = erLaastForEndringer,
        endringsforslagFraArrangor = emptyList(),
        prisinformasjon = null,
        sisteVurdering = null,
        deltakelsesmengder = null,
        soktInnDato = LocalDate.now(),
        importertFraArena = null,
    )

    fun lagVedtaksinformasjonResponse() = VedtaksinformasjonResponse(
        fattet = LocalDateTime.now(),
        fattetAvNav = true,
        opprettet = LocalDateTime.now(),
        opprettetAv = "~veileder~",
        opprettetAvEnhet = "~enhet~",
        sistEndret = LocalDateTime.now(),
        sistEndretAv = "~veileder2~",
        sistEndretAvEnhet = "~enhet2~",
    )

    fun lagDeltakerResponse(deltaker: Deltaker) = lagDeltakerResponse(
        id = deltaker.id,
        startdato = deltaker.startdato,
        sluttdato = deltaker.sluttdato,
        dagerPerUke = deltaker.dagerPerUke,
        status = deltaker.status,
        deltakelsesprosent = deltaker.deltakelsesprosent,
        bakgrunnsinformasjon = deltaker.bakgrunnsinformasjon,
        deltakelsesinnhold = deltaker.deltakelsesinnhold,
        sistEndret = deltaker.sistEndret,
        erManueltDeltMedArrangor = deltaker.erManueltDeltMedArrangor,
        opprettet = deltaker.opprettet,
        endringsforslagFraArrangor = deltaker.historikk
            .filterIsInstance<DeltakerHistorikk.Forslag>()
            .map { it.forslag },
        navBruker = lagNavBrukerResponse(
            personident = deltaker.navBruker.personident,
            fornavn = deltaker.navBruker.fornavn,
            mellomnavn = deltaker.navBruker.mellomnavn,
            etternavn = deltaker.navBruker.etternavn,
            adressebeskyttelse = deltaker.navBruker.adressebeskyttelse,
            oppfolgingsperioder = deltaker.navBruker.oppfolgingsperioder,
            innsatsgruppe = deltaker.navBruker.innsatsgruppe,
            adresse = deltaker.navBruker.adresse,
            erSkjermet = deltaker.navBruker.erSkjermet,
            telefon = deltaker.navBruker.telefon,
            epost = deltaker.navBruker.epost,
        ),
        deltakerliste = lagGjennomforingResponse(
            id = deltaker.deltakerliste.id,
            tiltakstype = deltaker.deltakerliste.tiltak,
            navn = deltaker.deltakerliste.navn,
            status = deltaker.deltakerliste.status,
            startDato = deltaker.deltakerliste.startDato!!,
            sluttDato = deltaker.deltakerliste.sluttDato,
            oppstart = deltaker.deltakerliste.oppstart,
            apentForPamelding = deltaker.deltakerliste.apentForPamelding,
            oppmoteSted = deltaker.deltakerliste.oppmoteSted ?: "~oppmoteSted~",
            pameldingType = deltaker.deltakerliste.pameldingstype,
        ),
        vedtaksinformasjon = lagVedtaksinformasjonResponse(),
    )

    fun lagDeltakerResponse(deltaker: DeltakerModel) = lagDeltakerResponse(
        id = deltaker.id,
        startdato = deltaker.startdato,
        sluttdato = deltaker.sluttdato,
        dagerPerUke = deltaker.dagerPerUke,
        status = deltaker.status,
        deltakelsesprosent = deltaker.deltakelsesprosent,
        bakgrunnsinformasjon = deltaker.bakgrunnsinformasjon,
        deltakelsesinnhold = deltaker.deltakelsesinnhold,
        sistEndret = deltaker.sistEndret,
        erManueltDeltMedArrangor = deltaker.erManueltDeltMedArrangor,
        opprettet = LocalDateTime.now(),
        endringsforslagFraArrangor = emptyList(),
        navBruker = lagNavBrukerResponse(
            personident = deltaker.navBruker.personident,
            fornavn = deltaker.navBruker.fornavn,
            mellomnavn = deltaker.navBruker.mellomnavn,
            etternavn = deltaker.navBruker.etternavn,
            adressebeskyttelse = deltaker.navBruker.adressebeskyttelse,
            oppfolgingsperioder = deltaker.navBruker.oppfolgingsperioder,
            innsatsgruppe = deltaker.navBruker.innsatsgruppe,
            adresse = deltaker.navBruker.adresse,
            erSkjermet = deltaker.navBruker.erSkjermet,
            telefon = deltaker.navBruker.telefon,
            epost = deltaker.navBruker.epost,
        ),
        deltakerliste = lagGjennomforingResponse(
            id = deltaker.gjennomforing.id,
            tiltakstype = deltaker.gjennomforing.tiltak,
            navn = deltaker.gjennomforing.navn,
            status = deltaker.gjennomforing.status,
            startDato = deltaker.gjennomforing.startDato!!,
            sluttDato = deltaker.gjennomforing.sluttDato,
            oppstart = deltaker.gjennomforing.oppstart!!,
            apentForPamelding = deltaker.gjennomforing.apentForPamelding,
            oppmoteSted = deltaker.gjennomforing.oppmoteSted ?: "~oppmoteSted~",
            pameldingType = deltaker.gjennomforing.pameldingstype,
        ),
        vedtaksinformasjon = lagVedtaksinformasjonResponse(),
    )

    fun lagDeltakerResponse(
        id: UUID = UUID.randomUUID(),
        navBruker: NavBrukerResponse = lagNavBrukerResponse(),
        deltakerliste: GjennomforingResponse = lagGjennomforingResponse(),
        startdato: LocalDate? = LocalDate.now().minusMonths(3),
        sluttdato: LocalDate? = LocalDate.now().minusDays(1),
        dagerPerUke: Float? = 5F,
        deltakelsesprosent: Float? = 100F,
        bakgrunnsinformasjon: String? = "Søkes inn fordi...",
        status: DeltakerStatus = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
        sistEndret: LocalDateTime = LocalDateTime.now(),
        erManueltDeltMedArrangor: Boolean = false,
        deltakelsesinnhold: Deltakelsesinnhold? = lagDeltakelsesinnhold(),
        vedtaksinformasjon: VedtaksinformasjonResponse? = lagVedtaksinformasjonResponse(),
        endringsforslagFraArrangor: List<Forslag> = listOf(lagForslag()),
        prisinformasjon: String? = null,
        opprettet: LocalDateTime = LocalDateTime.now(),
        erLaastForEndringer: Boolean = false,
    ) = DeltakerResponse(
        id = id,
        status = status,
        navBruker = navBruker,
        gjennomforing = deltakerliste,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        deltakelsesinnhold = deltakelsesinnhold,
        vedtaksinformasjon = vedtaksinformasjon,
        erManueltDeltMedArrangor = erManueltDeltMedArrangor,
        kilde = Kilde.KOMET,
        sistEndret = sistEndret,
        opprettet = opprettet,
        erLaastForEndringer = erLaastForEndringer,
        endringsforslagFraArrangor = endringsforslagFraArrangor,
        prisinformasjon = prisinformasjon,
        sisteVurdering = null,
        soktInnDato = LocalDate.now().minusMonths(2),
        deltakelsesmengder = DeltakelsesmengderResponse(
            nesteDeltakelsesmengde = DeltakelsesmengdeResponse(
                deltakelsesprosent = 100F,
                dagerPerUke = 5F,
                gyldigFra = LocalDate.now().minusMonths(3),
            ),
            sisteDeltakelsesmengde = DeltakelsesmengdeResponse(
                deltakelsesprosent = 50F,
                dagerPerUke = 3F,
                gyldigFra = LocalDate.now().minusMonths(1),
            ),
        ),
        importertFraArena = null,
    )

    fun lagTiltakskoordinatorDeltakerResponse(
        id: UUID = UUID.randomUUID(),
        navBruker: TiltakskoordinatorNavBrukerResponse = lagTiltakskoordinatorNavBrukerResponse(),
        status: DeltakerStatus = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
        startdato: LocalDate? = LocalDate.now().minusMonths(3),
        sluttdato: LocalDate? = LocalDate.now().minusDays(1),
        erManueltDeltMedArrangor: Boolean = false,
        harAktivtForslag: Boolean = false,
        sisteVurderingstype: Vurderingstype? = null,
        soktInnDato: LocalDate? = LocalDate.now().minusMonths(2),
        kanEndres: Boolean = true,
    ) = TiltakskoordinatorDeltakerIListeResponse(
        id = id,
        status = status,
        navBruker = navBruker,
        startdato = startdato,
        sluttdato = sluttdato,
        soktInnDato = soktInnDato,
        erManueltDeltMedArrangor = erManueltDeltMedArrangor,
        harAktivtForslag = harAktivtForslag,
        sisteVurderingstype = sisteVurderingstype,
        kanEndres = kanEndres,
    )

    fun lagTiltakskoordinatorNavBrukerResponse(
        personident: String = randomIdent(),
        fornavn: String = "Fornavn",
        mellomnavn: String? = "Mellomnavn",
        etternavn: String = "Etternavn",
        adressebeskyttelse: Adressebeskyttelse? = null,
        erSkjermet: Boolean = false,
        ikkeDigitalOgManglerAdresse: Boolean = false,
    ) = TiltakskoordinatorNavBrukerResponse(
        personident = personident,
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        etternavn = etternavn,
        erSkjermet = erSkjermet,
        adressebeskyttelse = adressebeskyttelse,
        ikkeDigitalOgManglerAdresse = ikkeDigitalOgManglerAdresse,
        navEnhet = "Nav Grunerløkka",
    )

    fun lagVurdering(
        id: UUID = UUID.randomUUID(),
        deltakerId: UUID = UUID.randomUUID(),
        opprettetAvArrangorAnsattId: UUID = UUID.randomUUID(),
        opprettet: LocalDateTime = LocalDateTime.now().minusMonths(1),
        vurderingstype: Vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE,
        begrunnelse: String? = "Begrunnelse på vurdering",
    ) = Vurdering(
        id = id,
        deltakerId = deltakerId,
        opprettetAvArrangorAnsattId = opprettetAvArrangorAnsattId,
        opprettet = opprettet,
        vurderingstype = vurderingstype,
        begrunnelse = begrunnelse,
    )

    fun lagDeltakerStatus(
        type: DeltakerStatus.Type,
        aarsak: DeltakerStatus.Aarsak,
    ) = lagDeltakerStatus(type, aarsak.type, aarsak.beskrivelse)

    fun lagDeltakerStatus(
        statusType: DeltakerStatus.Type,
        aarsakType: DeltakerStatus.Aarsak.Type? = null,
        beskrivelse: String? = null,
    ) = lagDeltakerStatus(
        statusType = statusType,
        aarsakType = aarsakType,
        aarsakBeskrivelse = beskrivelse,
    )

    fun lagDeltakerStatus(
        id: UUID = UUID.randomUUID(),
        statusType: DeltakerStatus.Type = DeltakerStatus.Type.DELTAR,
        aarsakType: DeltakerStatus.Aarsak.Type? = null,
        aarsakBeskrivelse: String? = null,
        gyldigFra: LocalDateTime = LocalDateTime.now(),
        opprettet: LocalDateTime = LocalDateTime.now(),
    ) = DeltakerStatus(
        id = id,
        type = statusType,
        aarsak = aarsakType?.let { DeltakerStatus.Aarsak(it, aarsakBeskrivelse) },
        gyldigFra = gyldigFra,
        gyldigTil = null, // lagres ikke i databasen
        opprettet = opprettet,
    )

    fun lagVedtak(
        id: UUID = UUID.randomUUID(),
        deltakerVedVedtak: DeltakerModel = lagDeltakerModel(
            status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        ),
        deltakerId: UUID = deltakerVedVedtak.id,
        fattet: LocalDateTime? = null,
        gyldigTil: LocalDateTime? = null,
        fattetAvNav: Boolean = false,
        opprettet: LocalDateTime = LocalDateTime.now(),
        opprettetAv: UUID = UUID.randomUUID(),
        opprettetAvEnhet: UUID = UUID.randomUUID(),
        sistEndret: LocalDateTime = opprettet,
        sistEndretAv: UUID = opprettetAv,
        sistEndretAvEnhet: UUID = opprettetAvEnhet,
    ) = Vedtak(
        id,
        deltakerId,
        fattet,
        gyldigTil,
        deltakerVedVedtak.toDeltakerVedVedtak(),
        fattetAvNav,
        opprettet,
        opprettetAv,
        opprettetAvEnhet,
        sistEndret,
        sistEndretAv,
        sistEndretAvEnhet,
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

    fun lagNavBrukerResponse(
        personident: String = randomIdent(),
        fornavn: String = "Fornavn",
        mellomnavn: String? = "Mellomnavn",
        etternavn: String = "Etternavn",
        adressebeskyttelse: Adressebeskyttelse? = null,
        oppfolgingsperioder: List<Oppfolgingsperiode> = listOf(lagOppfolgingsperiode()),
        innsatsgruppe: Innsatsgruppe? = Innsatsgruppe.STANDARD_INNSATS,
        adresse: Adresse? = lagAdresse(),
        erSkjermet: Boolean = false,
        telefon: String? = null,
        epost: String? = null,
    ) = NavBrukerResponse(
        personident = personident,
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        etternavn = etternavn,
        erSkjermet = erSkjermet,
        adresse = adresse,
        adressebeskyttelse = adressebeskyttelse,
        oppfolgingsperioder = oppfolgingsperioder,
        innsatsgruppe = innsatsgruppe,
        telefon = telefon,
        epost = epost,
        erDigital = true,
        navVeileder = NavVeilederResponse("Nav Veiledersen", null, null),
        navEnhet = "Nav Grunerløkka",
    )

    private fun finnOppstartstype(type: Tiltakskode) = when (type) {
        Tiltakskode.JOBBKLUBB,
        Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
        Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
        -> Oppstartstype.FELLES

        else -> Oppstartstype.LOPENDE
    }

    fun lagNavAnsatteForDeltaker(deltaker: Deltaker) = listOfNotNull(
        deltaker.vedtaksinformasjon?.sistEndretAv,
        deltaker.vedtaksinformasjon?.opprettetAv,
    ).distinct().map { lagNavAnsatt(id = it) }

    fun lagNavAnsatteForHistorikk(historikk: List<DeltakerHistorikk>) = historikk
        .flatMap { it.navAnsatte() }
        .distinct()
        .map { lagNavAnsatt(id = it) }

    fun lagNavEnheterForHistorikk(historikk: List<DeltakerHistorikk>) = historikk
        .flatMap { it.navEnheter() }
        .distinct()
        .map { lagNavEnhet(id = it) }

    fun leggTilHistorikk(
        deltaker: DeltakerModel = lagDeltakerModel(),
        antallVedtak: Int = 1,
        antallEndringer: Int = 1,
        antallEndringerFraArrangor: Int = 1,
    ): List<DeltakerHistorikk> {
        val vedtak = (1..antallVedtak).map {
            val fattet = it == antallVedtak
            lagVedtak(
                deltakerVedVedtak = deltaker,
                fattet = if (fattet) LocalDateTime.now() else null,
                gyldigTil = if (fattet) null else LocalDateTime.now(),
                fattetAvNav = fattet,
            )
        }

        val endringer = (1..antallEndringer).map { lagDeltakerEndring(deltakerId = deltaker.id) }

        val endringerFraArrangor = (1..antallEndringerFraArrangor).map { lagEndringFraArrangor(deltakerId = deltaker.id) }

        return vedtak.map { DeltakerHistorikk.Vedtak(it) } + endringer.map { DeltakerHistorikk.Endring(it) } +
            endringerFraArrangor.map { DeltakerHistorikk.EndringFraArrangor(it) }
    }

    fun lagTiltakskoordinatorTilgang(
        id: UUID = UUID.randomUUID(),
        deltakerliste: Deltakerliste = lagDeltakerliste(),
        navAnsatt: NavAnsatt = lagNavAnsatt(),
        gyldigFra: LocalDateTime = LocalDateTime.now(),
        gyldigTil: LocalDateTime? = null,
    ) = TiltakskoordinatorDeltakerlisteTilgang(
        id = id,
        navAnsattId = navAnsatt.id,
        deltakerlisteId = deltakerliste.id,
        gyldigFra = gyldigFra,
        gyldigTil = gyldigTil,
    )
}

fun Deltaker.endre(deltakerEndring: DeltakerEndring): Deltaker {
    val deltaker = when (val endring = deltakerEndring.endring) {
        is DeltakerEndring.Endring.EndrePrisinfo -> this

        is DeltakerEndring.Endring.AvsluttDeltakelse -> this.copy(
            sluttdato = endring.sluttdato,
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = endring.aarsak?.toStatusAarsak()?.type,
                aarsakBeskrivelse = endring.aarsak?.beskrivelse,
            ),
        )

        is DeltakerEndring.Endring.EndreAvslutning -> this.copy(
            status = TestData.lagDeltakerStatus(
                statusType = if (endring.harFullfort == true) DeltakerStatus.Type.FULLFORT else DeltakerStatus.Type.AVBRUTT,
                aarsakType = endring.aarsak?.toStatusAarsak()?.type,
                aarsakBeskrivelse = endring.aarsak?.beskrivelse,
            ),
        )

        is DeltakerEndring.Endring.AvbrytDeltakelse -> this.copy(
            sluttdato = endring.sluttdato,
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.AVBRUTT,
                aarsakType = endring.aarsak.toStatusAarsak().type,
                aarsakBeskrivelse = endring.aarsak.beskrivelse,
            ),
        )

        is DeltakerEndring.Endring.EndreBakgrunnsinformasjon ->
            this.copy(bakgrunnsinformasjon = endring.bakgrunnsinformasjon)

        is DeltakerEndring.Endring.EndreDeltakelsesmengde -> this.copy(
            dagerPerUke = endring.dagerPerUke,
            deltakelsesprosent = endring.deltakelsesprosent,
        )

        is DeltakerEndring.Endring.EndreInnhold -> this.copy(
            deltakelsesinnhold = Deltakelsesinnhold(
                endring.ledetekst,
                endring.innhold,
            ),
        )

        is DeltakerEndring.Endring.EndreSluttarsak ->
            this.copy(status = this.status.copy(aarsak = endring.aarsak.toStatusAarsak()))

        is DeltakerEndring.Endring.EndreSluttdato -> this.copy(sluttdato = endring.sluttdato)
        is DeltakerEndring.Endring.EndreStartdato -> this.copy(startdato = endring.startdato, sluttdato = endring.sluttdato)
        is DeltakerEndring.Endring.ForlengDeltakelse -> this.copy(sluttdato = endring.sluttdato)
        is DeltakerEndring.Endring.IkkeAktuell -> this.copy(
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.IKKE_AKTUELL,
                aarsakType = endring.aarsak.toStatusAarsak().type,
                aarsakBeskrivelse = endring.aarsak.beskrivelse,
            ),
        )

        is DeltakerEndring.Endring.ReaktiverDeltakelse -> this.copy(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
        )

        is DeltakerEndring.Endring.FjernOppstartsdato -> this.copy(startdato = null, sluttdato = null)
    }
    return deltaker.copy(historikk = this.historikk.plus(DeltakerHistorikk.Endring(deltakerEndring)))
}

fun DeltakerEndring.Aarsak.toStatusAarsak() = DeltakerStatus.Aarsak(
    type = DeltakerStatus.Aarsak.Type.valueOf(this.type.name),
    beskrivelse = this.beskrivelse,
)
