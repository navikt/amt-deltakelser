package no.nav.amt.aktivitetskort.utils

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

fun <V> sqlParameters(vararg pairs: Pair<String, V>): MapSqlParameterSource = MapSqlParameterSource().addValues(pairs.toMap())

fun NamedParameterJdbcTemplate.advisoryLock(
    noekkel: String,
    delt: Boolean = false,
) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
        throw IllegalTransactionStateException("Advisory lock krever en aktiv transaksjon")
    }
    val funksjon = if (delt) "pg_advisory_xact_lock_shared" else "pg_advisory_xact_lock"
    query(
        "SELECT $funksjon(hashtextextended(:noekkel, 0))",
        sqlParameters("noekkel" to noekkel),
    ) { _ -> }
}

fun ResultSet.getNullableLocalDateTime(columnLabel: String): LocalDateTime? = this.getTimestamp(columnLabel)?.toLocalDateTime()

fun ResultSet.getNullableZonedDateTime(columnLabel: String): ZonedDateTime? = this.getTimestamp(columnLabel)?.let {
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.time), ZoneOffset.systemDefault())
}

fun ResultSet.getZonedDateTime(columnLabel: String): ZonedDateTime = getNullableZonedDateTime(columnLabel)
    ?: throw IllegalStateException("Expected $columnLabel not to be null")
