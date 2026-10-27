package no.nav.amt.deltaker.repository

import kotliquery.queryOf
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

object PrisinfoGjennomforingLinkRepository {
    fun upsert(
        gjennomforingId: UUID,
        prisinformasjonId: UUID,
        rolle: PrisinfoDbo.Rolle,
    ) {
        Database.query { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO prisinformasjon_gjennomforing_link (gjennomforing_id, prisinformasjon_id, rolle)
                    VALUES (?, ?, ?)
                    ON CONFLICT (gjennomforing_id, rolle)
                    DO UPDATE 
                    SET 
                        prisinformasjon_id = EXCLUDED.prisinformasjon_id, 
                        modified_at = now()
                    """.trimIndent(),
                    gjennomforingId,
                    prisinformasjonId,
                    rolle.name,
                ),
            )
        }
    }

    fun hentPrisinformasjonIdForEndring(gjennomforingId: UUID): UUID? = Database.query { session ->
        session.run(
            queryOf(
                """
                SELECT prisinformasjon_id 
                FROM prisinformasjon_gjennomforing_link
                WHERE gjennomforing_id = ? AND rolle = ?
                """.trimIndent(),
                gjennomforingId,
                PrisinfoDbo.Rolle.ENDRING.name,
            ).map { row -> row.uuid("prisinformasjon_id") }.asSingle,
        )
    }

    fun delete(
        gjennomforingId: UUID,
        prisinformasjonId: UUID,
        rolle: PrisinfoDbo.Rolle,
    ): Int = Database.query { session ->
        session.update(
            queryOf(
                """
                DELETE FROM prisinformasjon_gjennomforing_link
                WHERE 
                    gjennomforing_id = ? 
                    AND prisinformasjon_id = ?
                    AND rolle = ?
                """.trimIndent(),
                gjennomforingId,
                prisinformasjonId,
                rolle.name,
            ),
        )
    }
}
