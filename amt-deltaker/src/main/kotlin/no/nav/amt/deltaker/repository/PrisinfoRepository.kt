package no.nav.amt.deltaker.repository

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

object PrisinfoRepository {
    fun hentPrisinfo(
        gjennomforingId: UUID,
        okonomiGodkjent: Boolean,
    ): PrisinfoDbo? {
        val sql =
            """
            SELECT 
                id,
                deltakerliste_id,
                okonomi_godkjent,
                prisinformasjon_json_type,
                anskaffelse_pris,
                tilleggsopplysninger,
                ingenkostnader_aarsak
            FROM enkeltplass_prisinformasjon 
            WHERE 
                deltakerliste_id = ?
                AND okonomi_godkjent = ?
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(sql, gjennomforingId, okonomiGodkjent)
                    .map(::rowMapper)
                    .asSingle,
            )
        }
    }

    fun hentPrisinfos(gjennomforingId: UUID): List<PrisinfoDbo> {
        val sql =
            """
            SELECT 
                id,
                deltakerliste_id,
                okonomi_godkjent,
                prisinformasjon_json_type,
                anskaffelse_pris,
                tilleggsopplysninger,
                ingenkostnader_aarsak
            FROM enkeltplass_prisinformasjon 
            WHERE 
                deltakerliste_id = ?
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(sql, gjennomforingId)
                    .map(::rowMapper)
                    .asList,
            )
        }
    }

    fun deletePrisinfo(
        gjennomforingId: UUID,
        okonomiGodkjent: Boolean,
    ) = Database.query { session ->
        session.run(
            queryOf(
                "DELETE FROM enkeltplass_prisinformasjon WHERE deltakerliste_id = ? AND okonomi_godkjent = ?",
                gjennomforingId,
                okonomiGodkjent,
            ).asUpdate,
        )
    }

    fun insertPendingTotrinnskontrollPrisinfo(insertDbo: PrisinfoDbo): PrisinfoDbo {
        val sql =
            """
            INSERT INTO enkeltplass_prisinformasjon (
                id,
                deltakerliste_id,
                prisinformasjon_json_type,
                anskaffelse_pris,
                tilleggsopplysninger,
                ingenkostnader_aarsak
            )
            VALUES (
                :id,
                :deltakerliste_id,
                :prisinformasjon_json_type,
                :anskaffelse_pris,
                :tilleggsopplysninger,
                :ingenkostnader_aarsak
            )
            RETURNING *
            """.trimIndent()

        val params = mapOf(
            "id" to insertDbo.id,
            "deltakerliste_id" to insertDbo.gjennomforingId,
            "okonomi_godkjent" to insertDbo.okonomiGodkjent,
            "prisinformasjon_json_type" to insertDbo.prisinfoJsonSubtype,
            "anskaffelse_pris" to insertDbo.anskaffelsePris,
            "tilleggsopplysninger" to insertDbo.tilleggsopplysninger,
            "ingenkostnader_aarsak" to insertDbo.ingenkostnaderAarsak?.name,
        )

        return Database.query { session ->
            session.run(
                queryOf(sql, params)
                    .map(::rowMapper)
                    .asSingle,
            )
        } ?: error("Fant ikke oppdatert prisinfo")
    }

    fun settGodkjent(gjennomforingId: UUID) {
        Database.query { session ->
            session.run(
                queryOf(
                    """
                    UPDATE enkeltplass_prisinformasjon 
                    SET 
                        okonomi_godkjent = true,
                        modified_at = now()
                    WHERE 
                        deltakerliste_id = ?
                        AND okonomi_godkjent = false                                        
                    """.trimIndent(),
                    gjennomforingId,
                ).asUpdate,
            )
        }
    }

    private fun rowMapper(row: Row): PrisinfoDbo = PrisinfoDbo(
        id = row.uuid("id"),
        gjennomforingId = row.uuid("deltakerliste_id"),
        okonomiGodkjent = row.boolean("okonomi_godkjent"),
        prisinfoJsonSubtype = row.string("prisinformasjon_json_type"),
        anskaffelsePris = row.intOrNull("anskaffelse_pris"),
        tilleggsopplysninger = row.stringOrNull("tilleggsopplysninger"),
        ingenkostnaderAarsak = row.stringOrNull("ingenkostnader_aarsak")?.let {
            Aarsak.valueOf(it)
        },
    )
}
