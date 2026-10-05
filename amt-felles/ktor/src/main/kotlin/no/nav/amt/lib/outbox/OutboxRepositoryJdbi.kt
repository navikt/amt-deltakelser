package no.nav.amt.lib.outbox

import no.nav.amt.lib.utils.database.jdbi.Repository
import no.nav.amt.lib.utils.objectMapper
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.sql.ResultSet

@RegisterRowMapper(OutboxRecordMapper::class)
interface OutboxRepositoryJdbi : Repository {
    @SqlQuery(
        """
        INSERT INTO outbox_record (
            key,
            value,
            value_type,
            topic,
            status,
            retry_count
        )
        VALUES (
            :key,
            :value,
            :value_type,
            :topic,
            :status,
            :retry_count
        )
        RETURNING *
        """,
    )
    fun insertNewRecord(
        @Bind("key") key: String,
        @Bind("value") value: org.postgresql.util.PGobject,
        @Bind("value_type") valueType: String,
        @Bind("topic") topic: String,
        @Bind("status") status: String = OutboxRecordStatus.PENDING.name,
        @Bind("retry_count") retryCount: Int = 0,
    ): OutboxRecord
}

class OutboxRecordMapper : RowMapper<OutboxRecord> {
    override fun map(
        rs: ResultSet,
        ctx: StatementContext,
    ): OutboxRecord = OutboxRecord(
        id = OutboxRecordId(rs.getLong("id")),
        key = rs.getString("key"),
        value = objectMapper.readTree(rs.getString("value")),
        valueType = rs.getString("value_type"),
        topic = rs.getString("topic"),
        createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
        processedAt = rs.getTimestamp("processed_at")?.toLocalDateTime(),
        status = OutboxRecordStatus.valueOf(rs.getString("status")),
        retryCount = rs.getInt("retry_count"),
        retriedAt = rs.getTimestamp("retried_at")?.toLocalDateTime(),
        errorMessage = rs.getString("error_message")?.takeUnless { it.isBlank() },
    )
}
