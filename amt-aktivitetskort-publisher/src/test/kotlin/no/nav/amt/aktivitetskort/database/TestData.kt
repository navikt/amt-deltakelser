package no.nav.amt.aktivitetskort.database

import no.nav.amt.aktivitetskort.domain.AktivitetStatus
import no.nav.amt.aktivitetskort.domain.Aktivitetskort
import no.nav.amt.aktivitetskort.domain.Arrangor
import no.nav.amt.aktivitetskort.domain.Deltaker
import no.nav.amt.aktivitetskort.domain.DeltakerStatusModel
import no.nav.amt.aktivitetskort.domain.Deltakerliste
import no.nav.amt.aktivitetskort.domain.Detalj
import no.nav.amt.aktivitetskort.domain.EndretAv
import no.nav.amt.aktivitetskort.domain.Handling
import no.nav.amt.aktivitetskort.domain.IdentType
import no.nav.amt.aktivitetskort.domain.LenkeType
import no.nav.amt.aktivitetskort.domain.Melding
import no.nav.amt.aktivitetskort.domain.Oppfolgingsperiode
import no.nav.amt.aktivitetskort.domain.Tag
import no.nav.amt.aktivitetskort.domain.Tiltak
import no.nav.amt.aktivitetskort.domain.Tiltakstype
import no.nav.amt.aktivitetskort.domain.displayText
import no.nav.amt.aktivitetskort.domain.toAktivitetskortTiltakstype
import no.nav.amt.aktivitetskort.kafka.consumer.dto.ArrangorDto
import no.nav.amt.aktivitetskort.service.StatusMapping.deltakerStatusTilAktivitetStatus
import no.nav.amt.aktivitetskort.service.StatusMapping.deltakerStatusTilEtikett
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerStatusDto
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.Kontaktinformasjon
import no.nav.amt.lib.models.deltaker.Navn
import no.nav.amt.lib.models.deltaker.Personalia
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

object TestData {
    const val VEILEDER_URL_BASEPATH = "https://intern.veileder"
    const val DELTAKER_URL_BASEPATH = "https://ekstern.deltaker"

    fun melding(
        deltakerId: UUID = UUID.randomUUID(),
        deltakerlisteId: UUID = UUID.randomUUID(),
        arrangorId: UUID = UUID.randomUUID(),
        aktivitetskort: Aktivitetskort = aktivitetskort(),
        oppfolgingsperiode: UUID? = null,
    ) = Melding(aktivitetskort.id, deltakerId, deltakerlisteId, arrangorId, aktivitetskort, oppfolgingsperiode)

    fun aktivitetskort(
        id: UUID = UUID.randomUUID(),
        personIdent: String = UUID.randomUUID().toString(),
        tittel: String = UUID.randomUUID().toString(),
        aktivitetStatus: AktivitetStatus = AktivitetStatus.GJENNOMFORES,
        startDato: LocalDate = LocalDate.now().minusDays(7),
        sluttDato: LocalDate = LocalDate.now().plusDays(7),
        beskrivelse: String? = null,
        endretAv: EndretAv = EndretAv(UUID.randomUUID().toString(), IdentType.SYSTEM),
        endretAvTidspunkt: LocalDateTime = LocalDateTime.now(),
        avtaltMedNav: Boolean = true,
        detaljer: List<Detalj> = listOf(Detalj("Label", "Verdi")),
        etiketter: List<Tag> = listOf(),
        tiltakstype: Tiltakskode = Tiltakskode.OPPFOLGING,
    ) = Aktivitetskort(
        id = id,
        personident = personIdent,
        tittel = tittel,
        aktivitetStatus = aktivitetStatus,
        startDato = startDato,
        sluttDato = sluttDato,
        beskrivelse = beskrivelse,
        endretAv = endretAv,
        endretTidspunkt = endretAvTidspunkt,
        avtaltMedNav = avtaltMedNav,
        oppgave = null,
        handlinger = null,
        detaljer = detaljer,
        etiketter = etiketter,
        tiltakstype = tiltakstype.toAktivitetskortTiltakstype(),
    )

