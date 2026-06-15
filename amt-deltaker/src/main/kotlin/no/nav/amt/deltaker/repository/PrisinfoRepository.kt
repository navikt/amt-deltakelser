package no.nav.amt.deltaker.repository

import kotliquery.queryOf
import no.nav.amt.lib.models.deltakerliste.Priskomponent
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

object PrisinfoRepository {
    fun lagrePrisinfos(
        gjennomforingId: UUID,
        prisinfos: Set<Priskomponent>,
    ) {
        if (prisinfos.isEmpty()) return

        val insertSql =
            """
            INSERT INTO deltakerliste_prisinfo (
                deltakerliste_id,
                pristype,
                pris
            )
            VALUES (
                :deltakerliste_id,
                :pristype,
                :pris
            )
            """.trimIndent()

        val params = prisinfos.map {
            mapOf(
                "deltakerliste_id" to gjennomforingId,
                "pristype" to it.pristype.name,
                "pris" to it.pris.toInt(),
            )
        }

        if (params.isNotEmpty()) {
            Database.query { session ->
                session.batchPreparedNamedStatement(insertSql, params)
            }
        }
    }

    fun hentPrisinfos(gjennomforingId: UUID): Set<Priskomponent> {
        val sql =
            """
            SELECT 
                pristype, 
                pris 
            FROM deltakerliste_prisinfo 
            WHERE deltakerliste_id = ?
            """.trimIndent()

        return Database
            .query { session ->
                session.run(
                    queryOf(sql, gjennomforingId)
                        .map { row ->
                            Priskomponent(
                                pristype = Priskomponent.Pristype.valueOf(row.string("pristype")),
                                pris = row.int("pris").toUInt(),
                            )
                        }.asList,
                )
            }.toSet()
    }

    fun deleteForGjennomforing(gjennomforingId: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM deltakerliste_prisinfo WHERE deltakerliste_id = ?",
                    gjennomforingId,
                ),
            )
        }
    }
}
