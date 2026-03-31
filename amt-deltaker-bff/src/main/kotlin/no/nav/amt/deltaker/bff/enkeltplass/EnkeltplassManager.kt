package no.nav.amt.deltaker.bff.enkeltplass

import no.nav.amt.deltaker.bff.apiclients.EnkeltplassClient
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker

class EnkeltplassManager(
    private val deltakerRepository: DeltakerRepository,
    private val enkeltplassClient: EnkeltplassClient,
) {
    suspend fun meldPaaDirekte(deltaker: Deltaker) {
        enkeltplassClient.meldPaaDirekte(deltaker.id)
    }
}
