package no.nav.tiltaksarrangor.consumer

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.tiltaksarrangor.IntegrationTest
import no.nav.tiltaksarrangor.client.amtarrangor.dto.toArrangorDbo
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.arrangorInTest
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.deltakerlisteIdInTest
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.gjennomforingPayloadInTest
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.tiltakstypePayloadInTest
import no.nav.tiltaksarrangor.consumer.ConsumerUtils.toDeltakerlisteDbo
import no.nav.tiltaksarrangor.consumer.KafkaConsumer.Companion.ARRANGOR_ANSATT_TOPIC
import no.nav.tiltaksarrangor.consumer.KafkaConsumer.Companion.ARRANGOR_TOPIC
import no.nav.tiltaksarrangor.consumer.KafkaConsumer.Companion.DELTAKERLISTE_V2_TOPIC
import no.nav.tiltaksarrangor.consumer.KafkaConsumer.Companion.DELTAKER_TOPIC
import no.nav.tiltaksarrangor.consumer.KafkaConsumer.Companion.ENDRINGSMELDING_TOPIC
import no.nav.tiltaksarrangor.consumer.KafkaConsumer.Companion.TILTAKSTYPE_TOPIC
import no.nav.tiltaksarrangor.consumer.model.AnsattDto
import no.nav.tiltaksarrangor.consumer.model.AnsattPersonaliaDto
import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.consumer.model.ArrangorDto
import no.nav.tiltaksarrangor.consumer.model.EndringsmeldingDto
import no.nav.tiltaksarrangor.consumer.model.EndringsmeldingType
import no.nav.tiltaksarrangor.consumer.model.Innhold
import no.nav.tiltaksarrangor.consumer.model.NavnDto
import no.nav.tiltaksarrangor.consumer.model.TilknyttetArrangorDto
import no.nav.tiltaksarrangor.consumer.model.VeilederDto
import no.nav.tiltaksarrangor.consumer.model.toAnsattDbo
import no.nav.tiltaksarrangor.consumer.model.toArrangorDbo
import no.nav.tiltaksarrangor.consumer.model.toDeltakerDbo
import no.nav.tiltaksarrangor.consumer.model.toEndringsmeldingDbo
import no.nav.tiltaksarrangor.model.Endringsmelding
import no.nav.tiltaksarrangor.model.Veiledertype
import no.nav.tiltaksarrangor.repositories.AnsattRepository
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.EndringsmeldingRepository
import no.nav.tiltaksarrangor.repositories.TiltakstypeRepository
import no.nav.tiltaksarrangor.testutils.getDeltaker
import no.nav.tiltaksarrangor.testutils.getDeltakerliste
import no.nav.tiltaksarrangor.utils.objectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