    fun aktivitetskort(
        id: UUID,
        deltaker: Deltaker,
        deltakerliste: Deltakerliste,
        arrangor: Arrangor,
    ) = Aktivitetskort(
        id = id,
        personident = deltaker.personident,
        tittel = Aktivitetskort.lagTittel(deltakerliste, arrangor),
        aktivitetStatus = deltakerStatusTilAktivitetStatus(deltaker.status.type).getOrThrow(),
        startDato = deltaker.oppstartsdato,
        sluttDato = deltaker.sluttdato,
        beskrivelse = null,
        endretAv = EndretAv("amt-aktivitetskort-publisher", IdentType.SYSTEM),
        endretTidspunkt = LocalDateTime.now(),
        avtaltMedNav = true,
        oppgave = null,
        handlinger = listOf(
            Handling(
                tekst = "Les mer om din deltakelse",
                subtekst = "",
                url = "$VEILEDER_URL_BASEPATH/${deltaker.id}",
                lenkeType = LenkeType.INTERN,
            ),
            Handling(
                tekst = "Les mer om din deltakelse",
                subtekst = "",
                url = "$DELTAKER_URL_BASEPATH/${deltaker.id}",
                lenkeType = LenkeType.EKSTERN,
            ),
        ),
        detaljer = listOfNotNull(
            Detalj("Status for deltakelse", displayText(deltaker.status)),
            Detalj("Arrangør", arrangor.navn),
        ),
        etiketter = listOfNotNull(deltakerStatusTilEtikett(deltaker.status)),
        tiltakstype = deltakerliste.tiltak.tiltakskode.toAktivitetskortTiltakstype(),
    )

    fun lagDeltaker(
        id: UUID = UUID.randomUUID(),
        personident: String = "fnr",
        deltakerlisteId: UUID = UUID.randomUUID(),
        status: DeltakerStatusModel =
            DeltakerStatusModel(
                type = DeltakerStatus.Type.DELTAR,
                aarsak = null,
                gyldigFra = LocalDate.now().atStartOfDay(),
            ),
        dagerPerUke: Float? = 5.0f,
        prosentStilling: Double? = 100.0,
        oppstartsdato: LocalDate? = LocalDate.now().minusWeeks(4),
        sluttdato: LocalDate? = LocalDate.now().plusWeeks(4),
        deltarPaKurs: Boolean = false,
        kilde: Kilde = Kilde.ARENA,
    ) = Deltaker(
        id,
        personident,
        deltakerlisteId,
        status,
        dagerPerUke,
        prosentStilling,
        oppstartsdato,
        sluttdato,
        deltarPaKurs,
        kilde,
    )

    fun oppfolgingsperiode(
        id: UUID = UUID.randomUUID(),
        startDato: LocalDateTime = LocalDateTime.now().minusWeeks(4),
        sluttdato: LocalDateTime? = null,
    ) = Oppfolgingsperiode(id, startDato = startDato, sluttDato = sluttdato)

    fun lagArrangor(
        id: UUID = UUID.randomUUID(),
        organisasjonsnummer: String = (100_000_000..900_000_000).random().toString(),
        navn: String = "Navn",
        overordnetArrangorId: UUID? = null,
    ) = Arrangor(id, organisasjonsnummer, navn, overordnetArrangorId)

    fun lagTiltak(
        navn: String = "Jobbklubb",
        tiltakskode: Tiltakskode = Tiltakskode.JOBBKLUBB,
    ) = Tiltak(navn = navn, tiltakskode = tiltakskode)

    fun lagDeltakerliste(
        id: UUID = UUID.randomUUID(),
        tiltak: Tiltak = lagTiltak(navn = "Oppfølging", tiltakskode = Tiltakskode.OPPFOLGING),
        navn: String = "~deltakerlistenavn~",
        arrangorId: UUID = UUID.randomUUID(),
    ) = Deltakerliste(id, tiltak, navn, arrangorId)

