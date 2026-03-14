package no.nav.tiltaksarrangor

import io.kotest.matchers.shouldBe
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.tiltaksarrangor.kafka.KafkaTestConfiguration
import no.nav.tiltaksarrangor.mock.MockAmtArrangorHttpServer
import no.nav.tiltaksarrangor.mock.MockAmtPersonHttpServer
import no.nav.tiltaksarrangor.testutils.DeltakerContext
import no.nav.tiltaksarrangor.unleash.UnleashTestConfiguration
import no.nav.tiltaksarrangor.utils.Issuer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.UUID

@EnableMockOAuth2Server
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(KafkaTestConfiguration::class, UnleashTestConfiguration::class)
abstract class IntegrationTest : RepositoryTestBase() {
	@Autowired
	protected lateinit var mockOAuth2Server: MockOAuth2Server

	@LocalServerPort
	private var localServerPort: Int = 0

	fun serverUrl() = "http://localhost:$localServerPort"

	val client = OkHttpClient.Builder().callTimeout(Duration.ofMinutes(5)).build()

	companion object {
		val mockAmtArrangorServer = MockAmtArrangorHttpServer()
		val mockAmtPersonServer = MockAmtPersonHttpServer()

		val kafkaContainer = KafkaContainer(DockerImageName.parse("apache/kafka")).apply {
			// workaround for https://github.com/testcontainers/testcontainers-java/issues/9506
			withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9093,CONTROLLER://:9094")
			start()
			System.setProperty("KAFKA_BROKERS", bootstrapServers)
		}

		@JvmStatic
		@DynamicPropertySource
		@Suppress("unused")
		fun registerProperties(registry: DynamicPropertyRegistry) {
			mockAmtArrangorServer.start()
			registry.add("amt-arrangor.url", mockAmtArrangorServer::serverUrl)
			mockAmtPersonServer.start()
			registry.add("amt-person.url", mockAmtPersonServer::serverUrl)
		}
	}

	fun sendRequest(
		method: String,
		path: String,
		body: RequestBody? = null,
		headers: Map<String, String> = emptyMap(),
	): Response {
		val reqBuilder = Request.Builder().url("${serverUrl()}$path").method(method, body)

		headers.forEach {
			reqBuilder.addHeader(it.key, it.value)
		}

		return client.newCall(reqBuilder.build()).execute()
	}

	fun getTokenxToken(
		fnr: String,
		audience: String = "amt-tiltaksarrangor-bff-client-id",
		issuerId: String = Issuer.TOKEN_X,
		clientId: String = "amt-tiltaksarrangor-flate",
		claims: Map<String, Any> = mapOf(
			"acr" to "Level4",
			"idp" to "idporten",
			"client_id" to clientId,
			"pid" to fnr,
		),
	): String = mockOAuth2Server
		.issueToken(
			issuerId,
			clientId,
			DefaultOAuth2TokenCallback(
				issuerId = issuerId,
				subject = UUID.randomUUID().toString(),
				audience = listOf(audience),
				claims = claims,
				expiry = 3600,
			),
		).serialize()

	fun emptyRequest(): RequestBody {
		val mediaTypeHtml = "application/json".toMediaType()
		return "".toRequestBody(mediaTypeHtml)
	}

	fun testTokenAutentisering(requestBuilders: List<Request.Builder>) {
		requestBuilders.forEach {
			val utenTokenResponse = client.newCall(it.build()).execute()
			utenTokenResponse.code shouldBe 401
			val feilTokenResponse = client
				.newCall(
					it
						.header(
							name = HttpHeaders.AUTHORIZATION,
							value = "Bearer ${mockOAuth2Server.issueToken("ikke-azuread").serialize()}",
						).build(),
				).execute()
			feilTokenResponse.code shouldBe 401
		}
	}

	fun testIkkeTilgangTilDeltakerliste(requestFunction: (deltakerId: UUID, ansattPersonIdent: String) -> Response) {
		with(DeltakerContext(applicationContext)) {
			setKoordinatorDeltakerliste(UUID.randomUUID())

			val response = requestFunction(deltaker.id, koordinator.personIdent)

			response.code shouldBe 403
		}
	}

	fun testDeltakerAdressebeskyttet(requestFunction: (deltakerId: UUID, ansattPersonIdent: String) -> Response) {
		with(DeltakerContext(applicationContext)) {
			setDeltakerAdressebeskyttet()

			val response = requestFunction(deltaker.id, koordinator.personIdent)

			response.code shouldBe 403
		}
	}

	fun testDeltakerSkjult(requestFunction: (deltakerId: UUID, ansattPersonIdent: String) -> Response) {
		with(DeltakerContext(applicationContext)) {
			setDeltakerSkjult()

			val response = requestFunction(deltaker.id, koordinator.personIdent)

			response.code shouldBe 400
		}
	}
}
