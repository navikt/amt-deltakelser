package no.nav.amt.aktivitetskort.mock

import okhttp3.mockwebserver.MockResponse
import java.util.UUID

class MockAmtArenaAclServer : MockHttpServer("amt-arena-acl-server") {
    fun addArenaIdResponse(
        amtId: UUID,
        arenaId: Long?,
    ) {
        val endepunkt = "/api/v2/translation/$amtId"

        if (arenaId == null) {
            addResponseHandler(endepunkt, MockResponse().setResponseCode(404))
        } else {
            addResponseHandler(endepunkt, MockResponse().setResponseCode(200).setBody("""{"arenaId": "$arenaId", "arenaHistId": null}"""))
        }
    }
}
