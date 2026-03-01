package no.nav.amt.distribusjon.hendelse

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.application.plugins.objectMapper
import no.nav.amt.distribusjon.distribusjonskanal.DokdistkanalClient
import no.nav.amt.distribusjon.hendelse.model.HendelseDto
import no.nav.amt.distribusjon.hendelse.model.toModel
import no.nav.amt.distribusjon.journalforing.JournalforingService
import no.nav.amt.distribusjon.tiltakshendelse.TiltakshendelseService
import no.nav.amt.distribusjon.varsel.VarselService
import no.nav.amt.distribusjon.veilarboppfolging.VeilarboppfolgingClient
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.kafka.ManagedKafkaConsumer
import no.nav.amt.lib.kafka.config.KafkaConfig
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.utils.database.Database.transaction
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.UUIDDeserializer
import org.slf4j.LoggerFactory
import java.util.UUID

class HendelseConsumer(
    private val varselService: VarselService,
    private val journalforingService: JournalforingService,
    private val tiltakshendelseService: TiltakshendelseService,
    private val hendelseRepository: HendelseRepository,
    private val dokdistkanalClient: DokdistkanalClient,
    private val veilarboppfolgingClient: VeilarboppfolgingClient,
    groupId: String = Environment.KAFKA_CONSUMER_GROUP_ID,
    kafkaConfig: KafkaConfig = if (Environment.isLocal()) LocalKafkaConfig() else KafkaConfigImpl(),
) : Consumer<UUID, String> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = ManagedKafkaConsumer(
        topic = Environment.DELTAKER_HENDELSE_TOPIC,
        config = kafkaConfig.consumerConfig(
            keyDeserializer = UUIDDeserializer(),
            valueDeserializer = StringDeserializer(),
            groupId = groupId,
        ),
        consume = ::consume,
    )

    suspend fun consume(key: UUID, value: String) {
        val hendelseDto: HendelseDto = objectMapper.readValue(value)
        log.info("Mottatt hendelse ${hendelseDto.id} for deltaker ${hendelseDto.deltaker.id}")

        val distribusjonskanal = dokdistkanalClient.bestemDistribusjonskanal(hendelseDto.deltaker.personident, hendelseDto.deltaker.id)
        val erUnderManuellOppfolging = veilarboppfolgingClient.erUnderManuellOppfolging(hendelseDto.deltaker.personident)
        val hendelse = hendelseDto.toModel(distribusjonskanal, erUnderManuellOppfolging)

        transaction {
            hendelseRepository.insert(hendelse)
            varselService.handleHendelse(hendelse)
            tiltakshendelseService.handleHendelse(hendelse)
        }

        journalforingService.handleHendelse(hendelse)
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()
}
