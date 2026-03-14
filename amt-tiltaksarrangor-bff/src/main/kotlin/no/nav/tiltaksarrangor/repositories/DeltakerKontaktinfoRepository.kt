package no.nav.tiltaksarrangor.repositories

import no.nav.amt.lib.models.deltaker.Kontaktinformasjon
import no.nav.tiltaksarrangor.utils.sqlParameters
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

@Component
class DeltakerKontaktinfoRepository(
	private val template: NamedParameterJdbcTemplate,
) {
	fun getPersonerForOppdatering(hour: Int): List<String> = template.query(
		"""
		SELECT distinct personident FROM deltaker
		WHERE MOD(personident::bigint, 24) = :hour;
		""",
		sqlParameters("hour" to hour),
	) { rs, _ -> rs.getString("personident") }

	fun oppdaterKontaktinformasjon(kontakinfo: Map<String, Kontaktinformasjon>) {
		val sql =
			"""
			UPDATE deltaker
			SET
				epost = :epost,
				telefonnummer = :telefonnummer
			WHERE personident = :personident
			""".trimIndent()

		template.batchUpdate(
			sql,
			kontakinfo
				.map { (personident, info) ->
					sqlParameters(
						"personident" to personident,
						"epost" to info.epost,
						"telefonnummer" to info.telefonnummer,
					)
				}.toTypedArray(),
		)
	}
}
