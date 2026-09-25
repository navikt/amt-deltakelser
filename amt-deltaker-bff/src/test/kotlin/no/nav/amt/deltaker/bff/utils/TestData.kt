package no.nav.amt.deltaker.bff.utils

import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.commonresponse.DeltakelsesinnholdResponse.Companion.fulltInnhold
import no.nav.amt.deltaker.bff.model.ArrangorModel
import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.model.Deltakerliste
import no.nav.amt.deltaker.bff.model.GjennomforingModel
import no.nav.amt.deltaker.bff.model.NavBrukerModel
import no.nav.amt.deltaker.bff.model.Tiltak
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorDeltakerlisteTilgang
import no.nav.amt.internapi.deltaker.getInnholdselementer
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengdeResponse
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengderResponse
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.NavBrukerResponse
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
import no.nav.amt.internapi.deltaker.response.VedtaksinformasjonResponse
import no.nav.amt.internapi.deltaker.response.VisningsnavnResponse
import no.nav.amt.internapi.deltaker.toInnhold
import no.nav.amt.internapi.tiltakskoordinator.response.TiltakskoordinatorDeltakerIListeResponse
import no.nav.amt.internapi.tiltakskoordinator.response.TiltakskoordinatorNavBrukerResponse
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
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
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde.Companion.FALLBACK_DELTAKELSESPROSENT
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.kafka.AmtGjennomforingPayload
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
import java.util.UUID

object TestData {
    fun input(n: Int) = (1..n).map { ('a'..'z').random() }.joinToString("")

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
        status: GjennomforingStatusType = GjennomforingStatusType.GJENNOMFORES,
        sluttDato: LocalDate? = LocalDate.now().plusYears(1),
        oppstart: Oppstartstype = finnOppstartstype(tiltakstype.tiltakskode),
        pameldingType: GjennomforingPameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
    ) = Deltakerliste(
        id = id,
        status = status,
        sluttDato = sluttDato,
        oppstart = oppstart,
        arrangor = arrangor,
        pameldingstype = pameldingType,
    )

    /**
     * Bygger den slanke bff-lokale [Tiltak]-raden fra en delt [Tiltakstype]. Brukes i tester for å
     * upserte tiltakstype-raden som `deltakerliste.tiltakstype_id` FK-en peker på.
     */
    fun tiltakAv(tiltakstype: Tiltakstype) = Tiltak(
        id = tiltakstype.id,
        navn = tiltakstype.navn,
        tiltakskode = tiltakstype.tiltakskode,
    )

    fun lagAmtGjennomforingPayload(
        id: UUID = UUID.randomUUID(),
        tiltakstype: Tiltakstype = lagTiltakstype(),
        organisasjonsnummer: String = no.nav.amt.lib.testing.utils.TestData
            .randomOrgnr(),
        type: GjennomforingType = GjennomforingType.Gruppe,
        status: GjennomforingStatusType = GjennomforingStatusType.GJENNOMFORES,
        oppstart: Oppstartstype = finnOppstartstype(tiltakstype.tiltakskode),
        pameldingstype: GjennomforingPameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
        sluttDato: LocalDate? = LocalDate.now().plusYears(1),
    ) = AmtGjennomforingPayload(
        id = id,
        type = type,
        tiltak = AmtGjennomforingPayload.TiltakPayload(
            id = tiltakstype.id,
            navn = tiltakstype.navn,
            tiltakskode = tiltakstype.tiltakskode,
            innhold = tiltakstype.innhold,
        ),
        arrangor = AmtGjennomforingPayload.Arrangor(organisasjonsnummer),
        status = status,
        oppstart = oppstart,
        pameldingstype = pameldingstype,
        navn = "Test Deltakerliste ${tiltakstype.tiltakskode}",
        lopenummer = "2026-01",
        startDato = LocalDate.now().minusMonths(1),
        sluttDato = sluttDato,
        tilgjengeligForArrangorFraOgMedDato = null,
        apentForPamelding = true,
        antallPlasser = 42,
        oppmoteSted = "~oppmoteSted~",
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
        prisinformasjon = PrisinformasjonDto.Anskaffelse(
            pris = 10000,
        ),
        prisinformasjonTilGodkjenning = PrisinformasjonDto.Anskaffelse(
            pris = 20000,
        ),
        visningsnavn = VisningsnavnResponse("Tittel"),
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
        id = UUID.randomUUID(),
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

    fun lagDeltakerOld(
        id: UUID = UUID.randomUUID(),
        navBruker: NavBruker = lagNavBruker(),
        deltakerliste: Deltakerliste = lagDeltakerliste(),
        startdato: LocalDate? = LocalDate.now().minusMonths(3),
        sluttdato: LocalDate? = LocalDate.now().minusDays(1),
        dagerPerUke: Float? = 5F,
        deltakelsesprosent: Float? = FALLBACK_DELTAKELSESPROSENT,
        bakgrunnsinformasjon: String? = "Søkes inn fordi...",
        tiltakstype: Tiltakstype = lagTiltakstype(),
        innhold: List<Innhold> = tiltakstype.innhold
            ?.innholdselementer
            ?.map { it.toInnhold() } ?: emptyList(),
        status: DeltakerStatus = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
        kanEndres: Boolean = true,
        erManueltDeltMedArrangor: Boolean = false,
        createdAt: LocalDateTime = LocalDateTime.now(),
        sistEndret: LocalDateTime = LocalDateTime.now(),
    ): DeltakerModel = DeltakerModel(
        id = id,
        navBruker = lagNavBrukerModel(
            personident = navBruker.personident,
            fornavn = navBruker.fornavn,
            mellomnavn = navBruker.mellomnavn,
            etternavn = navBruker.etternavn,
            erSkjermet = navBruker.erSkjermet,
            adresse = navBruker.adresse,
            adressebeskyttelse = navBruker.adressebeskyttelse,
            oppfolgingsperioder = navBruker.oppfolgingsperioder,
            innsatsgruppe = navBruker.innsatsgruppe,
            telefon = navBruker.telefon,
            epost = navBruker.epost,
        ),
        gjennomforing = lagGjennomforingModel(
            id = deltakerliste.id,
            tiltak = tiltakstype,
            status = deltakerliste.status,
            sluttDato = deltakerliste.sluttDato,
            oppstart = deltakerliste.oppstart,
            arrangor = ArrangorModel(
                navn = deltakerliste.arrangor.navn,
                organisasjonsnummer = deltakerliste.arrangor.organisasjonsnummer,
            ),
            pameldingstype = deltakerliste.pameldingstype,
        ),
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        deltakelsesinnhold = Deltakelsesinnhold("ledetekst", innhold),
        vedtaksinformasjon = null,
        status = status,
        sistEndret = sistEndret,
        erManueltDeltMedArrangor = erManueltDeltMedArrangor,
        erLaastForEndringer = !kanEndres,
        endringsforslagFraArrangor = emptyList(),
        prisinformasjon = null,
        sisteVurdering = null,
        deltakelsesmengder = null,
        soktInnDato = createdAt.toLocalDate(),
        importertFraArena = null,
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
        deltakelsesprosent: Float? = FALLBACK_DELTAKELSESPROSENT,
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
        deltakelsesprosent: Float? = FALLBACK_DELTAKELSESPROSENT,
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
                deltakelsesprosent = FALLBACK_DELTAKELSESPROSENT,
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
