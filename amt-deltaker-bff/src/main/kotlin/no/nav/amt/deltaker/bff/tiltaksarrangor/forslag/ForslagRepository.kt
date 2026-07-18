package no.nav.amt.deltaker.bff.tiltaksarrangor.forslag

import kotliquery.queryOf
import no.nav.amt.deltaker.bff.db.toPGObject
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.util.UUID

class ForslagRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    fun upsert(forslag: Forslag) {
        val sql =
            """
            INSERT INTO forslag (
                id, 
                deltaker_id, 
                arrangoransatt_id, 
                opprettet, 
                begrunnelse, 
                endring,  
                status
            )
            VALUES (
                :id,
                :deltaker_id,
                :arrangoransatt_id,
                :opprettet,
                :begrunnelse,
                :endring,
                :status
            )
            ON CONFLICT (id) DO UPDATE SET
                deltaker_id     	= :deltaker_id,
                arrangoransatt_id	= :arrangoransatt_id,
                opprettet 			= :opprettet,
                begrunnelse			= :begrunnelse,
                endring				= :endring,
                status              = :status,
                modified_at         = CURRENT_TIMESTAMP
            """.trimIndent()

        val params = mapOf(
            "id" to forslag.id,
            "deltaker_id" to forslag.deltakerId,
            "arrangoransatt_id" to forslag.opprettetAvArrangorAnsattId,
            "opprettet" to forslag.opprettet,
            "begrunnelse" to forslag.begrunnelse,
            "endring" to toPGObject(forslag.endring),
            "status" to toPGObject(forslag.status),
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    fun delete(id: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM forslag WHERE id = :id",
                    mapOf("id" to id),
                ),
            )
        }
        log.info("Slettet godkjent forslag $id")
    }

    fun deleteForDeltaker(deltakerId: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM forslag WHERE deltaker_id = :deltaker_id",
                    mapOf("deltaker_id" to deltakerId),
                ),
            )
        }
    }

    fun kanLagres(deltakerId: UUID): Boolean = Database.query { session ->
        session.run(
            queryOf(
                "SELECT id FROM deltaker WHERE id = :id",
                mapOf("id" to deltakerId),
            ).map { row -> row.uuid("id") }.asSingle,
        )
    } != null
}
