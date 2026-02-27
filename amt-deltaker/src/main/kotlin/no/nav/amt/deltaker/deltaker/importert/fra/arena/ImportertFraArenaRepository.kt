package no.nav.amt.deltaker.deltaker.importert.fra.arena

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class ImportertFraArenaRepository {
    fun upsert(importertFraArena: ImportertFraArena) {
        val sql =
            """
            INSERT INTO importert_fra_arena (
                deltaker_id, 
                importert_dato, 
                deltaker_ved_import
            )
            VALUES (
                :deltaker_id,
                :importert_dato,
                :deltaker_ved_import
            )
            ON CONFLICT (deltaker_id) DO UPDATE SET
                importert_dato      = :importert_dato, 
                deltaker_ved_import = :deltaker_ved_import
            """.trimIndent()

        Database.query { session ->
            session.update(
                queryOf(
                    sql,
                    mapOf(
                        "deltaker_id" to importertFraArena.deltakerId,
                        "importert_dato" to importertFraArena.importertDato,
                        "deltaker_ved_import" to toPGObject(importertFraArena.deltakerVedImport),
                    ),
                ),
            )
        }
    }

    fun getForDeltaker(deltakerId: UUID): ImportertFraArena? = Database.query { session ->
        val sql =
            """
            SELECT
                deltaker_id,
                importert_dato,
                deltaker_ved_import
            FROM importert_fra_arena
            WHERE deltaker_id = :deltaker_id
            """.trimIndent()

        session.run(
            queryOf(
                sql,
                mapOf("deltaker_id" to deltakerId),
            ).map(::rowMapper).asSingle,
        )
    }

    fun deleteForDeltaker(deltakerId: UUID) = Database.query { session ->
        session.update(
            queryOf(
                "DELETE FROM importert_fra_arena WHERE deltaker_id = :deltaker_id",
                mapOf("deltaker_id" to deltakerId),
            ),
        )
    }

    companion object {
        private fun rowMapper(row: Row) = ImportertFraArena(
            deltakerId = row.uuid("deltaker_id"),
            importertDato = row.localDateTime("importert_dato"),
            deltakerVedImport = objectMapper.readValue(row.string("deltaker_ved_import")),
        )
    }
}
