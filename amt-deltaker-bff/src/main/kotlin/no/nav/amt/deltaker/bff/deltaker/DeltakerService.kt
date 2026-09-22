package no.nav.amt.deltaker.bff.deltaker

import no.nav.amt.deltaker.bff.clients.AmtDeltakerClient
import java.time.ZonedDateTime
import java.util.UUID

class DeltakerService(
    private val amtDeltakerClient: AmtDeltakerClient,
) {
    // benyttes av Routing.registerInnbyggerApi
    suspend fun oppdaterSistBesokt(deltakerId: UUID) {
        val sistBesokt = ZonedDateTime.now()
        amtDeltakerClient.sistBesokt(deltakerId, sistBesokt)
    }
}
