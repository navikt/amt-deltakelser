package no.nav.amt.deltaker.repository

import kotliquery.queryOf
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

object Deltakerliste2PrisinfoRepository {
    fun upsert(
        gjennomforingId: UUID,
        prisinformasjonId: UUID,
        rolle: PrisinfoDbo.Rolle,
    ) {
        Database.query { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO deltakerliste_2_prisinformasjon (deltakerliste_id, prisinformasjon_id, rolle)
                    VALUES (?, ?, ?)
                    ON CONFLICT (deltakerliste_id, rolle) 
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
                SELECT prisinformasjon_id FROM deltakerliste_2_prisinformasjon
                WHERE deltakerliste_id = ? AND rolle = ?
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
    ) {
        Database.query { session ->
            session.update(
                queryOf(
                    """
                    DELETE FROM deltakerliste_2_prisinformasjon
                    WHERE 
                        deltakerliste_id = ? 
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
}
