package no.nav.tiltaksarrangor.melding

import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.tiltaksarrangor.utils.objectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

const val MELDING_TOPIC = "amt.arrangor-melding-v1"

@Service
class MeldingProducer(
	private val producer: Producer<String, String>,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun produce(forslag: Forslag) {
		when (forslag.status) {
			is Forslag.Status.Avvist,
			is Forslag.Status.Godkjent,
			-> error("Forsøkte å produsere forslag ${forslag.id} med status ${forslag.status::class.simpleName}")

			is Forslag.Status.Tilbakekalt,
			is Forslag.Status.Erstattet,
			is Forslag.Status.VenterPaSvar,
			-> {
				producer.produce(MELDING_TOPIC, forslag.id.toString(), objectMapper.writeValueAsString(forslag))
				log.info("Produserte forslag ${forslag.id} med status ${forslag.status::class.simpleName}")
			}
		}
	}

	fun produce(endring: EndringFraArrangor) {
		producer.produce(MELDING_TOPIC, endring.id.toString(), objectMapper.writeValueAsString(endring))
		log.info("Produserte endring fra arrangør ${endring.id}")
	}

	fun produce(vurdering: Vurdering) {
		producer.produce(MELDING_TOPIC, vurdering.id.toString(), objectMapper.writeValueAsString(vurdering))
		log.info("Produserte vurdering fra arrangør ${vurdering.id}")
	}
}
