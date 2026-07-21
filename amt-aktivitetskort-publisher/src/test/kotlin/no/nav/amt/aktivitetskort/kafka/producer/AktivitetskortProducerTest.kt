package no.nav.amt.aktivitetskort.kafka.producer

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.amt.aktivitetskort.kafka.consumer.AKTIVITETSKORT_TOPIC
import no.nav.amt.aktivitetskort.kafka.producer.dto.AktivitetskortKasseringPayload
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class AktivitetskortProducerTest {
    private val template = mockk<KafkaTemplate<String, String>>(relaxed = true)
    private val objectMapper = jacksonObjectMapper()

    private val producer = AktivitetskortProducer(
        template = template,
        metricsService = mockk(relaxUnitFun = true),
        objectMapper = objectMapper,
    )

    @Test
    fun `slettAktivitetskort - sender kassering melding med riktige felter`() {
        val aktivitetskortId = UUID.randomUUID()
        val personIdent = "12345678901"
        val navIdent = "Z123456"
        val keySlot = slot<String>()
        val valueSlot = slot<String>()

        producer.slettAktivitetskort(aktivitetskortId, personIdent, navIdent)

        verify(exactly = 1) { template.send(AKTIVITETSKORT_TOPIC, capture(keySlot), capture(valueSlot)) }

        keySlot.captured shouldBe aktivitetskortId.toString()

        val payload = objectMapper.readValue<AktivitetskortKasseringPayload>(valueSlot.captured)
        assertSoftly(payload) {
            aktivitetsId shouldBe aktivitetskortId
            actionType shouldBe "KASSER_AKTIVITET"
            this.personIdent shouldBe personIdent
            this.navIdent shouldBe navIdent
            begrunnelse shouldBe "Kassering av duplikat aktivitetskort"
        }
    }
}
