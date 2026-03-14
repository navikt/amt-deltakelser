package no.nav.tiltaksarrangor.repositories

import no.nav.tiltaksarrangor.consumer.model.EndringsmeldingType
import no.nav.tiltaksarrangor.consumer.model.Innhold
import no.nav.tiltaksarrangor.model.Endringsmelding
import no.nav.tiltaksarrangor.repositories.model.EndringsmeldingDbo
import no.nav.tiltaksarrangor.utils.objectMapper
import no.nav.tiltaksarrangor.utils.sqlParameters
import org.postgresql.util.PGobject
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.readValue
import java.util.UUID

@Component
class EndringsmeldingRepository(
	private val template: NamedParameterJdbcTemplate,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	private val endringsmeldingRowMapper =
		RowMapper { rs, _ ->
			val type = EndringsmeldingType.valueOf(rs.getString("type"))
			EndringsmeldingDbo(
				id = UUID.fromString(rs.getString("id")),
				deltakerId = UUID.fromString(rs.getString("deltaker_id")),
				type = type,
				innhold = parseInnholdJson(rs.getString("innhold"), type),
				status = Endringsmelding.Status.valueOf(rs.getString("status")),
				sendt = rs.getTimestamp("sendt").toLocalDateTime(),
			)
		}

	fun insertOrUpdateEndringsmelding(endringsmeldingDbo: EndringsmeldingDbo) {
		val sql =
			"""
			INSERT INTO endringsmelding(id, deltaker_id, type, innhold, status, sendt)
			VALUES (:id,
					:deltaker_id,
					:type,
					:innhold,
					:status,
					:sendt)
			ON CONFLICT (id) DO UPDATE SET
					deltaker_id     	= :deltaker_id,
					type				= :type,
					innhold 			= :innhold,
					status				= :status,
					sendt				= :sendt
			""".trimIndent()

		template.update(
			sql,
			sqlParameters(
				"id" to endringsmeldingDbo.id,
				"deltaker_id" to endringsmeldingDbo.deltakerId,
				"type" to endringsmeldingDbo.type.name,
				"innhold" to endringsmeldingDbo.innhold?.toPGObject(),
				"status" to endringsmeldingDbo.status.name,
				"sendt" to endringsmeldingDbo.sendt,
			),
		)
	}

	fun deleteEndringsmelding(endringsmeldingId: UUID): Int = template.update(
		"DELETE FROM endringsmelding WHERE id = :id",
		sqlParameters("id" to endringsmeldingId),
	)

	fun getEndringsmelding(endringsmeldingId: UUID): EndringsmeldingDbo? = template
		.query(
			"SELECT * FROM endringsmelding WHERE id = :id",
			sqlParameters("id" to endringsmeldingId),
			endringsmeldingRowMapper,
		).firstOrNull()

	fun getEndringsmeldingerForDeltakere(deltakerIder: List<UUID>): List<EndringsmeldingDbo> {
		if (deltakerIder.isEmpty()) {
			return emptyList()
		}
		return template.query(
			"SELECT * FROM endringsmelding WHERE deltaker_id in(:ids) AND status = 'AKTIV'",
			sqlParameters("ids" to deltakerIder),
			endringsmeldingRowMapper,
		)
	}

	fun getEndringsmeldingerForDeltaker(deltakerId: UUID): List<EndringsmeldingDbo> = template.query(
		"SELECT * FROM endringsmelding WHERE deltaker_id = :deltaker_id",
		sqlParameters("deltaker_id" to deltakerId),
		endringsmeldingRowMapper,
	)

	private fun parseInnholdJson(innholdJson: String?, type: EndringsmeldingType): Innhold {
		if (innholdJson == null) {
			log.error("Kan ikke lese endringsmelding med type $type som mangler innhold")
			throw IllegalStateException("Endringsmelding med type $type må ha innhold")
		}

		return when (type) {
			EndringsmeldingType.LEGG_TIL_OPPSTARTSDATO -> objectMapper.readValue<Innhold.LeggTilOppstartsdatoInnhold>(innholdJson)
			EndringsmeldingType.ENDRE_OPPSTARTSDATO -> objectMapper.readValue<Innhold.EndreOppstartsdatoInnhold>(innholdJson)
			EndringsmeldingType.FORLENG_DELTAKELSE -> objectMapper.readValue<Innhold.ForlengDeltakelseInnhold>(innholdJson)
			EndringsmeldingType.AVSLUTT_DELTAKELSE -> objectMapper.readValue<Innhold.AvsluttDeltakelseInnhold>(innholdJson)
			EndringsmeldingType.DELTAKER_IKKE_AKTUELL -> objectMapper.readValue<Innhold.DeltakerIkkeAktuellInnhold>(innholdJson)
			EndringsmeldingType.ENDRE_DELTAKELSE_PROSENT -> objectMapper.readValue<Innhold.EndreDeltakelseProsentInnhold>(innholdJson)
			EndringsmeldingType.ENDRE_SLUTTDATO -> objectMapper.readValue<Innhold.EndreSluttdatoInnhold>(innholdJson)
			EndringsmeldingType.ENDRE_SLUTTAARSAK -> objectMapper.readValue<Innhold.EndreSluttaarsakInnhold>(innholdJson)
		}
	}
}

fun Innhold.toPGObject() = PGobject().also {
	it.type = "json"
	it.value = objectMapper.writeValueAsString(this)
}
