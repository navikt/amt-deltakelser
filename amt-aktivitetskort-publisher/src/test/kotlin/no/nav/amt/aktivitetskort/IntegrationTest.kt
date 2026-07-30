package no.nav.amt.aktivitetskort

import com.ninjasquad.springmockk.MockkBean
import no.nav.amt.aktivitetskort.client.AktivitetArenaAclClient
import no.nav.amt.aktivitetskort.client.AmtArenaAclClient
import no.nav.amt.aktivitetskort.client.AmtArrangorClient
import no.nav.amt.aktivitetskort.client.VeilarboppfolgingClient
import no.nav.amt.aktivitetskort.repositories.RepositoryTestBase
import no.nav.amt.aktivitetskort.unleash.UnleashTestConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate

@SpringBootTest(classes = [Application::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

    @MockkBean(relaxed = true)
    lateinit var kafkaTemplate: KafkaTemplate<String, String>
}
