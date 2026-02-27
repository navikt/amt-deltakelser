package no.nav.amt.deltaker.bff.deltakerliste.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.arrangor.ArrangorService
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.bff.utils.KafkaConsumerFactory.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.slf4j.LoggerFactory
import java.util.UUID

class DeltakerlisteConsumer(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val arrangorService: ArrangorService,
    private val tiltakstypeRepository: TiltakstypeRepository,
    private val pameldingService: PameldingService,
    private val tilgangskontrollService: TilgangskontrollService,
    private val unleashToggle: CommonUnleashToggle,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer =
        buildManagedKafkaConsumer(
            topic = Environment.DELTAKERLISTE_V2_TOPIC,
            consumeFunc = ::consume,
        )

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    suspend fun consume(
        key: UUID,
        value: String?,
    ) {
        if (value == null) {
            deltakerlisteRepository.delete(key)
        } else {
            handterDeltakerliste(objectMapper.readValue(value))
        }
    }

    private suspend fun handterDeltakerliste(deltakerlistePayload: GjennomforingV2KafkaPayload) {
        if (!unleashToggle.skalLeseGjennomforing(deltakerlistePayload.tiltakskode.name)) {
            return
        }

        deltakerlistePayload.assertPameldingstypeIsValid()

        deltakerlisteRepository.get(deltakerlistePayload.id).onSuccess { eksisterendeDeltakerliste ->
            deltakerlistePayload.assertValidChanges(
                antallDeltakere = deltakerRepository.getAntallDeltakereForDeltakerliste(eksisterendeDeltakerliste.id),
                eksisterendePameldingstype = eksisterendeDeltakerliste.pameldingstype,
                eksisterendeOppstartstype = eksisterendeDeltakerliste.oppstart,
            )
        }

        val arrangor = arrangorService.hentArrangor(deltakerlistePayload.arrangor.organisasjonsnummer)
        val tiltakstype = tiltakstypeRepository.get(deltakerlistePayload.tiltakskode).getOrThrow()

        val deltakerliste =
            deltakerlistePayload.toModel(
                { gruppe -> gruppe.toModel(arrangor, tiltakstype) },
                { enkeltplass -> enkeltplass.toModel(arrangor, tiltakstype) },
            )

        deltakerlisteRepository.upsert(deltakerliste)

        if (deltakerliste.status == GjennomforingStatusType.AVLYST || deltakerliste.status == GjennomforingStatusType.AVBRUTT) {
            val kladderSomSkalSlettes = deltakerRepository.getKladderForDeltakerliste(deltakerliste.id)
            kladderSomSkalSlettes.forEach {
                pameldingService.slettKladd(it)
            }
            log.info("Slettet ${kladderSomSkalSlettes.size} for deltakerliste ${deltakerliste.id} med status ${deltakerliste.status.name}")

            tilgangskontrollService.stengTilgangerTilDeltakerliste(deltakerliste.id)
        }
    }
}
