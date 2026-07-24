package no.nav.amt.deltaker.repository

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter.toPrisinformasjonDto
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.repository.dbo.PrisinfoUpsertDbo
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak
import no.nav.amt.lib.models.deltaker.PrisinformasjonForHistorikk
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

object PrisinfoRepository {
    fun hentPrisinfoStatus(
        gjennomforingId: UUID,
        prisinformasjonId: UUID,
    ): PrisinfoDbo.PrisinfoStatus? {
        val sql =
            """
            SELECT status 
            FROM enkeltplass_prisinformasjon
            WHERE
                id = ?
                AND deltakerliste_id = ? 
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(sql, prisinformasjonId, gjennomforingId)
                    .map { row ->
                        PrisinfoDbo.PrisinfoStatus.valueOf(row.string("status"))
                    }.asSingle,
            )
        }
    }

    fun hentPrisinfo(
        gjennomforingId: UUID,
        rolle: PrisinfoDbo.Rolle,
    ): PrisinfoDbo? {
        val sql =
            """
            SELECT 
                prisinfo.id,
                prisinfo.deltakerliste_id,
                prisinfo.status,
                prisinfo.prisinformasjon_json_type,
                prisinfo.anskaffelse_pris,
                prisinfo.tilleggsopplysninger,
                prisinfo.ingenkostnader_aarsak
            FROM
                deltakerliste_2_prisinformasjon d2p
                JOIN enkeltplass_prisinformasjon prisinfo ON d2p.prisinformasjon_id = prisinfo.id
            WHERE 
                d2p.deltakerliste_id = ? 
                AND d2p.rolle = ?
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(sql, gjennomforingId, rolle.name)
                    .map(::rowMapper)
                    .asSingle,
            )
        }
    }

    fun hentPrisinfoMap(gjennomforingId: UUID): Map<PrisinfoDbo.Rolle, PrisinfoDbo> {
        val sql =
            """
            SELECT 
                d2p.rolle,
                prisinfo.id,
                prisinfo.deltakerliste_id,
                prisinfo.status,
                prisinfo.prisinformasjon_json_type,
                prisinfo.anskaffelse_pris,
                prisinfo.tilleggsopplysninger,
                prisinfo.ingenkostnader_aarsak
            FROM
                deltakerliste_2_prisinformasjon d2p
                JOIN enkeltplass_prisinformasjon prisinfo ON d2p.prisinformasjon_id = prisinfo.id
            WHERE d2p.deltakerliste_id = ?
            """.trimIndent()

        return Database.query { session ->
            session
                .run(
                    queryOf(sql, gjennomforingId)
                        .map { row -> PrisinfoDbo.Rolle.valueOf(row.string("rolle")) to rowMapper(row) }
                        .asList,
                ).toMap()
        }
    }

    fun upsertPrisinfo(upsertDbo: PrisinfoUpsertDbo) {
        val sql =
            """
            INSERT INTO enkeltplass_prisinformasjon (
                id,
                deltakerliste_id,
                status,
                prisinformasjon_json_type,
                anskaffelse_pris,
                tilleggsopplysninger,
                ingenkostnader_aarsak
            )
            VALUES (
                :id,
                :deltakerliste_id,
                :status,
                :prisinformasjon_json_type,
                :anskaffelse_pris,
                :tilleggsopplysninger,
                :ingenkostnader_aarsak
            )
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                prisinformasjon_json_type = EXCLUDED.prisinformasjon_json_type,
                anskaffelse_pris = EXCLUDED.anskaffelse_pris,
                tilleggsopplysninger = EXCLUDED.tilleggsopplysninger,
                ingenkostnader_aarsak = EXCLUDED.ingenkostnader_aarsak,
                modified_at = now()
            """.trimIndent()

        val params = mapOf(
            "id" to upsertDbo.id,
            "deltakerliste_id" to upsertDbo.gjennomforingId,
            "status" to upsertDbo.status.name,
            "prisinformasjon_json_type" to upsertDbo.prisinfoJsonSubtype,
            "anskaffelse_pris" to upsertDbo.anskaffelsePris,
            "tilleggsopplysninger" to upsertDbo.tilleggsopplysninger,
            "ingenkostnader_aarsak" to upsertDbo.ingenkostnaderAarsak?.name,
        )

        Database.query { session ->
            session.update(
                queryOf(sql, params),
            )
        }
    }

    fun oppdaterStatus(
        prisinformasjonId: UUID,
        status: PrisinfoDbo.PrisinfoStatus,
    ) = Database.query { session ->
        session.update(
            queryOf(
                """
                UPDATE enkeltplass_prisinformasjon
                SET 
                    status = ?,
                    modified_at = now()
                WHERE id = ?
                """.trimIndent(),
                status.name,
                prisinformasjonId,
            ),
        )
    }

    fun hentPrisinfoListeForHistorikk(deltakerId: UUID): List<PrisinformasjonForHistorikk> {
        val sql =
            """
            SELECT 
                prisinfo.id,
                prisinfo.deltakerliste_id,
                prisinfo.status,
                prisinfo.prisinformasjon_json_type,
                prisinfo.anskaffelse_pris,
                prisinfo.tilleggsopplysninger,
                prisinfo.ingenkostnader_aarsak,
                vedtak.modified_at,
                vedtak.sist_endret_av,
                vedtak.sist_endret_av_enhet
            FROM
                deltaker                
                JOIN vedtak ON deltaker.id = vedtak.deltaker_id
                JOIN enkeltplass_prisinformasjon prisinfo ON deltaker.deltakerliste_id = prisinfo.deltakerliste_id
            WHERE 
                deltaker.id = ?
                AND prisinfo.status = 'GODKJENT'
            ORDER BY vedtak.modified_at                
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(sql, deltakerId)
                    .map { row ->
                        PrisinformasjonForHistorikk(
                            prisinformasjon = rowMapper(row).toPrisinformasjonDto(),
                            sistEndret = row.localDateTime("modified_at"),
                            sistEndretAvNavAnsattId = row.uuid("sist_endret_av"),
                            sistEndretAvNavEnhetId = row.uuid("sist_endret_av_enhet"),
                        )
                    }.asList,
            )
        }
    }

    private fun rowMapper(row: Row): PrisinfoDbo = PrisinfoDbo(
        id = row.uuid("id"),
        gjennomforingId = row.uuid("deltakerliste_id"),
        status = PrisinfoDbo.PrisinfoStatus.valueOf(row.string("status")),
        prisinfoJsonSubtype = row.string("prisinformasjon_json_type"),
        anskaffelsePris = row.intOrNull("anskaffelse_pris"),
        tilleggsopplysninger = row.stringOrNull("tilleggsopplysninger"),
        ingenkostnaderAarsak = row.stringOrNull("ingenkostnader_aarsak")?.let {
            Aarsak.valueOf(it)
        },
    )
}
