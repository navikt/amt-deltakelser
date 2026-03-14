package no.nav.tiltaksarrangor.consumer

import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerStatusDto
import no.nav.amt.lib.models.deltaker.Deltakerliste
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.Kontaktinformasjon
import no.nav.amt.lib.models.deltaker.Navn
import no.nav.amt.lib.models.deltaker.Personalia
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltak
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.tiltaksarrangor.client.amtarrangor.AmtArrangorClient
import no.nav.tiltaksarrangor.client.amtarrangor.dto.ArrangorMedOverordnetArrangor
import no.nav.tiltaksarrangor.client.amtperson.AmtPersonClient
import no.nav.tiltaksarrangor.client.amtperson.NavEnhetDto
import no.nav.tiltaksarrangor.consumer.model.EndringsmeldingDto
import no.nav.tiltaksarrangor.consumer.model.EndringsmeldingType
import no.nav.tiltaksarrangor.consumer.model.Innhold
import no.nav.tiltaksarrangor.consumer.model.NavEnhet
import no.nav.tiltaksarrangor.consumer.model.toDeltakerDbo
import no.nav.tiltaksarrangor.melding.forslag.ForslagService
import no.nav.tiltaksarrangor.melding.forslag.forlengDeltakelseForslag
import no.nav.tiltaksarrangor.model.Endringsmelding
import no.nav.tiltaksarrangor.model.Oppdatering
import no.nav.tiltaksarrangor.repositories.AnsattRepository
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.EndringsmeldingRepository
import no.nav.tiltaksarrangor.repositories.NavAnsattRepository
import no.nav.tiltaksarrangor.repositories.UlestEndringRepository
import no.nav.tiltaksarrangor.service.NavAnsattService
import no.nav.tiltaksarrangor.service.NavEnhetService
import no.nav.tiltaksarrangor.testutils.getAdresse
import no.nav.tiltaksarrangor.testutils.getDeltaker
import no.nav.tiltaksarrangor.testutils.getDeltakerliste
import no.nav.tiltaksarrangor.testutils.getNavAnsatt
import no.nav.tiltaksarrangor.testutils.getVurderinger
import no.nav.tiltaksarrangor.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class KafkaConsumerServiceTest {
	private val arrangorRepository = mockk<ArrangorRepository>()
	private val ansattRepository = mockk<AnsattRepository>()
	private val navAnsattRepository = mockk<NavAnsattRepository>()
	private val deltakerlisteRepository = mockk<DeltakerlisteRepository>()
	private val deltakerRepository = mockk<DeltakerRepository>()
	private val endringsmeldingRepository = mockk<EndringsmeldingRepository>()
	private val amtArrangorClient = mockk<AmtArrangorClient>()
	private val navAnsattService = mockk<NavAnsattService>(relaxUnitFun = true)
	private val navEnhetService = mockk<NavEnhetService>(relaxUnitFun = true)
	private val forslagService = mockk<ForslagService>(relaxUnitFun = true)
	private val ulestEndringRepository = mockk<UlestEndringRepository>()
	private val amtPersonClient = mockk<AmtPersonClient>()
	private val kafkaConsumerService =
		KafkaConsumerService(
			arrangorRepository,
			ansattRepository,
			deltakerRepository,
			endringsmeldingRepository,
			forslagService,
			navEnhetService,
			navAnsattService,
			ulestEndringRepository,
			amtPersonClient,
			navAnsattRepository,
		)

	private val arrangor =
		ArrangorMedOverordnetArrangor(
			id = UUID.randomUUID(),
			navn = "Arrangør AS",
			organisasjonsnummer = "88888888",
			overordnetArrangor = null,
		)

	@BeforeEach
	fun resetMocks() {
		clearAllMocks()

		every { deltakerlisteRepository.insertOrUpdateDeltakerliste(any()) } just Runs
		every { deltakerlisteRepository.getDeltakerliste(any()) } returns getDeltakerliste(arrangor.id)
		every { deltakerlisteRepository.deleteDeltakerlisteOgDeltakere(any()) } returns 1
		every { deltakerRepository.insertOrUpdateDeltaker(any()) } just Runs
		every { deltakerRepository.deleteDeltaker(any()) } returns 1
		every { endringsmeldingRepository.insertOrUpdateEndringsmelding(any()) } just Runs
		every { endringsmeldingRepository.deleteEndringsmelding(any()) } returns 1
		every { arrangorRepository.getArrangor("88888888") } returns null
		every { arrangorRepository.insertOrUpdateArrangor(any()) } just Runs
		every { ulestEndringRepository.insert(any(), any()) } returns mockk()
		coEvery { amtArrangorClient.getArrangor("88888888") } returns arrangor
		coEvery { amtPersonClient.hentOppdatertKontaktinfo(any<String>()) } returns
			Result.failure(RuntimeException("Oppdatert kontaktinformasjon ikke nødvendig for test"))
	}

	@Test
	fun `lagreDeltaker - feil gjennomforingstype - skal ikke lagre`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			medStatus(DeltakerStatus.Type.DELTAR)

			val deltakerInTest =
				deltakerDto.copy(deltakerliste = deltakerDto.deltakerliste.copy(gjennomforingstype = GjennomforingType.Enkeltplass))

			kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerInTest))

			verify(exactly = 0) { deltakerRepository.insertOrUpdateDeltaker(any()) }
		}
	}

	@Test
	fun `lagreDeltaker - status DELTAR - lagres i db`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			medStatus(DeltakerStatus.Type.DELTAR)
			every { deltakerRepository.getDeltaker(any()) } returns null
			kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerDto))

			verify(exactly = 1) { deltakerRepository.insertOrUpdateDeltaker(any()) }
		}
	}

	@Test
	fun `lagreDeltaker - enkeltplass type - lagres ikke i db`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			medStatus(DeltakerStatus.Type.DELTAR)
			medDeltakerlisteType(Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING)
			every { deltakerRepository.getDeltaker(any()) } returns null
			kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerDto))

			verify(exactly = 0) { deltakerRepository.insertOrUpdateDeltaker(any()) }
		}
	}

	@Test
	fun `lagreDeltaker - ny deltaker - henter kontaktinfo`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			medStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
			every { deltakerRepository.getDeltaker(any()) } returns null
			kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerDto))

			verify(exactly = 1) { amtPersonClient.hentOppdatertKontaktinfo(any<String>()) }
			verify(exactly = 1) { deltakerRepository.insertOrUpdateDeltaker(any()) }
		}
	}

	@Test
	fun `lagreDeltaker - status FEILREGISTRERT - lagres ikke i db`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			medStatus(DeltakerStatus.Type.FEILREGISTRERT)
			every { deltakerRepository.getDeltaker(any()) } returns null
			kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerDto))

			verify(exactly = 0) { deltakerRepository.insertOrUpdateDeltaker(any()) }
			verify(exactly = 1) { deltakerRepository.deleteDeltaker(deltakerDto.id) }
		}
	}

	@Test
	fun `lagreDeltaker - status SOKT_INN - lagres hvis er delt med arrangor`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			medErManueltDeltMedArrangor()
			medStatus(DeltakerStatus.Type.SOKT_INN)
			every { deltakerRepository.getDeltaker(any()) } returns null
			kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerDto))

			verify(exactly = 1) { deltakerRepository.insertOrUpdateDeltaker(any()) }
		}
	}

	@Test
	fun `lagreDeltaker - status SOKT_INN - lagres ikke hvis ikke er delt med arrangor`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			medStatus(DeltakerStatus.Type.SOKT_INN)
			every { deltakerRepository.getDeltaker(any()) } returns null
			kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerDto))

			verify(exactly = 0) { deltakerRepository.insertOrUpdateDeltaker(any()) }
			verify(exactly = 1) { deltakerRepository.deleteDeltaker(deltakerDto.id) }
		}
	}

	@Test
	fun `lagreDeltaker - status HAR_SLUTTET for mer enn 40 dager siden - lagres ikke i db`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			medStatus(DeltakerStatus.Type.HAR_SLUTTET, 41)
			every { deltakerRepository.getDeltaker(any()) } returns null
			kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerDto))
			verify(exactly = 0) { deltakerRepository.insertOrUpdateDeltaker(any()) }
			verify(exactly = 1) { deltakerRepository.deleteDeltaker(deltakerDto.id) }
		}
	}

	@Test
	fun `lagreDeltaker - status HAR_SLUTTET for mindre enn 40 dager siden - lagres i db`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			medStatus(DeltakerStatus.Type.HAR_SLUTTET, 39)
			every { deltakerRepository.getDeltaker(any()) } returns null
			kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerDto))

			verify(exactly = 1) { deltakerRepository.insertOrUpdateDeltaker(any()) }
		}
	}

	@Test
	fun `lagreDeltaker - status IKKE_AKTUELL og deltar pa kurs og finnes ikke i db fra for - lagres ikke i db`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			every { deltakerRepository.getDeltaker(any()) } returns null
			medStatus(DeltakerStatus.Type.IKKE_AKTUELL)
			medDeltarPaKurs()
			kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerDto))

			verify(exactly = 0) { deltakerRepository.insertOrUpdateDeltaker(any()) }
			verify(exactly = 1) { deltakerRepository.deleteDeltaker(deltakerDto.id) }
		}
	}

	@Test
	fun `lagreDeltaker - status IKKE_AKTUELL og deltar pa kurs og finnes i db fra for - lagres i db`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			medStatus(DeltakerStatus.Type.IKKE_AKTUELL)
			medDeltarPaKurs()
			every { deltakerRepository.getDeltaker(any()) } returns getDeltaker(deltakerDto.id)
			kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerDto))

			verify(exactly = 1) { deltakerRepository.insertOrUpdateDeltaker(any()) }
			verify(exactly = 0) { deltakerRepository.deleteDeltaker(deltakerDto.id) }
		}
	}

	@Test
	fun `lagreDeltaker - status IKKE_AKTUELL for mer enn 40 dager siden, deltar pa kurs, finnes i db - lagres ikke i db`(): Unit =
		runBlocking {
			with(DeltakerDtoCtx()) {
				medStatus(DeltakerStatus.Type.IKKE_AKTUELL, 42)
				medDeltarPaKurs()
				every { deltakerRepository.getDeltaker(any()) } returns getDeltaker(deltakerDto.id)

				kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerDto))

				verify(exactly = 0) { deltakerRepository.insertOrUpdateDeltaker(any()) }
				verify(exactly = 1) { deltakerRepository.deleteDeltaker(deltakerDto.id) }
			}
		}

	@Test
	fun `lagreDeltaker - status HAR_SLUTTET mindre enn 40 dager siden, sluttdato mer enn 40 dager - lagres ikke i db`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			medStatus(DeltakerStatus.Type.HAR_SLUTTET, gyldigFraDagerSiden = 39)
			medSluttdato(dagerSiden = 41)
			every { deltakerRepository.getDeltaker(any()) } returns null
			kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerDto))

			verify(exactly = 0) { deltakerRepository.insertOrUpdateDeltaker(any()) }
			verify(exactly = 1) { deltakerRepository.deleteDeltaker(deltakerDto.id) }
		}
	}

	@Test
	fun `lagreDeltaker - har adressebeskyttelse - lagres i db`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			medAdressebeskyttelse()
			every { deltakerRepository.getDeltaker(any()) } returns null
			kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerDto))

			verify(exactly = 1) { deltakerRepository.insertOrUpdateDeltaker(any()) }
		}
	}

	@Test
	fun `lagreDeltaker - skjult, ny status DELTAR - fjerner skjuling i db`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			medStatus(DeltakerStatus.Type.DELTAR)

			val opprinneligDeltaker =
				getDeltaker(deltakerDto.id).copy(
					status = DeltakerStatus.Type.HAR_SLUTTET,
					skjultDato = LocalDateTime.now(),
					skjultAvAnsattId = UUID.randomUUID(),
				)
			every { deltakerRepository.getDeltaker(any()) } returns opprinneligDeltaker

			kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerDto))

			verify(exactly = 1) { deltakerRepository.insertOrUpdateDeltaker(match { it.skjultDato == null && it.skjultAvAnsattId == null }) }
		}
	}

	@Test
	fun `lagreDeltaker - skjult, samme status - beholder skjuling i db`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			medStatus(DeltakerStatus.Type.HAR_SLUTTET)

			val skjultDato = LocalDateTime.now().minusDays(2)
			val skjultAvAnsattId = UUID.randomUUID()
			val opprinneligDeltaker =
				getDeltaker(deltakerDto.id).copy(
					status = DeltakerStatus.Type.HAR_SLUTTET,
					skjultDato = skjultDato,
					skjultAvAnsattId = skjultAvAnsattId,
				)
			every { deltakerRepository.getDeltaker(any()) } returns opprinneligDeltaker

			kafkaConsumerService.lagreDeltaker(deltakerDto.id, objectMapper.writeValueAsString(deltakerDto))

			verify(exactly = 1) {
				deltakerRepository.insertOrUpdateDeltaker(
					match {
						it.skjultDato?.toLocalDate() == skjultDato.toLocalDate() && it.skjultAvAnsattId == skjultAvAnsattId
					},
				)
			}
		}
	}

	@Test
	fun `lagreDeltaker - historikk inneholder svar pa forslag som ikke finnes i db - lagrer ulest endring i db`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			val lagretDeltaker = deltakerDto.toDeltakerDbo()
			val forslag = forlengDeltakelseForslag(
				status = Forslag.Status.Avvist(
					Forslag.NavAnsatt(
						UUID.randomUUID(),
						UUID.randomUUID(),
					),
					LocalDateTime.now(),
					"Fordi...",
				),
			)
			val nyDeltaker = deltakerDto.copy(
				historikk = listOf(DeltakerHistorikk.Forslag(forslag)),
			)
			every { deltakerRepository.getDeltaker(any()) } returns lagretDeltaker
			every { navEnhetService.hentOpprettEllerOppdaterNavEnhet(any()) } returns mockk()
			every { navAnsattService.hentEllerOpprettNavAnsatt(any()) } returns mockk()
			kafkaConsumerService.lagreDeltaker(nyDeltaker.id, objectMapper.writeValueAsString(nyDeltaker))

			verify(exactly = 1) { ulestEndringRepository.insert(any(), any()) }
		}
	}

	@Test
	fun `lagreDeltaker - historikk inneholder endring fra arrangor - lagrer ikke i db`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			val lagretDeltaker = deltakerDto.toDeltakerDbo()
			val endringFraArrangor = DeltakerHistorikk.EndringFraArrangor(
				EndringFraArrangor(
					id = UUID.randomUUID(),
					deltakerId = lagretDeltaker.id,
					opprettetAvArrangorAnsattId = UUID.randomUUID(),
					opprettet = LocalDate.of(2023, 1, 1).atStartOfDay(),
					endring = EndringFraArrangor.LeggTilOppstartsdato(
						startdato = LocalDate.of(2023, 2, 1),
						sluttdato = null,
					),
				),
			)
			val nyDeltaker = deltakerDto.copy(
				historikk = listOf(endringFraArrangor),
			)
			every { deltakerRepository.getDeltaker(any()) } returns lagretDeltaker
			every { navEnhetService.hentOpprettEllerOppdaterNavEnhet(any()) } returns mockk()
			every { navAnsattService.hentEllerOpprettNavAnsatt(any()) } returns mockk()
			kafkaConsumerService.lagreDeltaker(nyDeltaker.id, objectMapper.writeValueAsString(nyDeltaker))

			verify(exactly = 0) { ulestEndringRepository.insert(any(), any()) }
		}
	}

	@Test
	fun `lagreDeltaker - historikk inneholder svar pa forslag som  finnes i db - lagrer ikke endring i db`(): Unit = runBlocking {
		val forslag = DeltakerHistorikk.Forslag(
			forlengDeltakelseForslag(
				status = Forslag.Status.Godkjent(
					Forslag.NavAnsatt(
						UUID.randomUUID(),
						UUID.randomUUID(),
					),
					LocalDateTime.now(),
				),
			),
		)

		with(DeltakerDtoCtx()) {
			val lagretDeltaker = deltakerDto
				.toDeltakerDbo()
				.copy(historikk = listOf(forslag))

			val nyDeltaker = deltakerDto.copy(
				historikk = listOf(forslag),
			)
			every { deltakerRepository.getDeltaker(any()) } returns lagretDeltaker
			every { navEnhetService.hentOpprettEllerOppdaterNavEnhet(any()) } returns mockk()
			every { navAnsattService.hentEllerOpprettNavAnsatt(any()) } returns mockk()
			kafkaConsumerService.lagreDeltaker(nyDeltaker.id, objectMapper.writeValueAsString(nyDeltaker))

			verify(exactly = 0) { ulestEndringRepository.insert(any(), any()) }
		}
	}

	@Test
	fun `lagreDeltaker - deltaker har ny Nav-veileder og nytt kontor - lagrer i db`(): Unit = runBlocking {
		with(DeltakerDtoCtx()) {
			val lagretDeltaker = getDeltaker(deltakerDto.id).copy(
				personident = "10987654321",
				fornavn = "Fornavn",
				etternavn = "Etternavn",
				telefonnummer = "98989898",
				epost = null,
				adresse = null,
			)

			val nyDeltaker = deltakerDto.copy(
				navVeileder = NavAnsatt(
					id = UUID.randomUUID(),
					navIdent = "X999999",
					navn = "Ny Veilederesen",
					epost = lagretDeltaker.navVeilederEpost,
					telefon = lagretDeltaker.navVeilederTelefon,
					navEnhetId = null,
				),
				navKontor = "nytt kontor",
			)

			every { deltakerRepository.getDeltaker(any()) } returns lagretDeltaker
			every { navEnhetService.hentOpprettEllerOppdaterNavEnhet(any()) } returns mockk()
			every { navAnsattService.hentEllerOpprettNavAnsatt(any()) } returns mockk()
			kafkaConsumerService.lagreDeltaker(nyDeltaker.id, objectMapper.writeValueAsString(nyDeltaker))

			verify(exactly = 1) { ulestEndringRepository.insert(any(), any()) }
		}
	}

	@Test
	fun `lagreEndringsmelding - status AKTIV - lagres i db`() {
		val endringsmeldingId = UUID.randomUUID()
		val endringsmeldingDto =
			EndringsmeldingDto(
				id = endringsmeldingId,
				deltakerId = UUID.randomUUID(),
				utfortAvNavAnsattId = null,
				opprettetAvArrangorAnsattId = UUID.randomUUID(),
				utfortTidspunkt = null,
				status = Endringsmelding.Status.AKTIV,
				type = EndringsmeldingType.ENDRE_SLUTTDATO,
				innhold = Innhold.EndreSluttdatoInnhold(sluttdato = LocalDate.now().plusWeeks(3)),
				createdAt = LocalDateTime.now(),
			)

		kafkaConsumerService.lagreEndringsmelding(endringsmeldingId, endringsmeldingDto)

		verify(exactly = 1) { endringsmeldingRepository.insertOrUpdateEndringsmelding(any()) }
	}

	@Test
	fun `lagreEndringsmelding - status UTDATERT - lagres i db`() {
		val endringsmeldingId = UUID.randomUUID()
		val endringsmeldingDto =
			EndringsmeldingDto(
				id = endringsmeldingId,
				deltakerId = UUID.randomUUID(),
				utfortAvNavAnsattId = null,
				opprettetAvArrangorAnsattId = UUID.randomUUID(),
				utfortTidspunkt = null,
				status = Endringsmelding.Status.UTDATERT,
				type = EndringsmeldingType.ENDRE_SLUTTDATO,
				innhold = Innhold.EndreSluttdatoInnhold(sluttdato = LocalDate.now().plusWeeks(3)),
				createdAt = LocalDateTime.now(),
			)

		kafkaConsumerService.lagreEndringsmelding(endringsmeldingId, endringsmeldingDto)

		verify(exactly = 1) { endringsmeldingRepository.insertOrUpdateEndringsmelding(any()) }
	}

	@Test
	fun `lagreNavAnsatt - ny ansatt - lagres`() {
		val navAnsatt = getNavAnsatt()

		every { navAnsattRepository.upsert(any()) } just Runs

		kafkaConsumerService.lagreNavAnsatt(navAnsatt)

		verify(exactly = 1) { navAnsattRepository.upsert(navAnsatt) }
	}

	@Test
	fun `handleForslag - forslaget er aktivt - gjor ingenting`() {
		val forslag = forlengDeltakelseForslag()

		kafkaConsumerService.handleMelding(forslag.id, forslag)

		verify(exactly = 0) { forslagService.delete(forslag.id) }
	}

	@Test
	fun `handleForslag - forslaget er tilbakekalt - gjor ingenting`() {
		val forslag = forlengDeltakelseForslag(
			status = Forslag.Status.Tilbakekalt(
				UUID.randomUUID(),
				LocalDateTime.now(),
			),
		)

		kafkaConsumerService.handleMelding(forslag.id, forslag)

		verify(exactly = 0) { forslagService.delete(forslag.id) }
	}

	@Test
	fun `handleForslag - forslaget er godkjent - sletter`() {
		val forslag = forlengDeltakelseForslag(
			status = Forslag.Status.Godkjent(
				Forslag.NavAnsatt(
					UUID.randomUUID(),
					UUID.randomUUID(),
				),
				LocalDateTime.now(),
			),
		)

		kafkaConsumerService.handleMelding(forslag.id, forslag)

		verify(exactly = 1) { forslagService.delete(forslag.id) }
	}

	@Test
	fun `handleForslag - forslaget er avvist - sletter`() {
		val forslag = forlengDeltakelseForslag(
			status = Forslag.Status.Avvist(
				Forslag.NavAnsatt(
					UUID.randomUUID(),
					UUID.randomUUID(),
				),
				LocalDateTime.now(),
				"Fordi...",
			),
		)

		kafkaConsumerService.handleMelding(forslag.id, forslag)

		verify(exactly = 1) { forslagService.delete(forslag.id) }
	}

	@Test
	fun `lagreNavEnhet - enhet har nytt navn - oppdaterer deltakere og lagrer`() {
		val id = UUID.randomUUID()
		val gammeltNavn = "NAV Bærum"
		val nyttNavn = "NAV Asker"
		val opprinneligEnhet = NavEnhet(
			id = id,
			enhetsnummer = "1234",
			navn = gammeltNavn,
		)
		val nyEnhet = NavEnhetDto(
			id = id,
			enhetId = "1234",
			navn = nyttNavn,
		)

		every { navEnhetService.hentEnhet(id) } returns opprinneligEnhet
		every { deltakerRepository.oppdaterEnhetsnavnForDeltakere(gammeltNavn, nyttNavn) } just Runs

		kafkaConsumerService.lagreNavEnhet(id, nyEnhet)

		verify(exactly = 1) { navEnhetService.upsert(match { it.navn == nyttNavn }) }
		verify(exactly = 1) { deltakerRepository.oppdaterEnhetsnavnForDeltakere(gammeltNavn, nyttNavn) }
	}

	@Nested
	inner class LagreNyDeltakerUlestEndringTests {
		val deltakerId: UUID = UUID.randomUUID()
		val endretAv: UUID = UUID.randomUUID()
		val endretAvEnhet: UUID = UUID.randomUUID()
		val endretDato: LocalDateTime = LocalDateTime.now()

		val endringInTest = EndringFraTiltakskoordinator(
			id = UUID.randomUUID(),
			deltakerId = deltakerId,
			endring = EndringFraTiltakskoordinator.DelMedArrangor,
			endretAv = endretAv,
			endretAvEnhet = endretAvEnhet,
			endret = endretDato,
		)

		@BeforeEach
		fun beforeEach() {
			every { navAnsattService.hentEllerOpprettNavAnsatt(endretAv).navn } returns "Navn"
			every { navEnhetService.hentOpprettEllerOppdaterNavEnhet(endretAvEnhet).navn } returns "EnhetNavn"
		}

		@Test
		fun `endring fra tiltakskoordinator - DeltMedArrangor`() {
			with(DeltakerDtoCtx()) {
				val historikk = listOf(DeltakerHistorikk.EndringFraTiltakskoordinator(endringFraTiltakskoordinator = endringInTest))
				medHistorikk(historikk)

				kafkaConsumerService.lagreNyDeltakerUlestEndring(deltakerDto, deltakerId)

				verify {
					ulestEndringRepository.insert(
						deltakerId,
						match {
							it is Oppdatering.DeltMedArrangor &&
								it.deltAvNavn == "Navn" &&
								it.deltAvEnhet == "EnhetNavn" &&
								it.delt == endretDato.toLocalDate()
						},
					)
				}
			}
		}

		@Test
		fun `lagreNyDeltakerUlestEndring - endring fra tiltakskoordinator - TildeltPlass`() {
			with(DeltakerDtoCtx()) {
				val endring = endringInTest.copy(endring = EndringFraTiltakskoordinator.TildelPlass)
				val historikk = listOf(DeltakerHistorikk.EndringFraTiltakskoordinator(endringFraTiltakskoordinator = endring))

				medHistorikk(historikk)

				kafkaConsumerService.lagreNyDeltakerUlestEndring(deltakerDto, deltakerId)

				verify {
					ulestEndringRepository.insert(
						deltakerId,
						match {
							it is Oppdatering.TildeltPlass &&
								it.tildeltPlassAvNavn == "Navn" &&
								it.tildeltPlassAvEnhet == "EnhetNavn" &&
								it.tildeltPlass == endretDato.toLocalDate()
						},
					)
				}
			}
		}

		@Test
		fun `lagreNyDeltakerUlestEndring - endring fra tiltakskoordinator - SettPaaVenteliste - lagrer ikke`() {
			with(DeltakerDtoCtx()) {
				val endring = endringInTest.copy(endring = EndringFraTiltakskoordinator.SettPaaVenteliste)
				val historikk = listOf(DeltakerHistorikk.EndringFraTiltakskoordinator(endringFraTiltakskoordinator = endring))
				medHistorikk(historikk)

				kafkaConsumerService.lagreNyDeltakerUlestEndring(deltakerDto, deltakerId)

				verify(exactly = 0) {
					ulestEndringRepository.insert(
						deltakerId,
						any(),
					)
				}
			}
		}

		@Test
		fun `lagreNyDeltakerUlestEndring - endring fra tiltakskoordinator - TildeltPlass og DelMedArrangor - lagrer den nyeste`() {
			with(DeltakerDtoCtx()) {
				val endretDato2 = LocalDateTime.now().plusDays(10)

				val endring1 = endringInTest.copy(endring = EndringFraTiltakskoordinator.DelMedArrangor)
				val endring2 = endringInTest.copy(endring = EndringFraTiltakskoordinator.TildelPlass, endret = endretDato2)

				val historikk = listOf(
					DeltakerHistorikk.EndringFraTiltakskoordinator(endringFraTiltakskoordinator = endring1),
					DeltakerHistorikk.EndringFraTiltakskoordinator(endringFraTiltakskoordinator = endring2),
				)
				medHistorikk(historikk)

				kafkaConsumerService.lagreNyDeltakerUlestEndring(deltakerDto, deltakerId)

				verify {
					ulestEndringRepository.insert(
						deltakerId,
						match {
							it is Oppdatering.TildeltPlass &&
								it.tildeltPlassAvNavn == "Navn" &&
								it.tildeltPlassAvEnhet == "EnhetNavn" &&
								it.tildeltPlass == endretDato2.toLocalDate()
						},
					)
				}
			}
		}
	}
}