class KafkaConsumerTest(
	private val arrangorRepository: ArrangorRepository,
	private val ansattRepository: AnsattRepository,
	private val deltakerRepository: DeltakerRepository,
	private val deltakerlisteRepository: DeltakerlisteRepository,
	private val endringsmeldingRepository: EndringsmeldingRepository,
	private val tiltakstypeRepository: TiltakstypeRepository,
	private val testKafkaProducer: KafkaProducer<String, String>,
) : IntegrationTest() {
	@Test
	fun `skal lagre tiltakstype i database`() {
		testKafkaProducer
			.send(
				ProducerRecord(
					TILTAKSTYPE_TOPIC,
					null,
					tiltakstypePayloadInTest.id.toString(),
					objectMapper.writeValueAsString(tiltakstypePayloadInTest),
				),
			).get()

		await().untilAsserted {
			tiltakstypeRepository.getByTiltakskode(tiltakstypePayloadInTest.tiltakskode) shouldNotBe null
		}
	}

	@Nested
	inner class ListenDeltakerliste {
		@Test
		fun `skal lagre deltakerliste i database`() {
			tiltakstypeRepository.upsert(tiltakstypePayloadInTest.toModel())
			arrangorRepository.insertOrUpdateArrangor(arrangorInTest.toArrangorDbo())

			testKafkaProducer
				.send(
					ProducerRecord(
						DELTAKERLISTE_V2_TOPIC,
						null,
						deltakerlisteIdInTest.toString(),
						objectMapper.writeValueAsString(gjennomforingPayloadInTest),
					),
				).get()

			await().untilAsserted {
				deltakerlisteRepository.getDeltakerliste(deltakerlisteIdInTest) shouldNotBe null
			}
		}

		@Test
		fun `skal slette deltakerliste i database`() {
			deltakerlisteRepository.insertOrUpdateDeltakerliste(
				gjennomforingPayloadInTest.toDeltakerlisteDbo(
					arrangorId = arrangorInTest.id,
					navnTiltakstype = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING.name,
				),
			)
			deltakerlisteRepository.getDeltakerliste(deltakerlisteIdInTest) shouldNotBe null

			testKafkaProducer
				.send(
					ProducerRecord(
						DELTAKERLISTE_V2_TOPIC,
						deltakerlisteIdInTest.toString(),
						null,
					),
				).get()

			await().untilAsserted {
				deltakerlisteRepository.getDeltakerliste(deltakerlisteIdInTest) shouldBe null
			}
		}

		@Test
		fun `skal slette deltakerliste og deltaker i database`() {
			deltakerlisteRepository.insertOrUpdateDeltakerliste(
				gjennomforingPayloadInTest.toDeltakerlisteDbo(
					arrangorId = arrangorInTest.id,
					navnTiltakstype = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING.name,
				),
			)
			deltakerlisteRepository.getDeltakerliste(deltakerlisteIdInTest) shouldNotBe null

			val deltaker = getDeltaker(deltakerId = UUID.randomUUID(), deltakerlisteId = deltakerlisteIdInTest)
			deltakerRepository.insertOrUpdateDeltaker(deltaker)
			deltakerRepository.getDeltaker(deltaker.id) shouldNotBe null

			val avsluttetDeltakerlisteDto = gjennomforingPayloadInTest.copy(status = GjennomforingStatusType.AVSLUTTET)

			testKafkaProducer
				.send(
					ProducerRecord(
						DELTAKERLISTE_V2_TOPIC,
						null,
						deltakerlisteIdInTest.toString(),
						objectMapper.writeValueAsString(avsluttetDeltakerlisteDto),
					),
				).get()

			await().untilAsserted {
				deltakerlisteRepository.getDeltakerliste(deltakerlisteIdInTest) shouldBe null
				deltakerRepository.getDeltaker(deltaker.id) shouldBe null
			}
		}
	}

	@Test
	fun `listen - melding pa arrangor-topic - lagres i database`() {
		val arrangorId = UUID.randomUUID()
		val arrangorDto =
			ArrangorDto(
				id = arrangorId,
				navn = "Arrangør AS",
				organisasjonsnummer = "88888888",
				overordnetArrangorId = UUID.randomUUID(),
			)

		testKafkaProducer
			.send(
				ProducerRecord(
					ARRANGOR_TOPIC,
					null,
					arrangorId.toString(),
					objectMapper.writeValueAsString(arrangorDto),
				),
			).get()

		await().until {
			arrangorRepository.getArrangor(arrangorId) != null
		}
	}

	@Test
	fun `listen - tombstonemelding pa arrangor-topic - slettes i database`() {
		val arrangorId = UUID.randomUUID()
		val arrangorDto =
			ArrangorDto(
				id = arrangorId,
				navn = "Arrangør AS",
				organisasjonsnummer = "77777777",
				overordnetArrangorId = null,
			)
		arrangorRepository.insertOrUpdateArrangor(arrangorDto.toArrangorDbo())
		testKafkaProducer
			.send(
				ProducerRecord(
					ARRANGOR_TOPIC,
					null,
					arrangorId.toString(),
					null,
				),
			).get()

		await().atMost(5, TimeUnit.SECONDS).until {
			arrangorRepository.getArrangor(arrangorId) == null
		}
	}

	@Test
	fun `listen - melding pa arrangor-ansatt-topic - lagres i database`() {
		val deltaker = getDeltaker(UUID.randomUUID())
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		val ansattId = UUID.randomUUID()
		val ansattDto =
			AnsattDto(
				id = ansattId,
				personalia =
					AnsattPersonaliaDto(
						personident = "12345678910",
						navn =
							NavnDto(
								fornavn = "Fornavn",
								mellomnavn = null,
								etternavn = "Etternavn",
							),
					),
				arrangorer =
					listOf(
						TilknyttetArrangorDto(
							arrangorId = UUID.randomUUID(),
							roller = listOf(AnsattRolle.KOORDINATOR, AnsattRolle.VEILEDER),
							veileder = listOf(VeilederDto(deltaker.id, Veiledertype.VEILEDER)),
							koordinator = listOf(UUID.randomUUID()),
						),
					),
			)
		testKafkaProducer
			.send(
				ProducerRecord(
					ARRANGOR_ANSATT_TOPIC,
					null,
					ansattId.toString(),
					objectMapper.writeValueAsString(ansattDto),
				),
			).get()

		await().atMost(5, TimeUnit.SECONDS).until {
			ansattRepository.getAnsatt(ansattId) != null &&
				ansattRepository.getAnsattRolleListe(ansattId).size == 2 &&
				ansattRepository.getKoordinatorDeltakerlisteDboListe(ansattId).size == 1 &&
				ansattRepository.getVeilederDeltakerDboListe(ansattId).size == 1
		}
	}

	@Test
	fun `listen - tombstonemelding pa arrangor-ansatt-topic - slettes i database`() {
		val deltaker = getDeltaker(UUID.randomUUID())
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		val ansattId = UUID.randomUUID()
		val ansattDto =
			AnsattDto(
				id = ansattId,
				personalia =
					AnsattPersonaliaDto(
						personident = "12345678910",
						navn =
							NavnDto(
								fornavn = "Fornavn",
								mellomnavn = null,
								etternavn = "Etternavn",
							),
					),
				arrangorer =
					listOf(
						TilknyttetArrangorDto(
							arrangorId = UUID.randomUUID(),
							roller = listOf(AnsattRolle.KOORDINATOR, AnsattRolle.VEILEDER),
							veileder = listOf(VeilederDto(deltaker.id, Veiledertype.VEILEDER)),
							koordinator = listOf(UUID.randomUUID()),
						),
					),
			)
		ansattRepository.insertOrUpdateAnsatt(ansattDto.toAnsattDbo())
		testKafkaProducer
			.send(
				ProducerRecord(
					ARRANGOR_ANSATT_TOPIC,
					null,
					ansattId.toString(),
					null,
				),
			).get()

		await().atMost(5, TimeUnit.SECONDS).until {
			ansattRepository.getAnsattRolleListe(ansattId).isEmpty() &&
				ansattRepository.getKoordinatorDeltakerlisteDboListe(ansattId).isEmpty() &&
				ansattRepository.getVeilederDeltakerDboListe(ansattId).isEmpty() &&
				ansattRepository.getAnsatt(ansattId) == null
		}
	}

	@Test
	fun `listen - melding pa deltaker-topic - lagres i database`() {
		val enhetId = UUID.randomUUID()
		val ansattId = UUID.randomUUID()
		with(DeltakerDtoCtx()) {
			deltakerlisteRepository.insertOrUpdateDeltakerliste(getDeltakerliste(id = deltakerDto.id, UUID.randomUUID()))
			mockAmtPersonServer.addEnhetResponse(enhetId)
			mockAmtPersonServer.addAnsattResponse(ansattId)
			val avbrytDeltakelseEndring = DeltakerEndring.Endring.AvbrytDeltakelse(
				DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.TRENGER_ANNEN_STOTTE, null),
				sluttdato = LocalDate.now().minusWeeks(4),
				begrunnelse = null,
			)
			val dto = deltakerDto.copy(
				historikk = listOf(
					DeltakerHistorikk.Endring(
						endring = DeltakerEndring(
							id = UUID.randomUUID(),
							endring = avbrytDeltakelseEndring,
							deltakerId = deltakerDto.id,
							endretAv = ansattId,
							endretAvEnhet = enhetId,
							forslag = null,
							endret = LocalDateTime.now(),
						),
					),
				),
			)
			medVurderinger()

			mockAmtPersonServer.addKontaktinformasjonResponse(dto.personalia.personident)

			testKafkaProducer
				.send(
					ProducerRecord(
						DELTAKER_TOPIC,
						null,
						dto.id.toString(),
						objectMapper.writeValueAsString(dto),
					),
				).get()

			await().atMost(5, TimeUnit.SECONDS).until {
				deltakerRepository.getDeltaker(dto.id) != null
			}
		}
	}

	@Test
	fun `listen - tombstonemelding pa deltaker-topic - slettes i database`() {
		val deltaker = getDeltaker(UUID.randomUUID())

		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		testKafkaProducer
			.send(
				ProducerRecord(
					DELTAKER_TOPIC,
					null,
					deltaker.id.toString(),
					null,
				),
			).get()

		await().atMost(5, TimeUnit.SECONDS).until {
			deltakerRepository.getDeltaker(deltaker.id) == null
		}
	}

	@Test
	fun `listen - avsluttet deltaker-melding pa deltaker-topic og deltaker finnes i db - sletter deltaker fra db`() {
		with(DeltakerDtoCtx()) {
			deltakerlisteRepository.insertOrUpdateDeltakerliste(getDeltakerliste(id = deltakerDto.id, UUID.randomUUID()))
			deltakerRepository.insertOrUpdateDeltaker(deltakerDto.toDeltakerDbo(null))

			medStatus(DeltakerStatus.Type.HAR_SLUTTET, 50)

			testKafkaProducer
				.send(
					ProducerRecord(
						DELTAKER_TOPIC,
						null,
						deltakerDto.id.toString(),
						objectMapper.writeValueAsString(deltakerDto),
					),
				).get()

			await().atMost(5, TimeUnit.SECONDS).until {
				deltakerRepository.getDeltaker(deltakerDto.id) == null
			}
		}
	}

	@Test
	fun `listen - melding pa endringsmelding-topic - lagres i database`() {
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
		testKafkaProducer
			.send(
				ProducerRecord(
					ENDRINGSMELDING_TOPIC,
					null,
					endringsmeldingId.toString(),
					objectMapper.writeValueAsString(endringsmeldingDto),
				),
			).get()

		await().atMost(5, TimeUnit.SECONDS).until {
			endringsmeldingRepository.getEndringsmelding(endringsmeldingId) != null
		}
	}

	@Test
	fun `listen - tombstonemelding pa endringsmelding-topic - slettes i database`() {
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
		endringsmeldingRepository.insertOrUpdateEndringsmelding(endringsmeldingDto.toEndringsmeldingDbo())
		testKafkaProducer
			.send(
				ProducerRecord(
					ENDRINGSMELDING_TOPIC,
					null,
					endringsmeldingId.toString(),
					null,
				),
			).get()

		await().atMost(5, TimeUnit.SECONDS).until {
			endringsmeldingRepository.getEndringsmelding(endringsmeldingId) == null
		}
	}

	@Test
	fun `listen - utfort endringsmelding-melding pa endringmelding-topic og endringsmelding finnes i db - oppdaterer endringsmelding i db`() {
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
		endringsmeldingRepository.insertOrUpdateEndringsmelding(endringsmeldingDto.toEndringsmeldingDbo())
		val utfortEndringsmeldingDto =
			EndringsmeldingDto(
				id = endringsmeldingId,
				deltakerId = UUID.randomUUID(),
				utfortAvNavAnsattId = null,
				opprettetAvArrangorAnsattId = UUID.randomUUID(),
				utfortTidspunkt = null,
				status = Endringsmelding.Status.UTFORT,
				type = EndringsmeldingType.ENDRE_SLUTTDATO,
				innhold = Innhold.EndreSluttdatoInnhold(sluttdato = LocalDate.now().plusWeeks(3)),
				createdAt = LocalDateTime.now(),
			)
		testKafkaProducer
			.send(
				ProducerRecord(
					ENDRINGSMELDING_TOPIC,
					null,
					endringsmeldingId.toString(),
					objectMapper.writeValueAsString(utfortEndringsmeldingDto),
				),
			).get()

		await().atMost(5, TimeUnit.SECONDS).until {
			endringsmeldingRepository.getEndringsmelding(endringsmeldingId)?.status == Endringsmelding.Status.UTFORT
		}
	}
}
