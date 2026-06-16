package no.nav.amt.deltaker.repository

import kotliquery.queryOf
import no.nav.amt.lib.models.deltakerliste.Prisinformasjon
import no.nav.amt.lib.models.deltakerliste.Priskomponent
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

object PrisinfoBelopRepository {
    fun lagrePrisinfoBelop(
        gjennomforingId: UUID,
        belop: Set<Priskomponent>,
    ) {
        if (belop.isEmpty()) return

        val insertSql =
            """
            INSERT INTO deltakerliste_prisinfo_belop (
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

        val params = belop.map {
            mapOf(
                "deltakerliste_id" to gjennomforingId,
                "pristype" to it.pristype.name,
                "pris" to it.pris,
            )
        }

        Database.query { session ->
            session.batchPreparedNamedStatement(insertSql, params)
        }
    }

    fun hentPrisinfoBelop(gjennomforingId: UUID): Set<Priskomponent> {
        val sql =
            """
            SELECT 
                pristype, 
                pris 
            FROM deltakerliste_prisinfo_belop 
            WHERE deltakerliste_id = ?
            """.trimIndent()

        return Database
            .query { session ->
                session.run(
                    queryOf(sql, gjennomforingId)
                        .map { row ->
                            Priskomponent(
                                pristype = Prisinformasjon.Tilskudd.Tilskuddstype.valueOf(row.string("pristype")),
                                pris = row.int("pris"),
                            )
                        }.asList,
                )
            }.toSet()
    }

    fun deleteForGjennomforing(gjennomforingId: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM deltakerliste_prisinfo_belop WHERE deltakerliste_id = ?",
                    gjennomforingId,
                ),
            )
        }
    }
}
