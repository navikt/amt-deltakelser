package no.nav.tiltaksarrangor

import no.nav.tiltaksarrangor.testutils.DbTestDataUtils.cleanDatabase
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureJdbc
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource

@ActiveProfiles("test")
@AutoConfigureJdbc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
abstract class RepositoryTestBase {
	@Autowired
	protected lateinit var template: NamedParameterJdbcTemplate

	@Autowired
	protected lateinit var dataSource: DataSource

	@Autowired
	protected lateinit var applicationContext: ApplicationContext

	@AfterEach
	fun cleanDatabase() = cleanDatabase(dataSource)

	companion object {
		private const val POSTGRES_DOCKER_IMAGE_NAME = "postgres:17-alpine"

		@ServiceConnection
		@Suppress("unused")
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
