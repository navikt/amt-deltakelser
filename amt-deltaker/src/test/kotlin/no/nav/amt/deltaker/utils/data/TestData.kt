package no.nav.amt.deltaker.utils.data

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.deltaker.vurdering.Vurdering
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.InnsokPaaFellesOppstart
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.Oppfolgingsperiode
import no.nav.amt.lib.models.person.address.Adresse
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.models.person.address.Bostedsadresse
import no.nav.amt.lib.models.person.address.Kontaktadresse
import no.nav.amt.lib.models.person.address.Matrikkeladresse
import no.nav.amt.lib.models.person.address.Vegadresse
import no.nav.amt.lib.models.person.dto.NavEnhetDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

object TestData {
    fun randomIdent() = (10_00_00_00_000..31_12_00_99_999).random().toString()

    fun randomNavIdent() = ('A'..'Z').random().toString() + (100_000..999_999).random().toString()

    fun randomEnhetsnummer() = (1000..9999999999).random().toString()

    fun randomOrgnr() = (900_000_000..999_999_998).random().toString()

    fun lagArrangor(
        id: UUID = UUID.randomUUID(),
        navn: String = "Arrangor 1",
        organisasjonsnummer: String = randomOrgnr(),
        overordnetArrangorId: UUID? = null,
    ) = Arrangor(id, navn, organisasjonsnummer, overordnetArrangorId)

    fun lagNavAnsatt(
        id: UUID = UUID.randomUUID(),
        navIdent: String = randomNavIdent(),
        navn: String = "Veileder Veiledersen",
        telefon: String = "99988777",
        epost: String = "ansatt@nav.no",
        navEnhetId: UUID? = UUID.randomUUID(),
    ) = NavAnsatt(id, navIdent, navn, epost, telefon, navEnhetId)

    fun lagNavEnhet(
        id: UUID = UUID.randomUUID(),
        enhetsnummer: String = randomEnhetsnummer(),
        navn: String = "Nav Testheim",
    ) = NavEnhet(id, enhetsnummer, navn)

    fun lagAdresse(): Adresse = Adresse(
        bostedsadresse = Bostedsadresse(
            coAdressenavn = "C/O Gutterommet",
            vegadresse = null,
            matrikkeladresse = Matrikkeladresse(
                tilleggsnavn = "Gården",
                postnummer = "0484",
                poststed = "OSLO",
            ),
        ),
        oppholdsadresse = null,
        kontaktadresse = Kontaktadresse(
            coAdressenavn = null,
            vegadresse = Vegadresse(
                husnummer = "1",
                husbokstav = null,
                adressenavn = "Gate",
                tilleggsnavn = null,
                postnummer = "1234",
                poststed = "MOSS",
            ),
            postboksadresse = null,
        ),
    )

    fun lagNavBruker(
        personId: UUID = UUID.randomUUID(),
        personident: String = randomIdent(),
        fornavn: String = "Fornavn",
        mellomnavn: String? = "Mellomnavn",
        etternavn: String = "Etternavn",
        navVeilederId: UUID? = UUID.randomUUID(),
        navEnhetId: UUID? = UUID.randomUUID(),
        telefon: String? = "77788999",
        epost: String? = "nav_bruker@gmail.com",
        erSkjermet: Boolean = false,
        adresse: Adresse? = lagAdresse(),
        adressebeskyttelse: Adressebeskyttelse? = null,
        oppfolgingsperioder: List<Oppfolgingsperiode> = listOf(lagOppfolgingsperiode()),
        innsatsgruppe: Innsatsgruppe? = Innsatsgruppe.STANDARD_INNSATS,
    ) = NavBruker(
        personId,
        personident,
        fornavn,
        mellomnavn,
        etternavn,
        navVeilederId,
        navEnhetId,
        telefon,
        epost,
        erSkjermet,
        adresse,
        adressebeskyttelse,
        oppfolgingsperioder,
        innsatsgruppe,
    )

    fun lagOppfolgingsperiode(
        id: UUID = UUID.randomUUID(),
        startdato: LocalDateTime = LocalDateTime.now().minusMonths(1),
        sluttdato: LocalDateTime? = null,
    ) = Oppfolgingsperiode(
        id,
        startdato,
        sluttdato,
    )

