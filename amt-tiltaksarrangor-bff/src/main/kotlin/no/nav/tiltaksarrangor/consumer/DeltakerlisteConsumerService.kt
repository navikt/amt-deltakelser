package no.nav.tiltaksarrangor.consumer

import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.kafka.AmtGjennomforingPayload
import no.nav.tiltaksarrangor.client.amtarrangor.HentArrangorClient
import no.nav.tiltaksarrangor.consumer.ConsumerUtils.skalLagres
import no.nav.tiltaksarrangor.consumer.ConsumerUtils.toDeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

@Service
class DeltakerlisteConsumerService(
    private val arrangorRepository: ArrangorRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val hentArrangorClient: HentArrangorClient,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handleGjennomforing(
        deltakerlisteId: UUID,
        value: String?,
    ) {
        if (value == null) {
            deltakerlisteRepository.deleteDeltakerlisteOgDeltakere(deltakerlisteId)
            log.info("Slettet tombstonet deltakerliste med id $deltakerlisteId")
            return
        }

        val payload: AmtGjennomforingPayload = objectMapper.readValue(value)

        if (payload.type != GjennomforingType.Gruppe) {
            log.info("Gjennomføringstype ${payload.type} er ikke støttet.")
            return
        }

        if (payload.skalLagres()) {
            deltakerlisteRepository.insertOrUpdateDeltakerliste(
                payload.toDeltakerlisteDbo(
                    arrangorId = hentArrangorId(payload.arrangor.organisasjonsnummer),
                ),
            )
            log.info("Lagret deltakerliste med id $deltakerlisteId")
        } else {
            val antallSlettedeDeltakerlister = deltakerlisteRepository.deleteDeltakerlisteOgDeltakere(deltakerlisteId)
            if (antallSlettedeDeltakerlister > 0) {
                log.info("Slettet deltakerliste med id $deltakerlisteId")
            } else {
                log.info("Ignorert deltakerliste med id $deltakerlisteId")
            }
        }
    }

    private fun hentArrangorId(organisasjonsnummer: String): UUID {
        arrangorRepository.getArrangor(organisasjonsnummer)?.let { arrangor -> return arrangor.id }

        log.info("Fant ikke arrangør med orgnummer $organisasjonsnummer i databasen, henter fra amt-arrangor")
        val arrangor =
            hentArrangorClient.getArrangor(organisasjonsnummer)
                ?: throw RuntimeException("Kunne ikke hente arrangør med orgnummer $organisasjonsnummer")

        arrangorRepository.insertOrUpdateArrangor(arrangor.toArrangorDbo())
        return arrangor.id
    }
}
