package no.nav.amt.deltaker.repository

import kotliquery.queryOf
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

object SertifiseringValgRepository {
    fun lagreSertifiseringValg(
        deltakerlisteId: UUID,
        sertifiseringValg: Set<SertifiseringValg>,
    ) {
        val insertSql =
            """
            INSERT INTO deltakerliste_sertifisering_valg (
                deltakerliste_id,
                sertifisering_id,
                sertifisering_navn
            )
            VALUES (
                :deltakerliste_id,
                :sertifisering_id,
                :sertifisering_navn
            )
            """.trimIndent()

        val params = sertifiseringValg.map {
            mapOf(
                "deltakerliste_id" to deltakerlisteId,
                "sertifisering_id" to it.id,
                "sertifisering_navn" to it.navn,
            )
        }

        if (params.isNotEmpty()) {
            Database.query { session ->
                session.batchPreparedNamedStatement(insertSql, params)
            }
        }
    }

    fun hentSertifiseringValg(deltakerlisteId: UUID): Set<SertifiseringValg> {
        val sql =
            """
            SELECT 
                sertifisering_id, 
                sertifisering_navn 
            FROM deltakerliste_sertifisering_valg 
            WHERE deltakerliste_id = ?
            """.trimIndent()

        return Database
            .query { session ->
                session.run(
                    queryOf(sql, deltakerlisteId)
                        .map { row ->
                            SertifiseringValg(
                                id = row.long("sertifisering_id"),
                                navn = row.string("sertifisering_navn"),
                            )
                        }.asList,
                )
            }.toSet()
    }

    fun deleteForGjennomforing(gjennomforingId: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM deltakerliste_sertifisering_valg WHERE deltakerliste_id = ?",
                    gjennomforingId,
                ),
            )
        }
    }
}