    private val tiltakstypeCache = mutableMapOf<Tiltakskode, Tiltakstype>()

    fun lagDeltakerRegistreringInnhold(
        innholdselementer: List<Innholdselement> = listOf(Innholdselement("Tekst", "kode")),
        ledetekst: String = "Beskrivelse av tilaket",
    ) = DeltakerRegistreringInnhold(innholdselementer, ledetekst)

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
        arrangor: Arrangor = lagArrangor(),
        tiltakstype: Tiltakstype = lagTiltakstype(),
        navn: String = "Test Deltakerliste ${tiltakstype.tiltakskode}",
        gjennomforingstype: GjennomforingType = GjennomforingType.Gruppe,
        status: GjennomforingStatusType = GjennomforingStatusType.GJENNOMFORES,
        startDato: LocalDate = LocalDate.now().minusMonths(1),
        sluttDato: LocalDate? = LocalDate.now().plusYears(1),
        oppstart: Oppstartstype = finnOppstartstype(tiltakstype.tiltakskode),
        oppmoteSted: String? = "~oppmoteSted~",
        apentForPamelding: Boolean = true,
        pameldingType: GjennomforingPameldingType? = GjennomforingPameldingType.TRENGER_GODKJENNING,
    ) = Deltakerliste(
        id = id,
        tiltakstype = tiltakstype,
        gjennomforingstype = gjennomforingstype,
        navn = navn,
        status = status,
        startDato = startDato,
        sluttDato = sluttDato,
        oppstart = oppstart,
        apentForPamelding = apentForPamelding,
        oppmoteSted = oppmoteSted,
        arrangor = arrangor,
        pameldingstype = pameldingType,
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
        tiltakskode = deltakerliste.tiltakstype.tiltakskode,
        arrangor = GjennomforingV2KafkaPayload.Arrangor(deltakerliste.arrangor.organisasjonsnummer),
        oppdatertTidspunkt = OffsetDateTime.now(),
        opprettetTidspunkt = OffsetDateTime.now(),
        pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
    )

    fun lagDeltakerlistePayload(arrangor: Arrangor = lagArrangor(), deltakerliste: Deltakerliste = lagDeltakerliste(arrangor = arrangor)) =
        GjennomforingV2KafkaPayload.Gruppe(
            id = deltakerliste.id,
            navn = deltakerliste.navn,
            tiltakskode = deltakerliste.tiltakstype.tiltakskode,
            startDato = deltakerliste.startDato!!,
            sluttDato = deltakerliste.sluttDato,
            status = deltakerliste.status!!,
            oppstart = deltakerliste.oppstart!!,
            apentForPamelding = deltakerliste.apentForPamelding,
            oppmoteSted = deltakerliste.oppmoteSted,
            tilgjengeligForArrangorFraOgMedDato = null,
            antallPlasser = 42,
            deltidsprosent = 42.0,
            arrangor = GjennomforingV2KafkaPayload.Arrangor(deltakerliste.arrangor.organisasjonsnummer),
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

    fun lagInnsoktPaaKurs(
        id: UUID = UUID.randomUUID(),
        deltakerId: UUID = UUID.randomUUID(),
        innsokt: LocalDateTime = LocalDateTime.now(),
        innsoktAv: UUID = UUID.randomUUID(),
        innsoktAvEnhet: UUID = UUID.randomUUID(),
        deltakelsesinnholdVedInnsok: Deltakelsesinnhold = Deltakelsesinnhold("", emptyList()),
        utkastDelt: LocalDateTime = LocalDateTime.now().minusDays(2),
        utkastGodkjentAvNav: Boolean = false,
    ) = InnsokPaaFellesOppstart(
        id = id,
        deltakerId = deltakerId,
        innsokt = innsokt,
        innsoktAv = innsoktAv,
        innsoktAvEnhet = innsoktAvEnhet,
        deltakelsesinnholdVedInnsok = deltakelsesinnholdVedInnsok,
        utkastDelt = utkastDelt,
        utkastGodkjentAvNav = utkastGodkjentAvNav,
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

        else -> Oppstartstype.LOPENDE
    }
}
