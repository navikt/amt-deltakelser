package no.nav.amt.deltaker.bff.gjennomforing

import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.deltaker.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.PameldingService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.SelfServiceTilgangService
import no.nav.amt.deltaker.bff.tiltak.TiltakRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.bff.utils.KafkaConsumerFactory
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.slf4j.LoggerFactory
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class GjennomforingConsumer(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val arrangorService: ArrangorService,
    private val tiltakRepository: TiltakRepository,
    private val pameldingService: PameldingService,
    private val selfServiceTilgangService: SelfServiceTilgangService,
    private val unleashToggle: CommonUnleashToggle,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = KafkaConsumerFactory.buildManagedKafkaConsumer(
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

        // enkelte gjennomforinger skal ikke bli lest grunnet feil
        if (GjennomforingV2KafkaPayload.gjennomforingBlacklist.contains(deltakerlistePayload.id)) {
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
        val tiltakstype = tiltakRepository.get(deltakerlistePayload.tiltakskode).getOrThrow()

        val deltakerliste = deltakerlistePayload.toModel(
            { gruppe -> gruppe.toModel(arrangor, tiltakstype) },
            { enkeltplass -> enkeltplass.toModel(arrangor, tiltakstype) },
        )

        deltakerlisteRepository.upsert(deltakerliste)

        if (deltakerliste.status == GjennomforingStatusType.AVLYST || deltakerliste.status == GjennomforingStatusType.AVBRUTT) {
            val kladderSomSkalSlettes = deltakerRepository.getKladderForDeltakerliste(deltakerliste.id)
            kladderSomSkalSlettes.forEach {
                pameldingService.slettKladd(it.id)
            }
            log.info("Slettet ${kladderSomSkalSlettes.size} for deltakerliste ${deltakerliste.id} med status ${deltakerliste.status.name}")

            selfServiceTilgangService.stengTilgangerTilDeltakerliste(deltakerliste.id)
        }
    }
}
