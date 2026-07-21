package no.nav.amt.aktivitetskort.repositories

import no.nav.amt.aktivitetskort.kafka.consumer.dto.AktivitetskortFeilmelding
import no.nav.amt.aktivitetskort.utils.sqlParameters
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class FeilmeldingRepository(
    private val template: NamedParameterJdbcTemplate,
) {
    fun insert(feilmelding: AktivitetskortFeilmelding) {
        template.update(
            """
            INSERT INTO feilmelding(id, key, errorMessage, errorType, failingMessage, timestamp)
            VALUES (:id,
            		:key,
            		:errorMessage,
            		:errorType,
            		:failingMessage,
            		:timestamp)
            """.trimIndent(),
            sqlParameters(
                "id" to UUID.randomUUID(),
                "key" to feilmelding.key,
                "errorMessage" to feilmelding.errorMessage,
                "errorType" to feilmelding.errorType,
                "failingMessage" to feilmelding.failingMessage,
                "timestamp" to feilmelding.timestamp.toOffsetDateTime(),
            ),
        )
    }
}
