package no.nav.amt.aktivitetskort

import com.ninjasquad.springmockk.MockkBean
import no.nav.amt.aktivitetskort.client.AktivitetArenaAclClient
import no.nav.amt.aktivitetskort.client.AmtArenaAclClient
import no.nav.amt.aktivitetskort.client.AmtArrangorClient
import no.nav.amt.aktivitetskort.client.VeilarboppfolgingClient
import no.nav.amt.aktivitetskort.repositories.RepositoryTestBase
import no.nav.amt.aktivitetskort.unleash.UnleashTestConfiguration
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest(classes = [Application::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableMockOAuth2Server
@Import(UnleashTestConfiguration::class)
abstract class IntegrationTest : RepositoryTestBase() {
    @LocalServerPort
    private var port: Int = 0

    @MockkBean
    lateinit var aktivitetArenaAclClient: AktivitetArenaAclClient

    @MockkBean
    lateinit var amtArenaAclClient: AmtArenaAclClient

    @MockkBean
    lateinit var amtArrangorClient: AmtArrangorClient

    @MockkBean
    lateinit var veilarboppfolgingClient: VeilarboppfolgingClient

    companion object {
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
        }
    }
}
