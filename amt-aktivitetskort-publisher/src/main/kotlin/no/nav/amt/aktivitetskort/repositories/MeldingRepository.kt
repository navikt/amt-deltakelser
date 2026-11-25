package no.nav.amt.aktivitetskort.repositories

import no.nav.amt.aktivitetskort.domain.Melding
import no.nav.amt.aktivitetskort.utils.getZonedDateTime
import no.nav.amt.aktivitetskort.utils.sqlParameters
import no.nav.amt.lib.utils.toPGObject
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

@Repository
class MeldingRepository(
    private val template: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val rowMapper = RowMapper { rs, _ ->
        Melding(
            id = UUID.fromString(rs.getString("id")),
            deltakerId = UUID.fromString(rs.getString("deltaker_id")),
            deltakerlisteId = UUID.fromString(rs.getString("deltakerliste_id")),
            arrangorId = UUID.fromString(rs.getString("arrangor_id")),
            aktivitetskort = objectMapper.readValue(rs.getString("melding")),
            oppfolgingperiode = rs.getString("oppfolgingsperiode")?.let { UUID.fromString(it) },
            createdAt = rs.getZonedDateTime("created_at"),
            modifiedAt = rs.getZonedDateTime("modified_at"),
        )
    }

    @Transactional
    fun upsert(
        melding: Melding,
        medlemskapLaasNodvendig: Boolean = true,
    ) {
        if (medlemskapLaasNodvendig) {
            advisoryLockMedlemskap("arrangor", melding.arrangorId, delt = true)
            advisoryLockMedlemskap("deltakerliste", melding.deltakerlisteId, delt = true)
        }
        advisoryLockMedlemshistorikk(melding.deltakerId)
        template.update(
            """
            INSERT INTO melding(id, deltaker_id, deltakerliste_id, arrangor_id, melding, oppfolgingsperiode)
            VALUES (:id,
            		:deltaker_id,
            		:deltakerliste_id,
            		:arrangor_id,
            		:melding,
            		:oppfolgingsperiode)
            ON CONFLICT (id) DO UPDATE SET melding = :melding,
            										deltakerliste_id = :deltakerliste_id,
            										arrangor_id      = :arrangor_id,
            										oppfolgingsperiode = :oppfolgingsperiode,
            										modified_at      = current_timestamp
            """.trimIndent(),
            sqlParameters(
                "id" to melding.aktivitetskort.id,
                "deltaker_id" to melding.deltakerId,
                "deltakerliste_id" to melding.deltakerlisteId,
                "arrangor_id" to melding.arrangorId,
                "oppfolgingsperiode" to melding.oppfolgingperiode,
                "melding" to objectMapper.toPGObject(melding.aktivitetskort),
            ),
        )
    }

    // To forespørsler kan begge tro at deltakeren mangler kort og lage hvert sitt første kort.
    // Derfor låser vi deltakeren, ikke et bestemt kort. Låsen virker også før det første kortet finnes.
    // MANDATORY krever en eksisterende transaksjon, så låsen ikke slippes
    // før kalleren har validert historikken og fullført eller angret lagringen.
    @Transactional(propagation = Propagation.MANDATORY)
    fun advisoryLockMedlemshistorikk(deltakerId: UUID) {
        advisoryLock("meldingshistorikk:$deltakerId")
    }

    // MANDATORY krever at medlemskapslåsen tas i bulkoppdateringens transaksjon.
    // En egen transaksjon ville sluppet låsen før utvalget valideres og kortene lagres.
    @Transactional(propagation = Propagation.MANDATORY)
    fun advisoryLockMedlemskap(
        type: String,
        id: UUID,
        delt: Boolean = false,
    ) {
        advisoryLock("medlemskap:$type:$id", delt)
    }

    private fun advisoryLock(
        noekkel: String,
        delt: Boolean = false,
    ) {
        val funksjon = if (delt) "pg_advisory_xact_lock_shared" else "pg_advisory_xact_lock"
        template.query(
            "SELECT $funksjon(hashtextextended(:noekkel, 0))",
            sqlParameters("noekkel" to "aktivitetskort:$noekkel"),
        ) { _ -> }
    }

    fun getByDeltakerId(deltakerId: UUID): List<Melding> = template.query(
        "SELECT * FROM melding where deltaker_id = :deltaker_id order by created_at desc",
        sqlParameters("deltaker_id" to deltakerId),
        rowMapper,
    )

    fun getByDeltakerlisteId(deltakerlisteId: UUID): List<Melding> = template.query(
        "SELECT DISTINCT ON (deltaker_id) * FROM melding where deltakerliste_id = :deltakerliste_id order by deltaker_id, created_at desc",
        sqlParameters("deltakerliste_id" to deltakerlisteId),
        rowMapper,
    )

    fun getByArrangorId(arrangorId: UUID): List<Melding> = template.query(
        "SELECT DISTINCT ON (deltaker_id) * FROM melding where arrangor_id = :arrangor_id order by deltaker_id, created_at desc",
        sqlParameters("arrangor_id" to arrangorId),
        rowMapper,
    )
}
