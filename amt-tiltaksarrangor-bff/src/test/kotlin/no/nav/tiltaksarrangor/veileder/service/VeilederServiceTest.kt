package no.nav.tiltaksarrangor.veileder.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.tiltaksarrangor.IntegrationTest
import no.nav.tiltaksarrangor.client.amtarrangor.AmtArrangorClient
import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.consumer.model.EndringsmeldingType
import no.nav.tiltaksarrangor.consumer.model.Innhold
import no.nav.tiltaksarrangor.melding.forslag.ForslagRepository
import no.nav.tiltaksarrangor.melding.forslag.forlengDeltakelseForslag
import no.nav.tiltaksarrangor.model.AktivEndring
import no.nav.tiltaksarrangor.model.Oppdatering
import no.nav.tiltaksarrangor.model.Veiledertype
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import no.nav.tiltaksarrangor.repositories.AnsattRepository
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.EndringsmeldingRepository
import no.nav.tiltaksarrangor.repositories.UlestEndringRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.AnsattRolleDbo
import no.nav.tiltaksarrangor.repositories.model.VeilederDeltakerDbo
import no.nav.tiltaksarrangor.testutils.DbTestDataUtils.shouldBeCloseTo
import no.nav.tiltaksarrangor.testutils.getDeltaker
import no.nav.tiltaksarrangor.testutils.getDeltakerliste
import no.nav.tiltaksarrangor.testutils.getEndringsmelding
import no.nav.tiltaksarrangor.testutils.getForslag
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class VeilederServiceTest(
	private val veilederService: VeilederService,
	private val ansattRepository: AnsattRepository,
	private val deltakerlisteRepository: DeltakerlisteRepository,
	private val deltakerRepository: DeltakerRepository,
	private val endringsmeldingRepository: EndringsmeldingRepository,
	private val forslagRepository: ForslagRepository,
	private val ulestEndringRepository: UlestEndringRepository,
	@MockkBean(relaxed = true) private val amtArrangorClient: AmtArrangorClient,
	@MockkBean(relaxed = true) private val unleashToggle: CommonUnleashToggle,
) : IntegrationTest() {
	@BeforeEach
	fun setup() {
		every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns false
	}

	@AfterEach
	fun tearDown() {
		clearMocks(amtArrangorClient, unleashToggle)
	}

	@Test
	fun `getMineDeltakere - ansatt har ikke veileder-rolle - returnerer unauthorized`() {
		val personIdent = "12345678910"
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Ansatt",
				mellomnavn = null,
				etternavn = "Ansattsen",
				roller = listOf(AnsattRolleDbo(UUID.randomUUID(), AnsattRolle.KOORDINATOR)),
				deltakerlister = emptyList(),
				veilederDeltakere = listOf(VeilederDeltakerDbo(UUID.randomUUID(), Veiledertype.VEILEDER)),
			),
		)

		assertThrows<UnauthorizedException> {
			veilederService.getMineDeltakere(personIdent)
		}
	}

	@Test
	fun `getMineDeltakere - ansatt er veileder for tre deltakere hos to arrangorer - returnerer riktige deltakere med endringsmeldinger`() {
		val personIdent = "12345678910"
		val ansattId = UUID.randomUUID()
		val arrangorId = UUID.randomUUID()
		val arrangorId2 = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		val deltakerliste2 = deltakerliste.copy(id = UUID.randomUUID(), arrangorId = arrangorId2, navn = "Deltakerliste 2")
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste2)
		val deltaker = getDeltaker(UUID.randomUUID(), deltakerliste.id).copy(personident = "12345")
		val deltaker2 = getDeltaker(
			UUID.randomUUID(),
			deltakerliste.id,
		).copy(personident = "23456", sistEndret = LocalDateTime.now().minusDays(2))
		val deltaker3 = getDeltaker(
			UUID.randomUUID(),
			deltakerliste2.id,
		).copy(personident = "34567", sistEndret = LocalDateTime.now().minusDays(1))
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		deltakerRepository.insertOrUpdateDeltaker(deltaker2)
		deltakerRepository.insertOrUpdateDeltaker(deltaker3)
		val endringsmelding = getEndringsmelding(deltaker.id)
		val endringsmelding2 =
			endringsmelding.copy(
				id = UUID.randomUUID(),
				type = EndringsmeldingType.ENDRE_OPPSTARTSDATO,
				innhold =
					Innhold.EndreOppstartsdatoInnhold(
						LocalDate.now().minusMonths(2),
					),
			)
		endringsmeldingRepository.insertOrUpdateEndringsmelding(endringsmelding)
		endringsmeldingRepository.insertOrUpdateEndringsmelding(endringsmelding2)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = ansattId,
				personIdent = personIdent,
				fornavn = "Ansatt",
				mellomnavn = null,
				etternavn = "Ansattsen",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER), AnsattRolleDbo(arrangorId2, AnsattRolle.VEILEDER)),
				deltakerlister = emptyList(),
				veilederDeltakere =
					listOf(
						VeilederDeltakerDbo(deltaker.id, Veiledertype.VEILEDER),
						VeilederDeltakerDbo(deltaker2.id, Veiledertype.MEDVEILEDER),
						VeilederDeltakerDbo(deltaker3.id, Veiledertype.VEILEDER),
					),
			),
		)
		val forslag = getForslag(deltaker3.id).copy(opprettetAvArrangorAnsattId = ansattId)
		forslagRepository.upsert(forslag)

		val mineDeltakere = veilederService.getMineDeltakere(personIdent)

		mineDeltakere.size shouldBe 3

		val minDeltaker1 = mineDeltakere.find { it.id == deltaker.id }
		minDeltaker1?.fodselsnummer shouldBe deltaker.personident
		minDeltaker1?.fornavn shouldNotBe ""
		minDeltaker1?.etternavn shouldNotBe ""
		minDeltaker1?.deltakerliste?.id shouldBe deltakerliste.id
		minDeltaker1?.veiledertype shouldBe Veiledertype.VEILEDER
		minDeltaker1?.aktiveEndringsmeldinger?.size shouldBe 2
		minDeltaker1?.adressebeskyttet shouldBe false
		minDeltaker1?.sistEndret?.shouldBeCloseTo(deltaker.sistEndret)
		minDeltaker1?.aktivEndring?.type shouldBe AktivEndring.Type.Endringsmelding
		minDeltaker1?.aktivEndring?.endingsType shouldBe AktivEndring.EndringsType.ForlengDeltakelse

		val minDeltaker2 = mineDeltakere.find { it.id == deltaker2.id }
		minDeltaker2?.fodselsnummer shouldBe deltaker2.personident
		minDeltaker2?.deltakerliste?.id shouldBe deltakerliste.id
		minDeltaker2?.veiledertype shouldBe Veiledertype.MEDVEILEDER
		minDeltaker2?.aktiveEndringsmeldinger?.size shouldBe 0
		minDeltaker2?.sistEndret?.shouldBeCloseTo(deltaker2.sistEndret)
		minDeltaker2?.aktivEndring shouldBe null

		val minDeltaker3 = mineDeltakere.find { it.id == deltaker3.id }
		minDeltaker3?.fodselsnummer shouldBe deltaker3.personident
		minDeltaker3?.deltakerliste?.id shouldBe deltakerliste2.id
		minDeltaker3?.veiledertype shouldBe Veiledertype.VEILEDER
		minDeltaker3?.aktiveEndringsmeldinger?.size shouldBe 0
		minDeltaker3?.sistEndret?.shouldBeCloseTo(deltaker3.sistEndret)
		minDeltaker3?.aktivEndring?.type shouldBe AktivEndring.Type.Forslag
		minDeltaker3?.aktivEndring?.endingsType shouldBe AktivEndring.EndringsType.ForlengDeltakelse
	}

	@Test
	fun `getMineDeltakere - ansatt er veileder for deltaker, komet er master - returnerer deltaker uten endringsmeldinger`() {
		every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true
		val personIdent = "12345678910"
		val ansattId = UUID.randomUUID()
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltaker = getDeltaker(UUID.randomUUID(), deltakerliste.id).copy(personident = "12345")
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		val endringsmelding = getEndringsmelding(deltaker.id)
		val endringsmelding2 =
			endringsmelding.copy(
				id = UUID.randomUUID(),
				type = EndringsmeldingType.ENDRE_OPPSTARTSDATO,
				innhold =
					Innhold.EndreOppstartsdatoInnhold(
						LocalDate.now().minusMonths(2),
					),
			)
		endringsmeldingRepository.insertOrUpdateEndringsmelding(endringsmelding)
		endringsmeldingRepository.insertOrUpdateEndringsmelding(endringsmelding2)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = ansattId,
				personIdent = personIdent,
				fornavn = "Ansatt",
				mellomnavn = null,
				etternavn = "Ansattsen",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER)),
				deltakerlister = emptyList(),
				veilederDeltakere =
					listOf(
						VeilederDeltakerDbo(deltaker.id, Veiledertype.VEILEDER),
					),
			),
		)

		val mineDeltakere = veilederService.getMineDeltakere(personIdent)

		mineDeltakere.size shouldBe 1

		val minDeltaker1 = mineDeltakere.find { it.id == deltaker.id }
		minDeltaker1?.fodselsnummer shouldBe deltaker.personident
		minDeltaker1?.fornavn shouldNotBe ""
		minDeltaker1?.etternavn shouldNotBe ""
		minDeltaker1?.deltakerliste?.id shouldBe deltakerliste.id
		minDeltaker1?.veiledertype shouldBe Veiledertype.VEILEDER
		minDeltaker1?.aktiveEndringsmeldinger?.size shouldBe 0
		minDeltaker1?.adressebeskyttet shouldBe false
		minDeltaker1?.sistEndret?.shouldBeCloseTo(deltaker.sistEndret)
		minDeltaker1?.aktivEndring shouldBe null
	}

	@Test
	fun `getMineDeltakere - ansatt har veilederrolle hos en arrangor og er veileder for deltakere - returnerer riktige deltakere`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val arrangorId2 = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		val deltakerliste2 = deltakerliste.copy(id = UUID.randomUUID(), arrangorId = arrangorId2, navn = "Deltakerliste 2")
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste2)
		val deltaker = getDeltaker(UUID.randomUUID(), deltakerliste.id).copy(personident = "12345")
		val deltaker2 = getDeltaker(UUID.randomUUID(), deltakerliste.id).copy(personident = "23456")
		val deltaker3 = getDeltaker(UUID.randomUUID(), deltakerliste2.id).copy(personident = "34567")
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		deltakerRepository.insertOrUpdateDeltaker(deltaker2)
		deltakerRepository.insertOrUpdateDeltaker(deltaker3)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Ansatt",
				mellomnavn = null,
				etternavn = "Ansattsen",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER)),
				deltakerlister = emptyList(),
				veilederDeltakere =
					listOf(
						VeilederDeltakerDbo(deltaker.id, Veiledertype.VEILEDER),
						VeilederDeltakerDbo(deltaker2.id, Veiledertype.MEDVEILEDER),
						VeilederDeltakerDbo(deltaker3.id, Veiledertype.VEILEDER),
					),
			),
		)

		val mineDeltakere = veilederService.getMineDeltakere(personIdent)

		mineDeltakere.size shouldBe 2
		mineDeltakere.find { it.id == deltaker.id } shouldNotBe null
		mineDeltakere.find { it.id == deltaker2.id } shouldNotBe null
		mineDeltakere.find { it.id == deltaker3.id } shouldBe null
	}

	@Test
	fun `getMineDeltakere - ansatt er veileder for adressebeskyttet deltaker - returnerer deltaker med tomme felter`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltaker = getDeltaker(UUID.randomUUID(), deltakerliste.id, adressebeskyttet = true).copy(personident = "12345")
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Ansatt",
				mellomnavn = null,
				etternavn = "Ansattsen",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER)),
				deltakerlister = emptyList(),
				veilederDeltakere =
					listOf(
						VeilederDeltakerDbo(deltaker.id, Veiledertype.VEILEDER),
					),
			),
		)

		val mineDeltakere = veilederService.getMineDeltakere(personIdent)

		mineDeltakere.size shouldBe 1
		val minDeltaker = mineDeltakere.find { it.id == deltaker.id }
		minDeltaker?.fodselsnummer shouldBe ""
		minDeltaker?.fornavn shouldBe ""
		minDeltaker?.etternavn shouldBe ""
		minDeltaker?.deltakerliste?.id shouldBe deltakerliste.id
		minDeltaker?.veiledertype shouldBe Veiledertype.VEILEDER
	}

	@Test
	fun `getMineDeltakere - ansatt er veileder for deltakere som er skjult - filtrerer bort skjulte deltakere`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltaker = getDeltaker(UUID.randomUUID(), deltakerliste.id).copy(personident = "12345")
		val deltaker2 = deltaker.copy(id = UUID.randomUUID(), personident = "23456", skjultDato = LocalDateTime.now())
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		deltakerRepository.insertOrUpdateDeltaker(deltaker2)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Ansatt",
				mellomnavn = null,
				etternavn = "Ansattsen",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER)),
				deltakerlister = emptyList(),
				veilederDeltakere =
					listOf(
						VeilederDeltakerDbo(deltaker.id, Veiledertype.VEILEDER),
						VeilederDeltakerDbo(deltaker2.id, Veiledertype.MEDVEILEDER),
					),
			),
		)

		val mineDeltakere = veilederService.getMineDeltakere(personIdent)

		mineDeltakere.size shouldBe 1
		mineDeltakere.find { it.id == deltaker.id } shouldNotBe null
		mineDeltakere.find { it.id == deltaker2.id } shouldBe null
	}

	@Test
	fun `getMineDeltakere - ansatt er veileder for deltakere med sluttdato mer enn 41 dager frem i tid - filtrerer bort skjulte deltakere`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltaker = getDeltaker(UUID.randomUUID(), deltakerliste.id).copy(personident = "12345")
		val deltaker2 = deltaker.copy(id = UUID.randomUUID(), personident = "23456", sluttdato = LocalDate.now().minusDays(41))
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		deltakerRepository.insertOrUpdateDeltaker(deltaker2.copy())
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Ansatt",
				mellomnavn = null,
				etternavn = "Ansattsen",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER)),
				deltakerlister = emptyList(),
				veilederDeltakere =
					listOf(
						VeilederDeltakerDbo(deltaker.id, Veiledertype.VEILEDER),
						VeilederDeltakerDbo(deltaker2.id, Veiledertype.MEDVEILEDER),
					),
			),
		)

		val mineDeltakere = veilederService.getMineDeltakere(personIdent)

		mineDeltakere.size shouldBe 1
		mineDeltakere.find { it.id == deltaker.id } shouldNotBe null
		mineDeltakere.find { it.id == deltaker2.id } shouldBe null
	}

	@Test
	fun `getMineDeltakere - ansatt har veilederrolle hos en arrangor, deltakerliste ikke tilgjengelig - filtrerer bort deltakere`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		val deltakerliste2 = deltakerliste.copy(id = UUID.randomUUID(), startDato = null, navn = "Deltakerliste 2")
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste2)
		val deltaker = getDeltaker(UUID.randomUUID(), deltakerliste.id).copy(personident = "12345")
		val deltaker2 = getDeltaker(UUID.randomUUID(), deltakerliste2.id).copy(personident = "34567")
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		deltakerRepository.insertOrUpdateDeltaker(deltaker2)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Ansatt",
				mellomnavn = null,
				etternavn = "Ansattsen",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER)),
				deltakerlister = emptyList(),
				veilederDeltakere =
					listOf(
						VeilederDeltakerDbo(deltaker.id, Veiledertype.VEILEDER),
						VeilederDeltakerDbo(deltaker2.id, Veiledertype.VEILEDER),
					),
			),
		)

		val mineDeltakere = veilederService.getMineDeltakere(personIdent)

		mineDeltakere.size shouldBe 1
		mineDeltakere.find { it.id == deltaker.id } shouldNotBe null
		mineDeltakere.find { it.id == deltaker2.id } shouldBe null
	}

	@Test
	fun `getMineDeltakere - ansatt er veileder for deltakere med forslag med svar - returnerer deltakere med svar fra Nav`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltaker = getDeltaker(UUID.randomUUID(), deltakerliste.id).copy(personident = "12345")
		val deltaker2 = getDeltaker(UUID.randomUUID(), deltakerliste.id).copy(personident = "12345")
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		deltakerRepository.insertOrUpdateDeltaker(deltaker2)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Ansatt",
				mellomnavn = null,
				etternavn = "Ansattsen",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER)),
				deltakerlister = emptyList(),
				veilederDeltakere =
					listOf(
						VeilederDeltakerDbo(deltaker.id, Veiledertype.VEILEDER),
						VeilederDeltakerDbo(deltaker2.id, Veiledertype.VEILEDER),
					),
			),
		)

		ulestEndringRepository.insert(
			deltaker.id,
			Oppdatering.AvvistForslag(
				forlengDeltakelseForslag(
					status = Forslag.Status.Avvist(
						Forslag.NavAnsatt(
							UUID.randomUUID(),
							UUID.randomUUID(),
						),
						LocalDateTime.now(),
						"fordi...",
					),
				),
			),
		)

		ulestEndringRepository.insert(
			deltaker2.id,
			Oppdatering.DeltakelsesEndring(
				endring = DeltakerEndring(
					forslag = forlengDeltakelseForslag(
						status = Forslag.Status.Godkjent(
							Forslag.NavAnsatt(
								UUID.randomUUID(),
								UUID.randomUUID(),
							),
							LocalDateTime.now(),
						),
					),
					id = UUID.randomUUID(),
					deltakerId = deltaker2.id,
					endring = DeltakerEndring.Endring.EndreSluttdato(
						LocalDate.now().plusDays(1),
						"fordi",
					),
					endretAv = arrangorId,
					endretAvEnhet = UUID.randomUUID(),
					endret = LocalDateTime.now(),
				),
			),
		)

		val mineDeltakere = veilederService.getMineDeltakere(personIdent)

		mineDeltakere.size shouldBe 2
		val minDeltaker1 = mineDeltakere.find { it.id == deltaker.id }
		minDeltaker1 shouldNotBe null
		minDeltaker1?.svarFraNav shouldBe true
		val minDeltaker2 = mineDeltakere.find { it.id == deltaker2.id }
		minDeltaker2 shouldNotBe null
		minDeltaker2?.svarFraNav shouldBe true
	}
}
