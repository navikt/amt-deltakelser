package no.nav.tiltaksarrangor.consumer

import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.tiltaksarrangor.client.amtarrangor.AmtArrangorClient
import no.nav.tiltaksarrangor.client.amtarrangor.dto.toArrangorDbo
import no.nav.tiltaksarrangor.consumer.ConsumerUtils.getGjennomforingstypeFromJson
import no.nav.tiltaksarrangor.consumer.ConsumerUtils.skalLagres
import no.nav.tiltaksarrangor.consumer.ConsumerUtils.toDeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.TiltakstypeRepository
import no.nav.tiltaksarrangor.utils.objectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.readValue
import java.util.UUID

@Service
class DeltakerlisteConsumerService(
	private val arrangorRepository: ArrangorRepository,
	private val deltakerlisteRepository: DeltakerlisteRepository,
	private val tiltakstypeRepository: TiltakstypeRepository,
	private val amtArrangorClient: AmtArrangorClient,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun lagreDeltakerliste(deltakerlisteId: UUID, value: String?) {
		if (value == null) {
			deltakerlisteRepository.deleteDeltakerlisteOgDeltakere(deltakerlisteId)
			log.info("Slettet tombstonet deltakerliste med id $deltakerlisteId")
			return
		}

		val gjennomforingstypeFromJson = getGjennomforingstypeFromJson(value)

		if (gjennomforingstypeFromJson != GjennomforingV2KafkaPayload.GRUPPE_V2_TYPE) {
			log.info("Gjennomføringstype $gjennomforingstypeFromJson er ikke støttet.")
			return
		}

		val deltakerlistePayload: GjennomforingV2KafkaPayload.Gruppe = objectMapper.readValue(value)

		if (deltakerlistePayload.skalLagres()) {
			deltakerlistePayload.assertPameldingstypeIsValid()

			deltakerlisteRepository.insertOrUpdateDeltakerliste(
				deltakerlistePayload.toDeltakerlisteDbo(
					arrangorId = hentArrangorId(deltakerlistePayload.arrangor.organisasjonsnummer),
					navnTiltakstype = tiltakstypeRepository
						.getByTiltakskode(deltakerlistePayload.tiltakskode.name)
						?.navn
						?: throw IllegalStateException("Tiltakstype med tiltakskode ${deltakerlistePayload.tiltakskode} finnes ikke i db"),
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

		log.info("Fant ikke arrangør med orgnummer $organisasjonsnummer i databasen, henter fra amt-arrangør")
		val arrangor =
			amtArrangorClient.getArrangor(organisasjonsnummer)
				?: throw RuntimeException("Kunne ikke hente arrangør med orgnummer $organisasjonsnummer")

		arrangorRepository.insertOrUpdateArrangor(arrangor.toArrangorDbo())
		return arrangor.id
	}
}
