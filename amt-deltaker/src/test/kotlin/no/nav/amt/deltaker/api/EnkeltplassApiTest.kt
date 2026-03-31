package no.nav.amt.deltaker.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.mockk.coVerify
import no.nav.amt.deltaker.deltaker.api.utils.postRequest
import no.nav.amt.deltaker.utils.RouteTestBase
import no.nav.amt.internapi.enkeltplass.MeldPaaDirekteEnkeltplassRequest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class EnkeltplassApiTest : RouteTestBase() {
    @Nested
    inner class MeldPaaDirekteTests {
        @Test
        fun `mangler token - returnerer Unauthorized`() {
            withTestApplicationContext { client ->
                client
                    .post("/enkeltplass-utkast/${UUID.randomUUID()}/meld-paa-direkte")
                    .status shouldBe HttpStatusCode.Unauthorized
            }
        }

        @Test
        fun `skal returnere 200 OK`() {
            val deltakerId = UUID.randomUUID()

            val request = MeldPaaDirekteEnkeltplassRequest(
                beskrivelse = "Testbeskrivelse",
                prisinformasjon = "Test prisinformasjon",
            )

            withTestApplicationContext { client ->
                client
                    .post("/enkeltplass-utkast/$deltakerId/meld-paa-direkte") {
                        postRequest(Unit)
                    }.status shouldBe HttpStatusCode.OK
            }

            coVerify { enkeltplassService.opprettGjennomforingRemote(deltakerId, request) }
        }
    }
}
