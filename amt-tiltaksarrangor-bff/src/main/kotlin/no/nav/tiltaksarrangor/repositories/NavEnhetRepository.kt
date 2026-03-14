package no.nav.tiltaksarrangor.repositories

import no.nav.tiltaksarrangor.consumer.model.NavEnhet
import no.nav.tiltaksarrangor.repositories.model.NavEnhetDbo
import no.nav.tiltaksarrangor.utils.sqlParameters
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class NavEnhetRepository(
	private val template: NamedParameterJdbcTemplate,
) {
	private val rowMapper = RowMapper { rs, _ ->
		NavEnhetDbo(
			id = UUID.fromString(rs.getString("id")),
			enhetsnummer = rs.getString("nav_enhet_nummer"),
			navn = rs.getString("navn"),
			sistEndret = rs.getTimestamp("modified_at").toLocalDateTime(),
		)
	}

	fun upsert(enhet: NavEnhet) {
		val sql =
			"""
			insert into nav_enhet (id, nav_enhet_nummer, navn)
			values (:id,
					:nav_enhet_nummer,
					:navn)
			on conflict (id) do update set
				navn = :navn,
				nav_enhet_nummer = :nav_enhet_nummer,
				modified_at = current_timestamp
			""".trimIndent()
		val params = sqlParameters(
			"id" to enhet.id,
			"nav_enhet_nummer" to enhet.enhetsnummer,
			"navn" to enhet.navn,
		)

		template.update(sql, params)
	}

	fun get(id: UUID): NavEnhetDbo? {
		val sql =
			"""
			select * from nav_enhet where id = :id
			""".trimIndent()
		val params = sqlParameters("id" to id)

		return template.query(sql, params, rowMapper).firstOrNull()
	}

	fun getMany(enhetIder: List<UUID>): List<NavEnhetDbo> {
		if (enhetIder.isEmpty()) return emptyList()

		val sql =
			"""
			SELECT id, nav_enhet_nummer, navn, modified_at
			FROM nav_enhet
			WHERE id IN (:enhetIder)
			""".trimIndent()

		val parameters = MapSqlParameterSource().addValues(
			mapOf("enhetIder" to enhetIder),
		)

		return template.query(sql, parameters, rowMapper)
	}
}
