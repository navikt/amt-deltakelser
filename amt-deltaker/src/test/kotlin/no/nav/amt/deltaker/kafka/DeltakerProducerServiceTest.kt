package no.nav.amt.deltaker.kafka

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.amt.deltaker.kafka.payload.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.kafka.payload.DeltakerV1Dto
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.junit.jupiter.api.Test

class DeltakerProducerServiceTest {
    private val deltakerKafkaPayloadBuilder = mockk<DeltakerKafkaPayloadBuilder>()
    private val deltakerProducer = mockk<DeltakerProducer>(relaxed = true)
    private val deltakerV1Producer = mockk<DeltakerV1Producer>(relaxed = true)
    private val deltakerEksternV1Producer = mockk<DeltakerEksternV1Producer>(relaxed = true)
    private val unleashToggle = mockk<CommonUnleashToggle>()

    private val deltakerProducerService = DeltakerProducerService(
        deltakerKafkaPayloadBuilder = deltakerKafkaPayloadBuilder,
        deltakerProducer = deltakerProducer,
        deltakerV1Producer = deltakerV1Producer,
        deltakerEksternV1Producer = deltakerEksternV1Producer,
        unleashToggle = unleashToggle,
    )

    @Test
    fun `produce - tiltakskode TILRETTELAGT_ARBEID_ORDINAER - publiserer ikke til deltaker-v1`() {
        val deltaker = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(
                tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER),
            ),
        )
        // Selv om Komet er master skal vi ikke publisere denne tiltakstypen til deltaker-v1
        every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true

        deltakerProducerService.produce(
            deltaker = deltaker,
            publiserTilDeltakerEksternV1 = false,
            publiserTilDeltakerV2 = false,
        )

        verify(exactly = 0) { deltakerKafkaPayloadBuilder.buildDeltakerV1Record(any()) }
        verify(exactly = 0) { deltakerV1Producer.produce(any()) }
    }

    @Test
    fun `produce - annen tiltakskode der Komet er master - publiserer til deltaker-v1`() {
        val deltaker = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(
                tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.OPPFOLGING),
            ),
        )
        val v1Record = mockk<DeltakerV1Dto>()
        every { deltakerKafkaPayloadBuilder.buildDeltakerV1Record(deltaker) } returns v1Record
        every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true

        deltakerProducerService.produce(
            deltaker = deltaker,
            publiserTilDeltakerEksternV1 = false,
            publiserTilDeltakerV2 = false,
        )

        verify(exactly = 1) { deltakerV1Producer.produce(v1Record) }
    }
}
