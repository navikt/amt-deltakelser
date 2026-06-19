package no.nav.amt.deltaker.repository

import kotliquery.queryOf
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

@Deprecated("Denne tabellen og tilhørende repository er ikke i bruk, og bør fjernes")
object KodeverkValgRepository {
    fun hentGjennomforingerSomSkalMigreresTilNyTabell(): List<UUID> {
        val sql =
            """
            SELECT DISTINCT deltakerliste_kodeverk_valg.deltakerliste_id 
            FROM 
                deltakerliste_kodeverk_valg dkv
                LEFT JOIN opplaering_kategorisering_valg okv ON 
                    dkv.deltakerliste_id = okv.deltakerliste_id
                WHERE okv.deltakerliste_id IS NULL
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(sql)
                    .map { row -> row.uuid("deltakerliste_id") }
                    .asList,
            )
        }
    }

    fun hentKodeverkValg(deltakerlisteId: UUID): Set<UUID> {
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
                        uuids?.filterIsInstance<UUID>()?.toSet() ?: emptySet()
                    }.asSingle,
            ) ?: emptySet()
        }
    }
}
