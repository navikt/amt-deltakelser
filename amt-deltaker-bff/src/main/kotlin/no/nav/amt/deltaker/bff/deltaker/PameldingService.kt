package no.nav.amt.deltaker.bff.deltaker

import no.nav.amt.deltaker.bff.application.metrics.MetricRegister
import no.nav.amt.deltaker.bff.clients.PaameldingClient
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.util.UUID

class PameldingService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val paameldingClient: PaameldingClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun opprettKladd(
        deltakerlisteId: UUID,
        personident: String,
    ): DeltakerResponse {
        val response = paameldingClient.opprettKladd(
            personIdent = personident,
            deltakerlisteId = deltakerlisteId,
        )
        MetricRegister.OPPRETTET_KLADD.inc()
        return response
    }

    suspend fun slettKladd(deltakerId: UUID): Boolean {
        // deltaker kan være null hvis det er en enkelplass eller det er usync mellom databaser
        val deltaker = deltakerRepository.get(deltakerId).getOrNull()

        if (deltaker !== null && deltaker.status.type != DeltakerStatus.Type.KLADD) {
            log.warn("Kan ikke slette deltaker med id ${deltaker.id} som har status ${deltaker.status.type}")
            return false
        }
        paameldingClient.slettKladd(deltakerId)
        Database.transaction {
            deltakerService.deleteDeltaker(deltakerId)
        }
        return true
    }

    fun getKladder(personident: String): List<Deltaker> = deltakerRepository.getMany(personident).filter {
        it.status.type == DeltakerStatus.Type.KLADD
    }
}
