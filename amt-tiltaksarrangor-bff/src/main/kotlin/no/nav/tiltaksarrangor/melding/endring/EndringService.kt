package no.nav.tiltaksarrangor.melding.endring

import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.tiltaksarrangor.melding.MeldingProducer
import no.nav.tiltaksarrangor.melding.endring.request.EndringFraArrangorRequest
import no.nav.tiltaksarrangor.melding.endring.request.LeggTilOppstartsdatoRequest
import no.nav.tiltaksarrangor.model.Deltaker
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerlisteDbo
import no.nav.tiltaksarrangor.service.DeltakerMapper
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class EndringService(
	private val producer: MeldingProducer,
	private val deltakerRepository: DeltakerRepository,
	private val deltakerMapper: DeltakerMapper,
) {
	fun endreDeltaker(
		deltaker: DeltakerDbo,
		deltakerliste: DeltakerlisteDbo,
		ansatt: AnsattDbo,
		request: EndringFraArrangorRequest,
	): Deltaker {
		val endring = when (request) {
			is LeggTilOppstartsdatoRequest -> EndringFraArrangor.LeggTilOppstartsdato(request.startdato, request.sluttdato)
		}
		val endringFraArrangor = EndringFraArrangor(
			id = UUID.randomUUID(),
			deltakerId = deltaker.id,
			opprettetAvArrangorAnsattId = ansatt.id,
			opprettet = LocalDateTime.now(),
			endring = endring,
		)

		val endretDeltaker = deltaker.endre(endring)

		deltakerRepository.insertOrUpdateDeltaker(endretDeltaker)
		producer.produce(endringFraArrangor)

		return deltakerMapper.map(endretDeltaker, deltakerliste, ansatt)
	}

	private fun DeltakerDbo.endre(endring: EndringFraArrangor.Endring): DeltakerDbo = when (endring) {
		is EndringFraArrangor.LeggTilOppstartsdato -> this.copy(
			startdato = endring.startdato,
			sluttdato = endring.sluttdato,
		)
	}
}