class DeltakerDtoCtx {
	var deltakerlisteId: UUID = UUID.randomUUID()
	var deltakerDto = DeltakerKafkaPayload(
		id = deltakerlisteId,
		deltakerlisteId = UUID.randomUUID(),
		personalia =
			Personalia(
				personId = UUID.randomUUID(),
				personident = "10987654321",
				navn = Navn("Fornavn", null, "Etternavn"),
				kontaktinformasjon = Kontaktinformasjon("98989898", "epost@nav.no"),
				skjermet = false,
				adresse = getAdresse(),
				adressebeskyttelse = null,
			),
		status =
			DeltakerStatusDto(
				id = UUID.randomUUID(),
				type = DeltakerStatus.Type.DELTAR,
				gyldigFra = LocalDate.now().minusWeeks(1).atStartOfDay(),
				opprettetDato = LocalDateTime.now().minusWeeks(1),
				aarsak = null,
				aarsaksbeskrivelse = null,
			),
		dagerPerUke = null,
		prosentStilling = null,
		oppstartsdato = LocalDate.now().minusWeeks(5),
		sluttdato = LocalDate.now(),
		innsoktDato = LocalDate.now().minusMonths(2),
		bestillingTekst = "Bestilling",
		navKontor = "NAV Oslo",
		navVeileder = NavAnsatt(
			id = UUID.randomUUID(),
			navn = "Per Veileder",
			navIdent = "P123456",
			epost = null,
			telefon = null,
			navEnhetId = null,
		),
		deltarPaKurs = false,
		vurderingerFraArrangor = null,
		innhold = Deltakelsesinnhold(
			ledetekst = "Innholdsledetekst...",
			innhold = listOf(
				no.nav.amt.lib.models.deltaker.Innhold(
					tekst = "tekst",
					innholdskode = "kode",
					valgt = true,
					beskrivelse = "beskrivelse",
				),
			),
		),
		kilde = Kilde.ARENA,
		historikk = null, // vedtak?
		sistEndret = LocalDateTime.now(),
		forsteVedtakFattet = LocalDate.now().minusMonths(2),
		deltakerliste = Deltakerliste(
			id = deltakerlisteId,
			navn = "Tiltak hos Arrangør",
			tiltak = Tiltak(
				navn = "Tralala",
				tiltakskode = Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
			),
			startdato = null,
			sluttdato = null,
			oppstartstype = Oppstartstype.LOPENDE,
		),
		erManueltDeltMedArrangor = false,
		sisteEndring = null,
		oppfolgingsperioder = emptyList(),
		sistEndretAv = null,
		sistEndretAvEnhet = null,
	)

