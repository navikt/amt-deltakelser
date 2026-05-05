package no.nav.amt.deltaker.bff.innbygger

import no.nav.amt.deltaker.bff.clients.DtoMappers.toDeltakeroppdatering
import no.nav.amt.deltaker.bff.clients.PaameldingClient
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus

class InnbyggerService(
    private val deltakerService: DeltakerService,
    private val paameldingClient: PaameldingClient,
) {
    suspend fun godkjennUtkast(deltaker: Deltaker): Deltaker {
        require(deltaker.status.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
            "Deltaker ${deltaker.id} har ikke status ${DeltakerStatus.Type.UTKAST_TIL_PAMELDING}"
        }

        val utkastResponse = paameldingClient.innbyggerGodkjennUtkast(deltaker.id)
        val deltakeroppdatering = utkastResponse.toDeltakeroppdatering()

        deltakerService.oppdaterDeltaker(deltakeroppdatering)

        return deltaker.oppdater(deltakeroppdatering)
    }
}
