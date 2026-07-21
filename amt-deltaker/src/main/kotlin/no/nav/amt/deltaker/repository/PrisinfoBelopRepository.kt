package no.nav.amt.deltaker.repository

import kotliquery.queryOf
import no.nav.amt.deltaker.repository.dbo.Priskomponent
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

object PrisinfoBelopRepository {
    fun lagrePrisinfoBelop(
        prisinformasjonId: UUID,
        belop: Set<Priskomponent>,
    ) {
        if (belop.isEmpty()) return

        val insertSql =
            """
            INSERT INTO enkeltplass_prisinformasjon_belop (
                prisinfo_id,
                pristype,
                pris
            )
            VALUES (
                :prisinfo_id,
                :pristype,
                :pris
            )
            """.trimIndent()

        val params = belop.map {
            mapOf(
                "prisinfo_id" to prisinformasjonId,
                "pristype" to it.type.name,
                "pris" to it.pris,
            )
        }

        Database.query { session ->
            session.batchPreparedNamedStatement(insertSql, params)
        }
    }

    fun deleteForPrisinfo(prisinfoId: UUID): Int = Database.query { session ->
        session.update(
            queryOf(
                "DELETE FROM enkeltplass_prisinformasjon_belop WHERE prisinfo_id = ?",
                prisinfoId,
            ),
        )
    }

    fun hentPrisinfoBelop(prisinformasjonId: UUID): List<Priskomponent> {
        val sql =
            """
            SELECT 
                pristype, 
                pris 
            FROM enkeltplass_prisinformasjon_belop
            WHERE prisinfo_id = ?
            """.trimIndent()

        return Database
            .query { session ->
                session.run(
                    queryOf(sql, prisinformasjonId)
                        .map { row ->
                            Priskomponent(
                                type = Tilskuddstype.valueOf(row.string("pristype")),
                                pris = row.int("pris"),
                            )
                        }.asList,
                )
            }
    }
}
