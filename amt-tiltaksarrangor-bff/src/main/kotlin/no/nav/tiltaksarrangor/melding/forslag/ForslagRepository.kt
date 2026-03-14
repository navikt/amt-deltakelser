package no.nav.tiltaksarrangor.melding.forslag

import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.tiltaksarrangor.utils.objectMapper
import no.nav.tiltaksarrangor.utils.sqlParameters
import org.postgresql.util.PGobject
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.readValue
import java.util.UUID

@Component
class ForslagRepository(
	private val template: NamedParameterJdbcTemplate,
) {
	private val rowMapper = RowMapper { rs, _ ->
		Forslag(
			id = UUID.fromString(rs.getString("id")),
			deltakerId = UUID.fromString(rs.getString("deltaker_id")),
			opprettetAvArrangorAnsattId = UUID.fromString(rs.getString("opprettet_av_arrangor_ansatt_id")),
			begrunnelse = rs.getString("begrunnelse"),
			endring = objectMapper.readValue(rs.getString("endring")),
			status = objectMapper.readValue(rs.getString("status")),
			opprettet = rs.getTimestamp("created_at").toLocalDateTime(),
		)
	}

	fun upsert(forslag: Forslag): Forslag {
		val sql =
			"""
			insert into forslag (
				 id,
				 deltaker_id,
				 opprettet_av_arrangor_ansatt_id,
				 begrunnelse,
				 endring,
				 status,
				 created_at
			)
			values (
				:id,
				:deltaker_id,
				:opprettet_av_arrangor_ansatt_id,
				:begrunnelse,
				:endring,
				:status,
				:created_at
			) on conflict (id) do update set
				status = :status,
				modified_at = current_timestamp
			returning *
			""".trimIndent()
		val params = sqlParameters(
			"id" to forslag.id,
			"deltaker_id" to forslag.deltakerId,
			"opprettet_av_arrangor_ansatt_id" to forslag.opprettetAvArrangorAnsattId,
			"begrunnelse" to forslag.begrunnelse,
			"endring" to toPGObject(forslag.endring),
			"status" to toPGObject(forslag.status),
			"created_at" to forslag.opprettet,
		)
		return template.queryForObject(sql, params, rowMapper)
	}

	fun get(id: UUID): Result<Forslag> {
		val sql = "select * from forslag where id = :id"
		val params = sqlParameters("id" to id)

		return template
			.query(sql, params, rowMapper)
			.firstOrNull()
			?.let { Result.success(it) }
			?: Result.failure(NoSuchElementException("Fant ikke forslag med id $id"))
	}

	fun getForDeltaker(deltakerId: UUID): List<Forslag> {
		val sql = "select * from forslag where deltaker_id = :deltaker_id"
		val params = sqlParameters("deltaker_id" to deltakerId)
		return template.query(sql, params, rowMapper)
	}

	fun getAktiveForslagForDeltakere(deltakerIder: List<UUID>): List<Forslag> {
		if (deltakerIder.isEmpty()) {
			return emptyList()
		}

		val sql = "select * from forslag where deltaker_id in(:ids)"
		val params = sqlParameters("ids" to deltakerIder)
		return template
			.query(sql, params, rowMapper)
			.filter { it.status is Forslag.Status.VenterPaSvar }
	}

	fun delete(id: UUID): Int {
		val sql = "delete from forslag where id = :id"
		val params = sqlParameters("id" to id)
		return template.update(sql, params)
	}

	fun getVentende(forslag: Forslag): Result<Forslag> {
		val sql =
			"""
			select *
			from forslag
			where deltaker_id = :deltaker_id
				and status->>'type' = :status
				and endring->>'type' = :type
			""".trimIndent()

		val params = sqlParameters(
			"deltaker_id" to forslag.deltakerId,
			"type" to forslag.endring.javaClass.simpleName,
			"status" to Forslag.Status.VenterPaSvar.javaClass.simpleName,
		)
		return template
			.query(sql, params, rowMapper)
			.firstOrNull()
			?.let { Result.success(it) }
			?: Result.failure(
				NoSuchElementException(
					"Fant ikke forslag for deltaker ${forslag.deltakerId} " +
						"av type ${forslag.endring.javaClass.simpleName} " +
						"og status ${Forslag.Status.VenterPaSvar.javaClass.simpleName}",
				),
			)
	}
}

fun toPGObject(value: Any?) = PGobject().also {
	it.type = "json"
	it.value = value?.let { v -> objectMapper.writeValueAsString(v) }
}
