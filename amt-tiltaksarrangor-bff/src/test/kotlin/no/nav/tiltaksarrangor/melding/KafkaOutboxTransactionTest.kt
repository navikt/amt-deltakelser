package no.nav.tiltaksarrangor.melding

import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MeterRegistry
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.tiltaksarrangor.IntegrationTestBase
import no.nav.tiltaksarrangor.melding.endring.EndringService
import no.nav.tiltaksarrangor.melding.endring.request.LeggTilOppstartsdatoRequest
import no.nav.tiltaksarrangor.melding.forslag.forlengDeltakelseForslag
import no.nav.tiltaksarrangor.testutils.DeltakerContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.queryForObject
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate

class KafkaOutboxTransactionTest(
    private val endringService: EndringService,
    private val meldingProducer: MeldingProducer,
    private val transactionTemplate: TransactionTemplate,
    private val meterRegistry: MeterRegistry,
) : IntegrationTestBase() {
    @Test
    fun `produsent krever en aktiv database transaksjon`() {
        assertThrows<IllegalTransactionStateException> {
            meldingProducer.produce(forlengDeltakelseForslag())
        }

        jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM kafka_producer_record") shouldBe 0
    }

    @Test
    fun `lagrer melding med eksisterende topic nøkkel og payload i outbox`() {
        val forslag = forlengDeltakelseForslag()

        transactionTemplate.executeWithoutResult {
            meldingProducer.produce(forslag)
        }

        val record = jdbcTemplate
            .query(
                "SELECT topic, key, value FROM kafka_producer_record",
            ) { rs, _ ->
                Triple(
                    rs.getString("topic"),
                    rs.getBytes("key").toString(Charsets.UTF_8),
                    objectMapper.readValue<Forslag>(rs.getBytes("value").toString(Charsets.UTF_8)),
                )
            }.single()

        record.first shouldBe MELDING_TOPIC
        record.second shouldBe forslag.id.toString()
        record.third shouldBe forslag
        meterRegistry.get("amt_tiltaksarrangor_bff_kafka_outbox_ventende").gauge().value() shouldBe 1.0
    }

    @Test
    fun `databaseendring og outboxmelding rulles tilbake sammen`() {
        with(DeltakerContext(applicationContext)) {
            setVenterPaOppstart()
            val original = deltakerRepository.getDeltaker(deltaker.id)
            val request = LeggTilOppstartsdatoRequest(
                startdato = LocalDate.now().plusWeeks(7),
                sluttdato = LocalDate.now().plusWeeks(42),
            )
            jdbcTemplate.execute(
                "ALTER TABLE kafka_producer_record ADD CONSTRAINT test_outbox_insert_failure CHECK (false)",
            )

            try {
                assertThrows<DataIntegrityViolationException> {
                    endringService.endreDeltaker(deltaker, deltakerliste, koordinator, request)
                }
            } finally {
                jdbcTemplate.execute("ALTER TABLE kafka_producer_record DROP CONSTRAINT test_outbox_insert_failure")
            }

            deltakerRepository.getDeltaker(deltaker.id) shouldBe original
            jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM kafka_producer_record") shouldBe 0
        }
    }
}
