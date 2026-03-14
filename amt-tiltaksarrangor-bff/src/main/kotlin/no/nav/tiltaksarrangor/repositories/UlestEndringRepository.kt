package no.nav.tiltaksarrangor.repositories

import no.nav.tiltaksarrangor.melding.forslag.toPGObject
import no.nav.tiltaksarrangor.model.Oppdatering
import no.nav.tiltaksarrangor.model.UlestEndring
import no.nav.tiltaksarrangor.utils.objectMapper
import no.nav.tiltaksarrangor.utils.sqlParameters
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.readValue
import java.util.UUID

@Component
class UlestEndringRepository(
	private val template: NamedParameterJdbcTemplate,
) {
	private val rowMapper = RowMapper { rs, _ ->
		UlestEndring(
			id = UUID.fromString(rs.getString("id")),
			deltakerId = UUID.fromString(rs.getString("deltaker_id")),
			oppdatering = objectMapper.readValue(rs.getString("oppdatering")),
			oppdatert = rs.getDate("modified_at").toLocalDate(),
		)
	}

	fun insert(deltakerId: UUID, oppdatering: Oppdatering): UlestEndring {
		val sql =
			"""
			insert into ulest_endring
				(id,
				deltaker_id,
				oppdatering)
			values (
			 	:id,
				:deltakerId,
			 	:oppdatering)
			on conflict (id) do update set
				oppdatering = :oppdatering
			returning *
			""".trimIndent()
		val params = sqlParameters(
			"id" to oppdatering.id,
			"deltakerId" to deltakerId,
			"oppdatering" to toPGObject(oppdatering),
		)

		return template.queryForObject(sql, params, rowMapper)
	}

	fun getMany(deltakerId: UUID): List<UlestEndring> {
		val sql = "select * from ulest_endring where deltaker_id = :deltakerId"
		val params = sqlParameters("deltakerId" to deltakerId)
		return template.query(sql, params, rowMapper)
	}

	fun delete(id: UUID): Int {
		val sql = "delete from ulest_endring where id = :id"
		val params = sqlParameters("id" to id)
		return template.update(sql, params)
	}

	fun getUlesteForslagForDeltakere(deltakerIder: List<UUID>): List<UlestEndring> {
		if (deltakerIder.isEmpty()) {
			return emptyList()
		}

		val sql = "select * from ulest_endring where deltaker_id in(:ids)"
		val params = sqlParameters("ids" to deltakerIder)
		return template.query(sql, params, rowMapper)
	}
}
