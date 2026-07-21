package no.nav.amt.aktivitetskort

import no.nav.amt.aktivitetskort.mock.MockAktivitetArenaAclServer
import no.nav.amt.aktivitetskort.mock.MockAmtArenaAclServer
import no.nav.amt.aktivitetskort.mock.MockMachineToMachineServer
import no.nav.amt.aktivitetskort.mock.MockVeilarboppfolgingServer
import no.nav.amt.aktivitetskort.repositories.RepositoryTestBase
import no.nav.amt.aktivitetskort.unleash.UnleashTestConfiguration
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.awaitility.Awaitility
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.concurrent.TimeUnit

@SpringBootTest(classes = [Application::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(UnleashTestConfiguration::class)
abstract class IntegrationTest : RepositoryTestBase() {
    @LocalServerPort
    private var port: Int = 0

    fun serverUrl() = "http://localhost:$port"

    private val client = OkHttpClient
        .Builder()
        .callTimeout(Duration.ofMinutes(5))
        .build()

    init {
        Awaitility.setDefaultTimeout(10, TimeUnit.SECONDS)
    }

    companion object {
        val mockAktivitetArenaAclServer = MockAktivitetArenaAclServer()
        val mockAmtArenaAclServer = MockAmtArenaAclServer()
        val mockMachineToMachineServer = MockMachineToMachineServer()
        val mockVeilarboppfolgingServer = MockVeilarboppfolgingServer()

        @Suppress("unused")
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
            mockMachineToMachineServer.start()
            registry.add("nais.env.azureOpenIdConfigTokenEndpoint") {
                mockMachineToMachineServer.serverUrl() + MockMachineToMachineServer.TOKEN_PATH
            }

            mockAmtArenaAclServer.start()
            registry.add("amt.arena-acl.url") { mockAmtArenaAclServer.serverUrl() }
            registry.add("amt.arena-acl.scope") { "test.amt-arena-acl" }

            registry.add("amt.arrangor.url") { "" }
            registry.add("amt.arrangor.scope") { "test.amt-arrangor" }

            mockVeilarboppfolgingServer.start()
            registry.add("veilarboppfolging.url") { mockVeilarboppfolgingServer.serverUrl() }
            registry.add("veilarboppfolging.scope") { "test.veilarboppfolging" }

            mockAktivitetArenaAclServer.start()
            registry.add("aktivitet.arena-acl.url") { mockAktivitetArenaAclServer.serverUrl() }
            registry.add("aktivitet.arena-acl.scope") { "test.dab-arena-acl" }
        }
    }

    protected fun sendRequest(
        method: String,
        path: String,
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap(),
    ): Response {
        val reqBuilder = Request
            .Builder()
            .url("${serverUrl()}$path")
            .method(method, body)

        headers.forEach {
            reqBuilder.addHeader(it.key, it.value)
        }

        return client.newCall(reqBuilder.build()).execute()
    }
}
