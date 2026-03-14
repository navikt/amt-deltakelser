package no.nav.tiltaksarrangor.consumer

import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.tiltaksarrangor.client.amtarrangor.AmtArrangorClient
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.arrangorInTest
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.deltakerlisteIdInTest
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.gjennomforingPayloadInTest
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.tiltakstypePayloadInTest
import no.nav.tiltaksarrangor.consumer.ConsumerUtils.GJENNOMFORINGSTYPE_KEY
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.TiltakstypeRepository
import no.nav.tiltaksarrangor.testutils.getDeltakerliste
import no.nav.tiltaksarrangor.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate

class DeltakerlisteConsumerServiceTest {
	private val arrangorRepository = mockk<ArrangorRepository>()
	private val deltakerlisteRepository = mockk<DeltakerlisteRepository>()
	private val tiltakstypeRepository = mockk<TiltakstypeRepository>()
	private val amtArrangorClient = mockk<AmtArrangorClient>()

	private val sut =
		DeltakerlisteConsumerService(
			arrangorRepository = arrangorRepository,
			deltakerlisteRepository = deltakerlisteRepository,
			tiltakstypeRepository = tiltakstypeRepository,
			amtArrangorClient = amtArrangorClient,
		)

	@BeforeEach
	fun resetMocks() {
		clearAllMocks()

		every { deltakerlisteRepository.insertOrUpdateDeltakerliste(any()) } just Runs
		every { deltakerlisteRepository.getDeltakerliste(any()) } returns getDeltakerliste(arrangorInTest.id)
		every { deltakerlisteRepository.deleteDeltakerlisteOgDeltakere(any()) } returns 1
		every { tiltakstypeRepository.getByTiltakskode(any()) } returns tiltakstypePayloadInTest.toModel()

		every { arrangorRepository.getArrangor(arrangorInTest.organisasjonsnummer) } returns null
		every { arrangorRepository.insertOrUpdateArrangor(any()) } just Runs
		coEvery { amtArrangorClient.getArrangor(arrangorInTest.organisasjonsnummer) } returns arrangorInTest
	}

	@Test
	fun `lagreDeltakerliste - status GJENNOMFORES - lagres i db `() {
		sut.lagreDeltakerliste(
			deltakerlisteId = deltakerlisteIdInTest,
			value = objectMapper.writeValueAsString(
				gjennomforingPayloadInTest.copy(
					pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
				),
			),
		)

		verify(exactly = 1) { deltakerlisteRepository.insertOrUpdateDeltakerliste(any()) }
	}

	@Test
	fun `lagreDeltakerliste - status AVSLUTTET for 6 mnd siden - lagres ikke`() {
		val deltakerlisteDto = gjennomforingPayloadInTest.copy(
			navn = "Avsluttet tiltak",
			sluttDato = LocalDate.now().minusMonths(6),
			status = GjennomforingStatusType.AVSLUTTET,
		)

		sut.lagreDeltakerliste(
			deltakerlisteId = deltakerlisteIdInTest,
			value = objectMapper.writeValueAsString(deltakerlisteDto),
		)

		verify(exactly = 0) { deltakerlisteRepository.insertOrUpdateDeltakerliste(any()) }
		verify(exactly = 1) { deltakerlisteRepository.deleteDeltakerlisteOgDeltakere(deltakerlisteIdInTest) }
	}

	@Test
	fun `lagreDeltakerliste - status AVSLUTTET for 1 uke siden - lagres i db`() {
		val deltakerlisteDto = gjennomforingPayloadInTest.copy(
			navn = "Avsluttet tiltak",
			sluttDato = LocalDate.now().minusWeeks(1),
			status = GjennomforingStatusType.AVSLUTTET,
			pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK,
		)

		sut.lagreDeltakerliste(
			deltakerlisteId = deltakerlisteIdInTest,
			value = objectMapper.writeValueAsString(deltakerlisteDto),
		)

		verify(exactly = 1) { deltakerlisteRepository.insertOrUpdateDeltakerliste(any()) }
	}

	@Test
	fun `lagreDeltakerliste - ikke stottet gjennomforingstype - lagres ikke i db `() {
		val gjennomforingPayload = gjennomforingPayloadInTest.copy(
			tiltakskode = Tiltakskode.STUDIESPESIALISERING,
		)

		val jsonAsMap = objectMapper.readValue<MutableMap<String, Any>>(objectMapper.writeValueAsString(gjennomforingPayload))
		jsonAsMap[GJENNOMFORINGSTYPE_KEY] = GjennomforingV2KafkaPayload.ENKELTPLASS_V2_TYPE

		sut.lagreDeltakerliste(
			deltakerlisteId = deltakerlisteIdInTest,
			value = objectMapper.writeValueAsString(jsonAsMap),
		)

		verify(exactly = 0) { deltakerlisteRepository.insertOrUpdateDeltakerliste(any()) }
	}
}
