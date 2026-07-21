package no.nav.amt.aktivitetskort.repositories

import no.nav.amt.aktivitetskort.database.DbTestDataUtils
import no.nav.amt.aktivitetskort.database.TestDatabaseService
import no.nav.amt.aktivitetskort.unleash.UnleashTestConfiguration
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureJdbc
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource

@ActiveProfiles("test")
@SpringBootTest(
    classes = [
        ArrangorRepository::class, DeltakerlisteRepository::class, DeltakerRepository::class,
        FeilmeldingRepository::class, MeldingRepository::class, OppfolgingsperiodeRepository::class,
        TiltakstypeRepository::class, UnleashTestConfiguration::class, TestDatabaseService::class,
    ],
)
@AutoConfigureJdbc
@AutoConfigureJson
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
abstract class RepositoryTestBase {
    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    protected lateinit var testDatabase: TestDatabaseService

    @AfterEach
    fun cleanDatabase() = DbTestDataUtils.cleanDatabase(dataSource)

    companion object {
        private const val POSTGRES_DOCKER_IMAGE_NAME = "postgres:17-alpine"

        @ServiceConnection
        private val postgres = PostgreSQLContainer(
            DockerImageName
                .parse(POSTGRES_DOCKER_IMAGE_NAME)
                .asCompatibleSubstituteFor("postgres"),
        ).apply {
            addEnv("TZ", "Europe/Oslo")
            waitingFor(Wait.forListeningPort())
        }
    }
}
