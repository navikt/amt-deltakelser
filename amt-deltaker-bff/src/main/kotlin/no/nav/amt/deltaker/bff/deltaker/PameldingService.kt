package no.nav.amt.deltaker.bff.deltaker

import no.nav.amt.deltaker.bff.application.metrics.MetricRegister
import no.nav.amt.deltaker.bff.clients.PaameldingClient
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

class PameldingService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val paameldingClient: PaameldingClient,
) {
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
        // Call amt-deltaker to delete the kladd
        val deleted = paameldingClient.slettKladdOgDeltaker(deltakerId)
        
        if (deleted) {
            // Delete from bff database
            Database.transaction {
                deltakerService.deleteDeltaker(deltakerId)
            }
        }
        
        return deleted
    }

    fun getKladder(personident: String): List<Deltaker> = deltakerRepository.getMany(personident).filter {
        it.status.type == DeltakerStatus.Type.KLADD
    }
}
