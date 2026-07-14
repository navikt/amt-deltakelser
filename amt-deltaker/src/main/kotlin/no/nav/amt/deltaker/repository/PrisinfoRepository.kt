package no.nav.amt.deltaker.repository

import kotliquery.queryOf
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

object PrisinfoRepository {
    fun hentPrisinfo(gjennomforingId: UUID): PrisinfoDbo? {
        val sql =
            """
            SELECT 
                prisinformasjon_json_type,
                anskaffelse_pris,
                tilleggsopplysninger,
                ingenkostnader_aarsak
            FROM deltakerliste_prisinfo 
            WHERE deltakerliste_id = ?
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(sql, gjennomforingId)
                    .map { row ->
                        PrisinfoDbo(
                            prisinfoJsonSubtype = row.string("prisinformasjon_json_type"),
                            anskaffelsePris = row.intOrNull("anskaffelse_pris"),
                            tilleggsopplysninger = row.stringOrNull("tilleggsopplysninger"),
                            ingenkostnaderAarsak = row.stringOrNull("ingenkostnader_aarsak")?.let {
                                Aarsak.valueOf(it)
                            },
                        )
                    }.asSingle,
            )
        }
    }

    fun upsertPrisinfo(
        gjennomforingId: UUID,
        insertDbo: PrisinfoDbo,
    ) {
        val sql =
            """
            INSERT INTO deltakerliste_prisinfo (
                deltakerliste_id,
                prisinformasjon_json_type,
                anskaffelse_pris,
                tilleggsopplysninger,
                ingenkostnader_aarsak
            )
            VALUES (
                :deltakerliste_id,
                :prisinformasjon_json_type,
                :anskaffelse_pris,
                :tilleggsopplysninger,
                :ingenkostnader_aarsak
            )
            ON CONFLICT (deltakerliste_id) DO UPDATE SET
                prisinformasjon_json_type = EXCLUDED.prisinformasjon_json_type,
                anskaffelse_pris = EXCLUDED.anskaffelse_pris,
                tilleggsopplysninger = EXCLUDED.tilleggsopplysninger,
                ingenkostnader_aarsak = EXCLUDED.ingenkostnader_aarsak,
                modified_at = NOW()
            """.trimIndent()

        val params = mapOf(
            "deltakerliste_id" to gjennomforingId,
            "prisinformasjon_json_type" to insertDbo.prisinfoJsonSubtype,
            "anskaffelse_pris" to insertDbo.anskaffelsePris,
            "tilleggsopplysninger" to insertDbo.tilleggsopplysninger,
            "ingenkostnader_aarsak" to insertDbo.ingenkostnaderAarsak?.name,
        )

        Database.query {
            it.update(queryOf(sql, params))
        }
    }
}
