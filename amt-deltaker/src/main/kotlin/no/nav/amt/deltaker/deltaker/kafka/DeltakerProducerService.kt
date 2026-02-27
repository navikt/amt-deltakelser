package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import java.util.UUID

class DeltakerProducerService(
    private val deltakerKafkaPayloadBuilder: DeltakerKafkaPayloadBuilder,
    private val deltakerProducer: DeltakerProducer,
    private val deltakerV1Producer: DeltakerV1Producer,
    private val deltakerEksternV1Producer: DeltakerEksternV1Producer,
    private val unleashToggle: CommonUnleashToggle,
) {
    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    fun produce(
        deltaker: Deltaker,
        forcedUpdate: Boolean? = false,
        publiserTilDeltakerV1: Boolean = true,
        publiserTilDeltakerEksternV1: Boolean = true,
        publiserTilDeltakerV2: Boolean = true,
    ) {
        if (deltaker.status.type == DeltakerStatus.Type.KLADD) {
            log.info("Skipper publisering deltaker ${deltaker.id} med status ${DeltakerStatus.Type.KLADD.name}")
            return
        }

        if (publiserTilDeltakerV1) {
            produceDeltakerV1Topic(deltaker)
        }

        if (publiserTilDeltakerEksternV1) {
            produceDeltakerEksternV1Topic(deltaker)
        }

        if (publiserTilDeltakerV2) {
            produceDeltakerV2Topic(deltaker, forcedUpdate)
        }
    }

    private fun produceDeltakerV1Topic(deltaker: Deltaker) {
        val deltakerV1Record = deltakerKafkaPayloadBuilder.buildDeltakerV1Record(deltaker)
        if (unleashToggle.erKometMasterForTiltakstype(deltaker.deltakerliste.tiltakstype.tiltakskode)) {
            deltakerV1Producer.produce(deltakerV1Record)
        }
    }

    private fun produceDeltakerEksternV1Topic(deltaker: Deltaker) {
        val deltakerEksternV1Record = deltakerKafkaPayloadBuilder.buildDeltakerEksternV1Record(deltaker)
        if (unleashToggle.skalProdusereTilDeltakerEksternTopic()) {
            deltakerEksternV1Producer.produce(deltakerEksternV1Record)
        }
    }

    private fun produceDeltakerV2Topic(deltaker: Deltaker, forcedUpdate: Boolean? = false) {
        val deltakerV2Record = deltakerKafkaPayloadBuilder.buildDeltakerV2Record(deltaker, forcedUpdate)
        if (unleashToggle.erKometMasterForTiltakstype(deltaker.deltakerliste.tiltakstype.tiltakskode) ||
            unleashToggle.skalLeseArenaDataForTiltakstype(deltaker.deltakerliste.tiltakstype.tiltakskode)
        ) {
            deltakerProducer.produce(deltakerV2Record)
        }
    }

    fun tombstone(deltakerId: UUID) {
        deltakerProducer.produceTombstone(deltakerId)
        deltakerV1Producer.produceTombstone(deltakerId)
        deltakerEksternV1Producer.produceTombstone(deltakerId)
    }
}