	fun medDeltakerlisteType(tiltakskode: Tiltakskode) {
		deltakerDto = deltakerDto.copy(
			deltakerliste = deltakerDto.deltakerliste.copy(
				tiltak = deltakerDto.deltakerliste.tiltak.copy(
					tiltakskode = tiltakskode,
				),
			),
		)
	}

	fun medSluttdato(dagerSiden: Long) {
		deltakerDto = deltakerDto.copy(
			sluttdato = LocalDate.now().minusDays(dagerSiden),
		)
	}

	fun medAdressebeskyttelse() {
		deltakerDto = deltakerDto.copy(
			personalia = deltakerDto.personalia.copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG),
		)
	}

	fun medDeltarPaKurs() {
		deltakerDto = deltakerDto.copy(deltarPaKurs = true)
	}

	fun medStatus(
		type: DeltakerStatus.Type,
		gyldigFraDagerSiden: Long = 1L,
		aarsak: DeltakerStatus.Aarsak.Type? = null,
		aarsakbeskrivelse: String? = null,
	) {
		deltakerDto = deltakerDto.copy(
			status = DeltakerStatusDto(
				id = UUID.randomUUID(),
				type = type,
				gyldigFra = LocalDate.now().minusDays(gyldigFraDagerSiden).atStartOfDay(),
				opprettetDato = LocalDateTime.now().minusDays(gyldigFraDagerSiden),
				aarsak = aarsak,
				aarsaksbeskrivelse = aarsakbeskrivelse,
			),
		)
	}

	fun medVurderinger() {
		deltakerDto = deltakerDto.copy(vurderingerFraArrangor = getVurderinger(deltakerDto.id))
	}

	fun medHistorikk(historikk: List<DeltakerHistorikk>) {
		deltakerDto = deltakerDto.copy(historikk = historikk)
	}

	fun medErManueltDeltMedArrangor() {
		deltakerDto = deltakerDto.copy(erManueltDeltMedArrangor = true)
	}
}
