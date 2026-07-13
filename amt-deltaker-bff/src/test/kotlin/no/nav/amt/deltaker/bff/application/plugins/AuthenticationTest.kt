package no.nav.amt.deltaker.bff.application.plugins

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.utils.configureEnvForAuthentication
import no.nav.amt.deltaker.bff.utils.generateJWT
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class AuthenticationTest {
    private val poaoTilgangCachedClient = mockk<PoaoTilgangCachedClient>()
    private val tilgangskontrollService = TilgangskontrollService(
        poaoTilgangCachedClient,
    )

    @BeforeEach
    fun setup() = configureEnvForAuthentication()

    @Test
    fun `testAuthentication - gyldig token, ansatt har tilgang - returnerer 200`() = testApplication {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        setUpTestApplication()
        client
            .get("/fnr/12345678910") {
                bearerAuth(generateJWT("frontend-clientid", UUID.randomUUID().toString(), "deltaker-bff"))
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals("Veileder har tilgang!", bodyAsText())
            }
    }

    @Test
    fun `testAuthentication - gyldig token, ansatt har ikke tilgang - returnerer 403`() = testApplication {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(
            null,
            Decision.Deny("Ikke tilgang", ""),
        )
        setUpTestApplication()
        client
            .get("/fnr/12345678910") {
                bearerAuth(generateJWT("frontend-clientid", UUID.randomUUID().toString(), "deltaker-bff"))
            }.apply {
                assertEquals(HttpStatusCode.Forbidden, status)
            }
    }

    @Test
    fun `testAuthentication - ugyldig tokenissuer - returnerer 401`() = testApplication {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)
        setUpTestApplication()
        client
            .get("/fnr/12345678910") {
                bearerAuth(generateJWT("frontend-clientid", UUID.randomUUID().toString(), "feilIssuer"))
            }.apply {
                assertEquals(HttpStatusCode.Unauthorized, status)
            }
    }

    private fun ApplicationTestBuilder.setUpTestApplication() {
        application {
            configureSerialization()
            configureAuthentication(Environment())
            configureRouting(
                tilgangskontrollService = tilgangskontrollService,
                deltakerService = mockk(),
                pameldingService = mockk(),
                navAnsattService = mockk(),
                forslagRepository = mockk(),
                amtDistribusjonClient = mockk(),
                amtDeltakerClient = mockk(),
                arrangorsokClient = mockk(),
                enkeltplassClient = mockk(),
                sporbarhetsloggService = mockk(),
                deltakerlisteService = mockk(),
                unleash = mockk(),
                tiltakskoordinatorTilgangskontrollService = mockk(),
                tiltakskoordinatorTilgangRepository = mockk(),
                paameldingClient = mockk(),
                opplaringKategoriseringClient = mockk(),
                selfServiceTilgangService = mockk(),
                tiltakskoordinatorResponseBuilder = mockk(),
                tiltakskoordinatorClient = mockk(),
            )
            setUpTestRoute()
        }
    }

    private fun Application.setUpTestRoute() {
        routing {
            authenticate(AuthLevel.VEILEDER.name) {
                get("/fnr/{fnr}") {
                    val norskIdent = call.parameters["fnr"]!!
                    tilgangskontrollService.verifiserLesetilgang(call.getNavAnsattAzureId(), norskIdent)

                    call.respondText("Veileder har tilgang!")
                }
            }
        }
    }
}
