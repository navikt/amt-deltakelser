package no.nav.amt.lib.utils.database

import kotliquery.queryOf
import org.slf4j.Logger
import java.sql.SQLException
import java.time.Instant
import java.time.ZoneId

fun logTimezoneDiagnostics(logger: Logger) {
    val jvmZone = ZoneId.systemDefault()
    logger.info(
        "timezone-diagnostics jvmZone={} jvmOffset={} envTZ={} javaVersion={}",
        jvmZone,
        jvmZone.rules.getOffset(Instant.now()),
        System.getenv("TZ") ?: "<unset>",
        Runtime.version(),
    )

    try {
        Database.query { session ->
            val settings = session.run(
                queryOf("SELECT setting, source FROM pg_settings WHERE name = 'TimeZone'")
                    .map { row -> row.string("setting") to row.string("source") }
                    .asSingle,
            ) ?: error("timezone-diagnostics: PostgreSQL TimeZone setting was not returned")

            logger.info("timezone-diagnostics databaseZone={} databaseZoneSource={}", settings.first, settings.second)

            session.forEach(
                queryOf(
                    """
                    SELECT sample, probe_instant
                    FROM (VALUES
                        ('winter', TIMESTAMPTZ '2026-11-02 22:30:00+00'),
                        ('summer', TIMESTAMPTZ '2026-07-02 21:30:00+00')
                    ) AS samples(sample, probe_instant)
                    """.trimIndent(),
                ),
            ) { row ->
                val sample = row.string("sample")
                val instant = row.instant("probe_instant")
                val mappedLocalDateTime = row.localDateTime("probe_instant")
                val expectedOsloDateTime = instant.atZone(ZoneId.of("Europe/Oslo")).toLocalDateTime()
                val matchesOslo = mappedLocalDateTime == expectedOsloDateTime

                logger.info(
                    "timezone-diagnostics sample={} instant={} mappedLocalDateTime={} expectedOsloDateTime={} " +
                        "mappedDate={} expectedOsloDate={} matchesOslo={}",
                    sample,
                    instant,
                    mappedLocalDateTime,
                    expectedOsloDateTime,
                    mappedLocalDateTime.toLocalDate(),
                    expectedOsloDateTime.toLocalDate(),
                    matchesOslo,
                )

                if (!matchesOslo) {
                    logger.warn("timezone-diagnostics sample={} JDBC LocalDateTime mapping differs from Europe/Oslo", sample)
                }
            }
        }
    } catch (exception: SQLException) {
        logger.warn(
            "timezone-diagnostics database probe failed sqlState={} errorCode={}",
            exception.sqlState,
            exception.errorCode,
        )
    }
}
