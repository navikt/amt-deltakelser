package no.nav.amt.deltaker.deltaker.forslag

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.utils.prefixColumn
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class ForslagRepository {
    fun getForDeltaker(deltakerId: UUID): List<Forslag> {
        val sql =
            """
            SELECT 
                f.id AS "f.id",
                f.deltaker_id AS "f.deltaker_id",
                f.arrangoransatt_id AS "f.arrangoransatt_id",
                f.opprettet AS "f.opprettet",
                f.begrunnelse AS "f.begrunnelse",
                f.endring AS "f.endring",
                f.status AS "f.status"
            FROM forslag f 
            WHERE f.deltaker_id = :deltaker_id
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(
                    sql,
                    mapOf("deltaker_id" to deltakerId),
                ).map(::rowMapper).asList,
            )
        }
    }

    fun get(id: UUID): Result<Forslag> = runCatching {
        val sql =
            """
            SELECT 
                f.id AS "f.id",
                f.deltaker_id AS "f.deltaker_id",
                f.arrangoransatt_id AS "f.arrangoransatt_id",
                f.opprettet AS "f.opprettet",
                f.begrunnelse AS "f.begrunnelse",
                f.endring AS "f.endring",
                f.status AS "f.status"
            FROM forslag f 
            WHERE f.id = :id
            """.trimIndent()

        Database.query { session ->
            session.run(
                queryOf(
                    sql,
                    mapOf("id" to id),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Ingen forslag med id $id")
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
                status)
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

    fun delete(id: UUID) = Database.query { session ->
        session.update(
            queryOf(
                "DELETE FROM forslag WHERE id = :id",
                mapOf("id" to id),
            ),
        )
    }

    fun deleteForDeltaker(deltakerId: UUID) = Database.query { session ->
        session.update(
            queryOf(
                "DELETE FROM forslag WHERE deltaker_id = :deltaker_id",
                mapOf("deltaker_id" to deltakerId),
            ),
        )
    }

    fun kanLagres(deltakerId: UUID): Boolean = Database.query { session ->
        session.run(
            queryOf(
                "SELECT id FROM deltaker WHERE id = :id",
                mapOf("id" to deltakerId),
            ).map { row -> row.uuid("id") }.asSingle,
        ) != null
    }

    companion object {
        private val col = prefixColumn("f")

        fun rowMapper(row: Row): Forslag = Forslag(
            id = row.uuid(col("id")),
            deltakerId = row.uuid(col("deltaker_id")),
            opprettetAvArrangorAnsattId = row.uuid(col("arrangoransatt_id")),
            opprettet = row.localDateTime(col("opprettet")),
            begrunnelse = row.stringOrNull(col("begrunnelse")),
            endring = objectMapper.readValue(row.string(col("endring"))),
            status = objectMapper.readValue(row.string(col("status"))),
        )
    }
}
