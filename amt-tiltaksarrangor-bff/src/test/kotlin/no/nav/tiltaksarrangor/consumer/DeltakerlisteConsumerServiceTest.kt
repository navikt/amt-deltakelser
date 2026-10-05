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
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.utils.objectMapper
import no.nav.tiltaksarrangor.client.amtarrangor.HentArrangorClient
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.arrangorInTest
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.deltakerlisteIdInTest
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.gjennomforingPayloadInTest
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.testutils.getDeltakerliste
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeltakerlisteConsumerServiceTest {
    private val arrangorRepository = mockk<ArrangorRepository>()
    private val deltakerlisteRepository = mockk<DeltakerlisteRepository>()
    private val hentArrangorClient = mockk<HentArrangorClient>()

    private val sut =
        DeltakerlisteConsumerService(
            arrangorRepository = arrangorRepository,
            deltakerlisteRepository = deltakerlisteRepository,
            hentArrangorClient = hentArrangorClient,
            objectMapper = objectMapper,
        )

    @BeforeEach
    fun resetMocks() {
        clearAllMocks()

        every { deltakerlisteRepository.insertOrUpdateDeltakerliste(any()) } just Runs
        every { deltakerlisteRepository.getDeltakerliste(any()) } returns getDeltakerliste(arrangorInTest.id)
        every { deltakerlisteRepository.deleteDeltakerlisteOgDeltakere(any()) } returns 1

        every { arrangorRepository.getArrangor(arrangorInTest.organisasjonsnummer) } returns null
        every { arrangorRepository.insertOrUpdateArrangor(any()) } just Runs
        coEvery { hentArrangorClient.getArrangor(arrangorInTest.organisasjonsnummer) } returns arrangorInTest
    }

    @Test
    fun `handleGjennomforing - status GJENNOMFORES - lagres i db `() {
        sut.handleGjennomforing(
            deltakerlisteId = deltakerlisteIdInTest,
            value = objectMapper.writeValueAsString(
                gjennomforingPayloadInTest.copy(
                    pameldingstype = GjennomforingPameldingType.DIREKTE_VEDTAK,
                ),
            ),
        )

        verify(exactly = 1) { deltakerlisteRepository.insertOrUpdateDeltakerliste(any()) }
    }

    @Test
    fun `handleGjennomforing - status AVSLUTTET for 6 mnd siden - lagres ikke`() {
        val deltakerlisteDto = gjennomforingPayloadInTest.copy(
            navn = "Avsluttet tiltak",
            sluttDato = LocalDate.now().minusMonths(6),
            status = GjennomforingStatusType.AVSLUTTET,
        )

        sut.handleGjennomforing(
            deltakerlisteId = deltakerlisteIdInTest,
            value = objectMapper.writeValueAsString(deltakerlisteDto),
        )

        verify(exactly = 0) { deltakerlisteRepository.insertOrUpdateDeltakerliste(any()) }
        verify(exactly = 1) { deltakerlisteRepository.deleteDeltakerlisteOgDeltakere(deltakerlisteIdInTest) }
    }

    @Test
    fun `handleGjennomforing - status AVSLUTTET for 1 uke siden - lagres i db`() {
        val deltakerlisteDto = gjennomforingPayloadInTest.copy(
            navn = "Avsluttet tiltak",
            sluttDato = LocalDate.now().minusWeeks(1),
            status = GjennomforingStatusType.AVSLUTTET,
            pameldingstype = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )

        sut.handleGjennomforing(
            deltakerlisteId = deltakerlisteIdInTest,
            value = objectMapper.writeValueAsString(deltakerlisteDto),
        )

        verify(exactly = 1) { deltakerlisteRepository.insertOrUpdateDeltakerliste(any()) }
    }

    @Test
    fun `handleGjennomforing - ikke stottet gjennomforingstype - lagres ikke i db `() {
        val gjennomforingPayload = gjennomforingPayloadInTest.copy(
            type = GjennomforingType.Enkeltplass,
        )

        sut.handleGjennomforing(
            deltakerlisteId = deltakerlisteIdInTest,
            value = objectMapper.writeValueAsString(gjennomforingPayload),
        )

        verify(exactly = 0) { deltakerlisteRepository.insertOrUpdateDeltakerliste(any()) }
    }
}
