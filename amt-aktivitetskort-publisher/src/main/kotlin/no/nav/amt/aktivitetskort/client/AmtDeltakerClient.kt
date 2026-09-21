package no.nav.amt.aktivitetskort.client

import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AmtDeltakerClient(
    private val api: AmtDeltakerApi,
) {
    fun getDeltaker(deltakerId: UUID): DeltakerResponse = api.getDeltaker(deltakerId)
}