    fun lagEnkeltplassDeltakerlistePayload(
        arrangor: Arrangor = lagArrangor(),
        deltakerliste: Deltakerliste = lagDeltakerliste(arrangorId = arrangor.id),
    ) = GjennomforingV2KafkaPayload.Enkeltplass(
        id = deltakerliste.id,
        tiltakskode = deltakerliste.tiltak.tiltakskode,
        arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangor.organisasjonsnummer),
        oppdatertTidspunkt = OffsetDateTime.now(),
        opprettetTidspunkt = OffsetDateTime.now(),
        pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
        oppstart = Oppstartstype.ENKELTPLASS,
        prisinformasjon = null,
        status = GjennomforingStatusType.GJENNOMFORES,
    )

    fun lagGruppeDeltakerlistePayload(
        arrangor: Arrangor = lagArrangor(),
        deltakerliste: Deltakerliste = lagDeltakerliste(arrangorId = arrangor.id),
    ) = GjennomforingV2KafkaPayload.Gruppe(
        id = deltakerliste.id,
        navn = deltakerliste.navn,
        tiltakskode = deltakerliste.tiltak.tiltakskode,
        startDato = LocalDate.now(),
        sluttDato = LocalDate.now().plusDays(1),
        status = GjennomforingStatusType.GJENNOMFORES,
        oppstart = Oppstartstype.LOPENDE,
        apentForPamelding = true,
        oppmoteSted = null,
        tilgjengeligForArrangorFraOgMedDato = null,
        antallPlasser = 42,
        deltidsprosent = 42.0,
        arrangor = GjennomforingV2KafkaPayload.Arrangor(arrangor.organisasjonsnummer),
        oppdatertTidspunkt = OffsetDateTime.now(),
        opprettetTidspunkt = OffsetDateTime.now(),
        pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
    )

    data class MockContext(
        val deltaker: Deltaker = lagDeltaker(),
        val deltakerliste: Deltakerliste = lagDeltakerliste(id = deltaker.deltakerlisteId),
        val arrangor: Arrangor = lagArrangor(id = deltakerliste.arrangorId),
        val tiltakstype: Tiltakstype = Tiltakstype(
            id = UUID.randomUUID(),
            navn = "Oppfølging",
            tiltakskode = Tiltakskode.OPPFOLGING,
        ),
        val aktivitetskortId: UUID = UUID.randomUUID(),
        val aktivitetskort: Aktivitetskort = aktivitetskort(
            id = aktivitetskortId,
            deltaker = deltaker,
            deltakerliste = deltakerliste,
            arrangor = arrangor,
        ),
        val oppfolgingsperiodeId: UUID? = null,
        val melding: Melding = melding(
            deltakerId = deltaker.id,
            deltakerlisteId = deltakerliste.id,
            arrangorId = arrangor.id,
            aktivitetskort = aktivitetskort,
            oppfolgingsperiode = oppfolgingsperiodeId,
        ),
        val deltakerlisteGruppePayload: GjennomforingV2KafkaPayload.Gruppe = lagGruppeDeltakerlistePayload(
            deltakerliste = deltakerliste,
            arrangor = arrangor,
        ),
        val deltakerlisteEnkeltplassPayload: GjennomforingV2KafkaPayload.Enkeltplass = lagEnkeltplassDeltakerlistePayload(
            deltakerliste = deltakerliste,
            arrangor = arrangor,
        ),
    )

    fun Deltaker.toDto() = DeltakerKafkaPayload(
        id = this.id,
        personalia = Personalia(
            personId = UUID.randomUUID(),
            personident = this.personident,
            navn = Navn(fornavn = "Fornavn", mellomnavn = null, etternavn = "Etternavn"),
            kontaktinformasjon = Kontaktinformasjon("23123", "fjwoei@fjweio.no"),
            skjermet = false,
            adresse = null,
            adressebeskyttelse = null,
        ),
        deltakerlisteId = this.deltakerlisteId,
        status = DeltakerStatusDto(
            id = UUID.randomUUID(),
            type = this.status.type,
            aarsak = this.status.aarsak,
            aarsaksbeskrivelse = null,
            gyldigFra = this.status.gyldigFra ?: LocalDateTime.now(),
            opprettetDato = LocalDateTime.now(),
        ),
        dagerPerUke = this.dagerPerUke,
        prosentStilling = this.prosentStilling,
        oppstartsdato = this.oppstartsdato,
        sluttdato = this.sluttdato,
        deltarPaKurs = this.deltarPaKurs,
        kilde = this.kilde,
        deltakerliste = no.nav.amt.lib.models.deltaker.Deltakerliste(
            id = this.deltakerlisteId,
            navn = "Navn",
            tiltak = no.nav.amt.lib.models.deltakerliste.tiltakstype
                .Tiltak("test", Tiltakskode.OPPFOLGING),
            startdato = null,
            sluttdato = null,
            oppstartstype = null,
        ),
        innsoktDato = LocalDate.now(),
        forsteVedtakFattet = null,
        bestillingTekst = null,
        innhold = null,
        historikk = null,
        vurderingerFraArrangor = null,
        erManueltDeltMedArrangor = false,
        sisteEndring = null,
        navKontor = null,
        navVeileder = null,
        oppfolgingsperioder = emptyList(),
        sistEndret = null,
        sistEndretAv = null,
        sistEndretAvEnhet = null,
    )

    fun Arrangor.toDto() = ArrangorDto(
        id = this.id,
        organisasjonsnummer = this.organisasjonsnummer,
        navn = this.navn,
        overordnetArrangorId = this.overordnetArrangorId,
    )

    // fun Tiltak.toDto(): DeltakerlistePayload.Tiltakstype = DeltakerlistePayload.Tiltakstype(this.tiltakskode.name)
}
