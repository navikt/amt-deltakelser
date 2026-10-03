package no.nav.amt.aktivitetskort.client

import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.lib.spring.boot.client.executeUpstreamCall
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AmtDeltakerClient(
    private val api: AmtDeltakerApi,
) {
    fun getDeltaker(deltakerId: UUID): DeltakerResponse = executeUpstreamCall(
        serviceName = AMT_DELTAKER_CLIENT_ID,
        operation = "hente deltaker",
    ) { api.getDeltaker(deltakerId) }
}
