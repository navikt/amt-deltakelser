package no.nav.amt.deltaker.repository

import kotliquery.queryOf
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

object KodeverkValgRepository {
    fun lagreKodeverkValg(
        deltakerlisteId: UUID,
        valg: List<UUID>,
    ) {
        val sql =
            """
            INSERT INTO deltakerliste_kodeverk_valg (deltakerliste_id, kodeverk_valg)
            VALUES (:deltakerliste_id, :kodeverk_valg)
            ON CONFLICT (deltakerliste_id) DO UPDATE SET kodeverk_valg = :kodeverk_valg
            """.trimIndent()

        Database.query { session ->
            session.update(
                queryOf(
                    sql,
                    mapOf(
                        "deltakerliste_id" to deltakerlisteId,
                        "kodeverk_valg" to session.createArrayOf("uuid", valg),
                    ),
                ),
            )
        }
    }

    fun hentKodeverkValg(deltakerlisteId: UUID): List<UUID> {
        val sql =
            """
            SELECT kodeverk_valg 
            FROM deltakerliste_kodeverk_valg 
            WHERE deltakerliste_id = :deltakerliste_id
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(sql, mapOf("deltakerliste_id" to deltakerlisteId))
                    .map { row ->
                        val array = row.anyOrNull("kodeverk_valg") as? java.sql.Array
                        val uuids = array?.array as? Array<*>
                        uuids?.filterIsInstance<UUID>() ?: emptyList()
                    }.asSingle,
            ) ?: emptyList()
        }
    }
}
