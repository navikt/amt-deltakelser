package no.nav.amt.aktivitetskort.mock

import okhttp3.mockwebserver.MockResponse
import java.time.ZonedDateTime
import java.util.UUID

class MockVeilarboppfolgingServer : MockHttpServer("veilarboppfolging-server") {
    fun addResponse(oppfolgingsperiodeId: UUID = UUID.randomUUID()) {
        val endepunkt = "/veilarboppfolging/api/v3/oppfolging/hent-gjeldende-periode"

        addResponseHandler(
            endepunkt,
            MockResponse()
                .setResponseCode(
                    200,
                ).setBody("""{"uuid": "$oppfolgingsperiodeId", "startDato": "${ZonedDateTime.now().minusDays(5)}", "sluttDato": null}"""),
        )
    }
}
