package no.nav.tiltaksarrangor.repositories

import no.nav.tiltaksarrangor.consumer.model.NavAnsatt
import no.nav.tiltaksarrangor.utils.sqlParameters
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class NavAnsattRepository(
	private val template: NamedParameterJdbcTemplate,
) {
	private val rowMapper = RowMapper { rs, _ ->
		NavAnsatt(
			id = UUID.fromString(rs.getString("id")),
			navident = rs.getString("nav_ident"),
			navn = rs.getString("navn"),
			epost = rs.getString("epost"),
			telefon = rs.getString("telefon"),
		)
	}

	fun upsert(navAnsatt: NavAnsatt) {
		val sql =
			"""
			insert into nav_ansatt (id,
									nav_ident,
									navn,
									telefon,
									epost)
			values (:id,
					:nav_ident,
					:navn,
					:telefon,
					:epost)
			on conflict (id) do update set
				navn = :navn,
				telefon = :telefon,
				epost = :epost,
				modified_at = current_timestamp
			""".trimIndent()
		val params = sqlParameters(
			"id" to navAnsatt.id,
			"nav_ident" to navAnsatt.navident,
			"navn" to navAnsatt.navn,
			"telefon" to navAnsatt.telefon,
			"epost" to navAnsatt.epost,
		)

		template.update(sql, params)
	}

	fun get(id: UUID): NavAnsatt? {
		val sql =
			"""
			select * from nav_ansatt where id = :id
			""".trimIndent()

		val params = sqlParameters("id" to id)

		return template.query(sql, params, rowMapper).firstOrNull()
	}

	fun getMany(navAnsattIder: List<UUID>): List<NavAnsatt> {
		if (navAnsattIder.isEmpty()) return emptyList()

		val sql =
			"""
			SELECT id, nav_ident, navn, telefon, epost
			FROM nav_ansatt
			WHERE id IN (:navAnsattIder)
			""".trimIndent()

		val parameters = MapSqlParameterSource().addValues(
			mapOf("navAnsattIder" to navAnsattIder),
		)

		return template.query(sql, parameters, rowMapper)
	}
}
