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
import no.nav.amt.deltaker.bff.auth.TiltakskoordinatorTilgangRepository
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.tiltakskoordinator.TiltakskoordinatorService
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
    private val navAnsattService = mockk<NavAnsattService>()
    private val tiltakskoordinatorTilgangRepository = mockk<TiltakskoordinatorTilgangRepository>()
    private val tilgangskontrollService = TilgangskontrollService(
        poaoTilgangCachedClient,
        navAnsattService,
        tiltakskoordinatorTilgangRepository,
        mockk(relaxed = true),
        mockk<TiltakskoordinatorService>(),
        mockk<DeltakerlisteService>(),
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
                tilgangskontrollService,
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
            )
            setUpTestRoute()
        }
    }

    private fun Application.setUpTestRoute() {
        routing {
            authenticate("VEILEDER") {
                get("/fnr/{fnr}") {
                    val norskIdent = call.parameters["fnr"]!!
                    tilgangskontrollService.verifiserLesetilgang(call.getNavAnsattAzureId(), norskIdent)

                    call.respondText("Veileder har tilgang!")
                }
            }
        }
    }
}
