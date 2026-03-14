package no.nav.tiltaksarrangor.utils

import org.postgresql.util.PGobject
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

fun <V> sqlParameters(vararg pairs: Pair<String, V>): MapSqlParameterSource = MapSqlParameterSource().addValues(pairs.toMap())

fun ResultSet.getNullableUUID(columnLabel: String): UUID? = this
	.getString(columnLabel)
	?.let { UUID.fromString(it) }

fun ResultSet.getNullableLocalDate(columnLabel: String): LocalDate? = this.getDate(columnLabel)?.toLocalDate()

fun ResultSet.getNullableLocalDateTime(columnLabel: String): LocalDateTime? = this.getTimestamp(columnLabel)?.toLocalDateTime()

fun ResultSet.getNullableFloat(columnLabel: String): Float? {
	val value = this.getFloat(columnLabel)
	if (this.wasNull()) return null
	return value
}

fun ResultSet.getNullableDouble(columnLabel: String): Double? {
	val value = this.getDouble(columnLabel)
	if (this.wasNull()) return null
	return value
}

inline fun <reified T> toPGObject(value: T?) = PGobject().also {
	it.type = "json"
	it.value = value?.let { v -> objectMapper.writePolymorphicListAsString(v) }
}
