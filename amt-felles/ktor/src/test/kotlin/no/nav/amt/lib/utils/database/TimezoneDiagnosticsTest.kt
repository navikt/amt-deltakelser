package no.nav.amt.lib.utils.database

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotliquery.queryOf
import no.nav.amt.lib.testing.TestPostgresContainer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

@ResourceLock(Resources.TIME_ZONE)
class TimezoneDiagnosticsTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupAll() = TestPostgresContainer.bootstrap()
    }

    @ParameterizedTest
    @ValueSource(strings = ["UTC", "Europe/Oslo", "GMT+02:00"])
    fun `diagnostikk viser faktisk JDBC-mapping for vinter og sommer`(jvmZone: String) {
        val originalTimeZone = TimeZone.getDefault()

        try {
            TimeZone.setDefault(TimeZone.getTimeZone(jvmZone))

            for (databaseZone in listOf("UTC", "Europe/Oslo", "Etc/GMT-2")) {
                captureLogs { logger, events ->
                    Database.transaction {
                        Database.query { session ->
                            session.run(
                                queryOf("SELECT set_config('TimeZone', ?, true) AS zone", databaseZone)
                                    .map { row -> row.string("zone") }
                                    .asSingle,
                            )
                        }

                        logTimezoneDiagnostics(logger)
                    }

                    val messages = events.map { it.formattedMessage }
                    messages.first() shouldContain "jvmZone=$jvmZone"
                    events.filter { it.level == Level.INFO } shouldHaveSize 4
                    messages.single { it.contains("databaseZone=") } shouldContain
                        "databaseZone=$databaseZone databaseZoneSource=session"

                    val samples = mapOf(
                        "winter" to Instant.parse("2026-11-02T22:30:00Z"),
                        "summer" to Instant.parse("2026-07-02T21:30:00Z"),
                    )

                    for ((sample, instant) in samples) {
                        val mapped = instant.atZone(ZoneId.of(jvmZone)).toLocalDateTime()
                        val expected = instant.atZone(ZoneId.of("Europe/Oslo")).toLocalDateTime()
                        val message = messages.single { it.contains("sample=$sample instant=") }

                        message shouldContain "instant=$instant"
                        message shouldContain "mappedLocalDateTime=$mapped"
                        message shouldContain "expectedOsloDateTime=$expected"
                        message shouldContain "mappedDate=${mapped.toLocalDate()}"
                        message shouldContain "expectedOsloDate=${expected.toLocalDate()}"
                        message shouldContain "matchesOslo=${mapped == expected}"

                        events.count { it.level == Level.WARN && it.formattedMessage.contains("sample=$sample ") } shouldBe
                            if (mapped == expected) 0 else 1
                    }

                    if (jvmZone == "GMT+02:00") {
                        messages.single { it.contains("sample=winter instant=") } shouldContain "mappedDate=2026-11-03"
                    }
                }
            }
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun `databasefeil logges uten feilmelding eller stacktrace og stopper ikke oppstart`() {
        mockkObject(Database)

        try {
            every { Database.query<Unit>(any()) } throws SQLException("must-not-be-logged", "08006", 7)

            captureLogs { logger, events ->
                logTimezoneDiagnostics(logger)

                events shouldHaveSize 2
                events.last().level shouldBe Level.WARN
                events.last().formattedMessage shouldContain "database probe failed sqlState=08006 errorCode=7"
                events.last().formattedMessage shouldNotContain "must-not-be-logged"
                events.last().throwableProxy shouldBe null
            }
        } finally {
            unmockkObject(Database)
        }
    }

    private fun captureLogs(assertions: (Logger, List<ILoggingEvent>) -> Unit) {
        val logger = LoggerFactory.getLogger(javaClass) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        try {
            assertions(logger, appender.list)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }
}
