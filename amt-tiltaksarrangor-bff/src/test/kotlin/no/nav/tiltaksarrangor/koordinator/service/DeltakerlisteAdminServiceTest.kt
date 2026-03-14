package no.nav.tiltaksarrangor.koordinator.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.tiltaksarrangor.IntegrationTest
import no.nav.tiltaksarrangor.client.amtarrangor.AmtArrangorClient
import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import no.nav.tiltaksarrangor.repositories.AnsattRepository
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.AnsattRolleDbo
import no.nav.tiltaksarrangor.repositories.model.ArrangorDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.model.KoordinatorDeltakerlisteDbo
import no.nav.tiltaksarrangor.service.MetricsService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID

class DeltakerlisteAdminServiceTest(
	private val ansattRepository: AnsattRepository,
	private val deltakerlisteRepository: DeltakerlisteRepository,
	private val arrangorRepository: ArrangorRepository,
	private val deltakerlisteAdminService: DeltakerlisteAdminService,
	@MockkBean private val amtArrangorClient: AmtArrangorClient,
	@Suppress("unused") @MockkBean(relaxed = true) private val metricsService: MetricsService,
) : IntegrationTest() {
	@AfterEach
	fun tearDown() {
		clearMocks(amtArrangorClient)
	}

	@Test
	fun `getAlleDeltakerlister - ansatt er ikke koordinator - returnerer unauthorized`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		arrangorRepository.insertOrUpdateArrangor(
			ArrangorDbo(
				id = arrangorId,
				navn = "Arrangør AS",
				organisasjonsnummer = "88888888",
				overordnetArrangorId = null,
			),
		)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER)),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(UUID.randomUUID())),
				veilederDeltakere = emptyList(),
			),
		)

		assertThrows<UnauthorizedException> {
			deltakerlisteAdminService.getAlleDeltakerlister(personIdent)
		}
	}

	@Test
	fun `getAlleDeltakerlister - ansatt er koordinator for flere arrangorer - returnerer deltakerlister`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		arrangorRepository.insertOrUpdateArrangor(
			ArrangorDbo(
				id = arrangorId,
				navn = "Arrangør AS",
				organisasjonsnummer = "88888888",
				overordnetArrangorId = null,
			),
		)
		val overordnetArrangorId = UUID.randomUUID()
		arrangorRepository.insertOrUpdateArrangor(
			ArrangorDbo(
				id = overordnetArrangorId,
				navn = "Overordnet arrangør AS",
				organisasjonsnummer = "777777777",
				overordnetArrangorId = null,
			),
		)
		val arrangorId2 = UUID.randomUUID()
		arrangorRepository.insertOrUpdateArrangor(
			ArrangorDbo(
				id = arrangorId2,
				navn = "Annen arrangør AS",
				organisasjonsnummer = "99999999",
				overordnetArrangorId = overordnetArrangorId,
			),
		)
		val deltakerliste1 =
			DeltakerlisteDbo(
				id = UUID.randomUUID(),
				navn = "Gjennomføring 1",
				gjennomforingstype = GjennomforingType.Gruppe,
				status = GjennomforingStatusType.GJENNOMFORES,
				arrangorId = arrangorId,
				tiltaksnavn = "Navn på tiltak",
				tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
				startDato = LocalDate.of(2023, 2, 1),
				sluttDato = null,
				erKurs = false,
				oppstartstype = Oppstartstype.LOPENDE,
				tilgjengeligForArrangorFraOgMedDato = null,
				pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
			)
		val deltakerliste2 =
			DeltakerlisteDbo(
				id = UUID.randomUUID(),
				navn = "Gjennomføring 2",
				gjennomforingstype = GjennomforingType.Gruppe,
				status = GjennomforingStatusType.GJENNOMFORES,
				arrangorId = arrangorId2,
				tiltaksnavn = "Annet tiltak",
				tiltakskode = Tiltakskode.OPPFOLGING,
				startDato = LocalDate.of(2023, 5, 1),
				sluttDato = LocalDate.of(2023, 6, 1),
				erKurs = false,
				oppstartstype = Oppstartstype.LOPENDE,
				tilgjengeligForArrangorFraOgMedDato = LocalDate.of(2023, 4, 1),
				pameldingstype = GjennomforingPameldingType.DIREKTE_VEDTAK,
			)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste1)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste2)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR), AnsattRolleDbo(arrangorId2, AnsattRolle.KOORDINATOR)),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste1.id)),
				veilederDeltakere = emptyList(),
			),
		)

		val adminDeltakerlister = deltakerlisteAdminService.getAlleDeltakerlister(personIdent)

		adminDeltakerlister.size shouldBe 2

		val adminDeltakerliste1 = adminDeltakerlister.find { it.id == deltakerliste1.id }
		adminDeltakerliste1?.navn shouldBe "Gjennomføring 1"
		adminDeltakerliste1?.tiltaksnavn shouldBe "Navn på tiltak"
		adminDeltakerliste1?.arrangorNavn shouldBe "Arrangør AS"
		adminDeltakerliste1?.arrangorOrgnummer shouldBe "88888888"
		adminDeltakerliste1?.arrangorParentNavn shouldBe "Arrangør AS"
		adminDeltakerliste1?.startDato shouldBe LocalDate.of(2023, 2, 1)
		adminDeltakerliste1?.sluttDato shouldBe null
		adminDeltakerliste1?.lagtTil shouldBe true

		val adminDeltakerliste2 = adminDeltakerlister.find { it.id == deltakerliste2.id }
		adminDeltakerliste2?.navn shouldBe "Gjennomføring 2"
		adminDeltakerliste2?.tiltaksnavn shouldBe "Annet tiltak"
		adminDeltakerliste2?.arrangorNavn shouldBe "Overordnet arrangør AS"
		adminDeltakerliste2?.arrangorOrgnummer shouldBe "99999999"
		adminDeltakerliste2?.arrangorParentNavn shouldBe "Annen arrangør AS"
		adminDeltakerliste2?.startDato shouldBe LocalDate.of(2023, 5, 1)
		adminDeltakerliste2?.sluttDato shouldBe LocalDate.of(2023, 6, 1)
		adminDeltakerliste2?.lagtTil shouldBe false
	}

	@Test
	fun `getAlleDeltakerlister - deltakerliste ikke tilgjengelig - returnerer tom liste`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		arrangorRepository.insertOrUpdateArrangor(
			ArrangorDbo(
				id = arrangorId,
				navn = "Arrangør AS",
				organisasjonsnummer = "88888888",
				overordnetArrangorId = null,
			),
		)
		val overordnetArrangorId = UUID.randomUUID()
		arrangorRepository.insertOrUpdateArrangor(
			ArrangorDbo(
				id = overordnetArrangorId,
				navn = "Overordnet arrangør AS",
				organisasjonsnummer = "777777777",
				overordnetArrangorId = null,
			),
		)
		val deltakerliste =
			DeltakerlisteDbo(
				id = UUID.randomUUID(),
				navn = "Gjennomføring 1",
				gjennomforingstype = GjennomforingType.Gruppe,
				status = GjennomforingStatusType.GJENNOMFORES,
				arrangorId = arrangorId,
				tiltaksnavn = "Navn på tiltak",
				tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
				startDato = LocalDate.now().plusMonths(1),
				sluttDato = null,
				erKurs = false,
				oppstartstype = Oppstartstype.LOPENDE,
				tilgjengeligForArrangorFraOgMedDato = null,
				pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
			)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR)),
				deltakerlister = emptyList(),
				veilederDeltakere = emptyList(),
			),
		)

		val adminDeltakerlister = deltakerlisteAdminService.getAlleDeltakerlister(personIdent)

		adminDeltakerlister.size shouldBe 0
	}

	@Test
	fun `leggTilDeltakerliste - ansatt er ikke koordinator hos arrangor - returnerer unauthorized`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR)),
				deltakerlister = emptyList(),
				veilederDeltakere = emptyList(),
			),
		)
		val deltakerliste =
			DeltakerlisteDbo(
				id = UUID.randomUUID(),
				navn = "Gjennomføring 1",
				gjennomforingstype = GjennomforingType.Gruppe,
				status = GjennomforingStatusType.GJENNOMFORES,
				arrangorId = UUID.randomUUID(),
				tiltaksnavn = "Navn på tiltak",
				tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
				startDato = LocalDate.of(2023, 2, 1),
				sluttDato = null,
				erKurs = false,
				oppstartstype = Oppstartstype.LOPENDE,
				tilgjengeligForArrangorFraOgMedDato = null,
				pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
			)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)

		assertThrows<UnauthorizedException> {
			deltakerlisteAdminService.leggTilDeltakerliste(deltakerliste.id, personIdent)
		}
	}

	@Test
	fun `leggTilDeltakerliste - ansatt har allerede lagt til deltakerliste - endrer ingenting`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste =
			DeltakerlisteDbo(
				id = UUID.randomUUID(),
				navn = "Gjennomføring 1",
				gjennomforingstype = GjennomforingType.Gruppe,
				status = GjennomforingStatusType.GJENNOMFORES,
				arrangorId = arrangorId,
				tiltaksnavn = "Navn på tiltak",
				tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
				startDato = LocalDate.of(2023, 2, 1),
				sluttDato = null,
				erKurs = false,
				oppstartstype = Oppstartstype.LOPENDE,
				tilgjengeligForArrangorFraOgMedDato = null,
				pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
			)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR)),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
				veilederDeltakere = emptyList(),
			),
		)

		deltakerlisteAdminService.leggTilDeltakerliste(deltakerliste.id, personIdent)

		coVerify(exactly = 0) { amtArrangorClient.leggTilDeltakerlisteForKoordinator(any(), any(), any()) }
	}

	@Test
	fun `leggTilDeltakerliste - ansatt har tilgang til deltakerliste - deltakerliste legges til`() {
		coEvery { amtArrangorClient.leggTilDeltakerlisteForKoordinator(any(), any(), any()) } just Runs

		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste =
			DeltakerlisteDbo(
				id = UUID.randomUUID(),
				navn = "Gjennomføring 1",
				gjennomforingstype = GjennomforingType.Gruppe,
				status = GjennomforingStatusType.GJENNOMFORES,
				arrangorId = arrangorId,
				tiltaksnavn = "Navn på tiltak",
				tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
				startDato = LocalDate.of(2023, 2, 1),
				sluttDato = null,
				erKurs = false,
				oppstartstype = Oppstartstype.LOPENDE,
				tilgjengeligForArrangorFraOgMedDato = null,
				pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
			)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val ansattId = UUID.randomUUID()
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = ansattId,
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR)),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(UUID.randomUUID())),
				veilederDeltakere = emptyList(),
			),
		)

		deltakerlisteAdminService.leggTilDeltakerliste(deltakerliste.id, personIdent)

		val ansattFraDb = ansattRepository.getAnsatt(ansattId)
		ansattFraDb?.deltakerlister?.size shouldBe 2
		ansattFraDb?.deltakerlister?.find { it.deltakerlisteId == deltakerliste.id } shouldNotBe null

		coVerify(exactly = 1) { amtArrangorClient.leggTilDeltakerlisteForKoordinator(ansattId, deltakerliste.id, arrangorId) }
	}

	@Test
	fun `leggTilDeltakerliste - deltakerliste ikke tilgjengelig - kaster NoSuchElementException`() {
		coEvery { amtArrangorClient.leggTilDeltakerlisteForKoordinator(any(), any(), any()) } just Runs

		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste =
			DeltakerlisteDbo(
				id = UUID.randomUUID(),
				navn = "Gjennomføring 1",
				gjennomforingstype = GjennomforingType.Gruppe,
				status = GjennomforingStatusType.GJENNOMFORES,
				arrangorId = arrangorId,
				tiltaksnavn = "Navn på tiltak",
				tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
				startDato = LocalDate.now().plusDays(10),
				sluttDato = null,
				erKurs = false,
				oppstartstype = Oppstartstype.LOPENDE,
				tilgjengeligForArrangorFraOgMedDato = LocalDate.now().plusDays(2),
				pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
			)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val ansattId = UUID.randomUUID()
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = ansattId,
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR)),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(UUID.randomUUID())),
				veilederDeltakere = emptyList(),
			),
		)

		assertThrows<NoSuchElementException> {
			deltakerlisteAdminService.leggTilDeltakerliste(deltakerliste.id, personIdent)
		}
	}

	@Test
	fun `fjernDeltakerliste - ansatt er ikke koordinator hos arrangor - returnerer unauthorized`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR)),
				deltakerlister = emptyList(),
				veilederDeltakere = emptyList(),
			),
		)
		val deltakerliste =
			DeltakerlisteDbo(
				id = UUID.randomUUID(),
				navn = "Gjennomføring 1",
				gjennomforingstype = GjennomforingType.Gruppe,
				status = GjennomforingStatusType.GJENNOMFORES,
				arrangorId = UUID.randomUUID(),
				tiltaksnavn = "Navn på tiltak",
				tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
				startDato = LocalDate.of(2023, 2, 1),
				sluttDato = null,
				erKurs = false,
				oppstartstype = Oppstartstype.LOPENDE,
				tilgjengeligForArrangorFraOgMedDato = null,
				pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
			)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)

		assertThrows<UnauthorizedException> {
			deltakerlisteAdminService.fjernDeltakerliste(deltakerliste.id, personIdent)
		}
	}

	@Test
	fun `fjernDeltakerliste - ansatt har ikke lagt til deltakerliste - endrer ignenting`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste =
			DeltakerlisteDbo(
				id = UUID.randomUUID(),
				navn = "Gjennomføring 1",
				gjennomforingstype = GjennomforingType.Gruppe,
				status = GjennomforingStatusType.GJENNOMFORES,
				arrangorId = arrangorId,
				tiltaksnavn = "Navn på tiltak",
				tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
				startDato = LocalDate.of(2023, 2, 1),
				sluttDato = null,
				erKurs = false,
				oppstartstype = Oppstartstype.LOPENDE,
				tilgjengeligForArrangorFraOgMedDato = null,
				pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
			)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR)),
				deltakerlister = emptyList(),
				veilederDeltakere = emptyList(),
			),
		)

		deltakerlisteAdminService.fjernDeltakerliste(deltakerliste.id, personIdent)

		coVerify(exactly = 0) { amtArrangorClient.fjernDeltakerlisteForKoordinator(any(), any(), any()) }
	}

	@Test
	fun `fjernDeltakerliste - ansatt har lagt til deltakerliste - deltakerliste fjernes`() {
		coEvery { amtArrangorClient.fjernDeltakerlisteForKoordinator(any(), any(), any()) } just Runs

		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste =
			DeltakerlisteDbo(
				id = UUID.randomUUID(),
				navn = "Gjennomføring 1",
				gjennomforingstype = GjennomforingType.Gruppe,
				status = GjennomforingStatusType.GJENNOMFORES,
				arrangorId = arrangorId,
				tiltaksnavn = "Navn på tiltak",
				tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
				startDato = LocalDate.of(2023, 2, 1),
				sluttDato = null,
				erKurs = false,
				oppstartstype = Oppstartstype.LOPENDE,
				tilgjengeligForArrangorFraOgMedDato = null,
				pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
			)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val ansattId = UUID.randomUUID()
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = ansattId,
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR)),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(UUID.randomUUID()), KoordinatorDeltakerlisteDbo(deltakerliste.id)),
				veilederDeltakere = emptyList(),
			),
		)

		deltakerlisteAdminService.fjernDeltakerliste(deltakerliste.id, personIdent)

		val ansattFraDb = ansattRepository.getAnsatt(ansattId)
		ansattFraDb?.deltakerlister?.size shouldBe 1
		ansattFraDb?.deltakerlister?.find { it.deltakerlisteId == deltakerliste.id } shouldBe null

		coVerify(exactly = 1) { amtArrangorClient.fjernDeltakerlisteForKoordinator(ansattId, deltakerliste.id, arrangorId) }
	}
}
