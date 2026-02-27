package no.nav.amt.deltaker.bff.deltaker.forslag

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.bff.db.toPGObject
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import java.util.UUID

class ForslagRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getForDeltaker(deltakerId: UUID): List<Forslag> {
        val query = queryOf(
            """
            SELECT 
                id,
                deltaker_id,
                arrangoransatt_id,
                opprettet,
                begrunnelse,
                endring,
                status
            FROM forslag 
            WHERE deltaker_id = :deltaker_id
            """.trimIndent(),
            mapOf("deltaker_id" to deltakerId),
        )

        return Database.query { session ->
            session.run(query.map(::rowMapper).asList)
        }
    }

    fun getForDeltakere(deltakerIder: List<UUID>): List<Forslag> {
        if (deltakerIder.isEmpty()) return emptyList()

        val query = queryOf(
            """
            SELECT 
                id,
                deltaker_id,
                arrangoransatt_id,
                opprettet,
                begrunnelse,
                endring,
                status
            FROM forslag 
            WHERE deltaker_id = ANY(:deltaker_ider::uuid[])
            """.trimIndent(),
            mapOf("deltaker_ider" to deltakerIder.toTypedArray()),
        )

        return Database.query { session ->
            session.run(query.map(::rowMapper).asList)
        }
    }

    fun get(id: UUID): Result<Forslag> = runCatching {
        val query = queryOf(
            """
            SELECT 
                id,
                deltaker_id,
                arrangoransatt_id,
                opprettet,
                begrunnelse,
                endring,
                status
            FROM forslag 
            WHERE id = :id
            """.trimIndent(),
            mapOf("id" to id),
        ).map(::rowMapper).asSingle

        Database.query { session ->
            session.run(query) ?: throw NoSuchElementException("Ingen forslag med id $id")
        }
    }

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

    companion object {
        private fun rowMapper(row: Row): Forslag = Forslag(
            id = row.uuid("id"),
            deltakerId = row.uuid("deltaker_id"),
            opprettetAvArrangorAnsattId = row.uuid("arrangoransatt_id"),
            opprettet = row.localDateTime("opprettet"),
            begrunnelse = row.stringOrNull("begrunnelse"),
            endring = objectMapper.readValue(row.string("endring")),
            status = objectMapper.readValue(row.string("status")),
        )
    }
}
