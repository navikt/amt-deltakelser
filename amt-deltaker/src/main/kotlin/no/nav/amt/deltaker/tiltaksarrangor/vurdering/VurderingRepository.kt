package no.nav.amt.deltaker.tiltaksarrangor.vurdering

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.Vurdering
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

open class VurderingRepository {
    fun getForDeltaker(deltakerId: UUID): List<Vurdering> = Database.query { session ->
        session.run(
            queryOf(
                """
                SELECT 
                    id, 
                    deltaker_id, 
                    opprettet_av_arrangor_ansatt_id, 
                    vurderingstype, 
                    begrunnelse, 
                    gyldig_fra 
                FROM vurdering 
                WHERE deltaker_id = :deltakerId
                """.trimIndent(),
                mapOf("deltakerId" to deltakerId),
            ).map(::rowMapper).asList,
        )
    }

    fun getSisteVurderingForDeltakere(deltakerIder: Set<UUID>): Map<UUID, Vurdering> {
        if (deltakerIder.isEmpty()) return emptyMap()

        val sql =
            """
            SELECT 
                id, 
                deltaker_id, 
                opprettet_av_arrangor_ansatt_id, 
                vurderingstype, 
                begrunnelse, 
                gyldig_fra
            FROM (
                SELECT *, ROW_NUMBER() OVER (
                    PARTITION BY deltaker_id ORDER BY gyldig_fra DESC
                ) AS rn
                FROM vurdering
                WHERE deltaker_id = ANY(:deltaker_ider)
            ) sub
            WHERE rn = 1
            """.trimIndent()

        return Database
            .query { session ->
                session.run(
                    queryOf(sql, mapOf("deltaker_ider" to deltakerIder.toTypedArray()))
                        .map(::rowMapper)
                        .asList,
                )
            }.associateBy { it.deltakerId }
    }

    fun upsert(vurdering: Vurdering) {
        val sql =
            """
            INSERT INTO vurdering (
                id, 
                deltaker_id, 
                opprettet_av_arrangor_ansatt_id, 
                vurderingstype, 
                begrunnelse, 
                gyldig_fra
            )
            VALUES (
                :id, 
                :deltaker_id, 
                :opprettet_av_arrangor_ansatt_id, 
                :vurderingstype, 
                :begrunnelse, 
                :gyldig_fra
            )
            ON CONFLICT (id) DO UPDATE SET
                opprettet_av_arrangor_ansatt_id = :opprettet_av_arrangor_ansatt_id, 
                vurderingstype = :vurderingstype, 
                begrunnelse = :begrunnelse, 
                gyldig_fra = :gyldig_fra
            """.trimIndent()

        val params = mapOf(
            "id" to vurdering.id,
            "deltaker_id" to vurdering.deltakerId,
            "opprettet_av_arrangor_ansatt_id" to vurdering.opprettetAvArrangorAnsattId,
            "vurderingstype" to vurdering.vurderingstype.name,
            "begrunnelse" to vurdering.begrunnelse,
            "gyldig_fra" to vurdering.gyldigFra,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    fun deleteForDeltaker(deltakerId: UUID) = Database.query { session ->
        session.update(
            queryOf(
                "DELETE FROM vurdering WHERE deltaker_id = :deltaker_id",
                mapOf("deltaker_id" to deltakerId),
            ),
        )
    }

    companion object {
        private fun rowMapper(row: Row) = Vurdering(
            id = row.uuid("id"),
            deltakerId = row.uuid("deltaker_id"),
            vurderingstype = Vurderingstype.valueOf(row.string("vurderingstype")),
            begrunnelse = row.stringOrNull("begrunnelse"),
            opprettetAvArrangorAnsattId = row.uuid("opprettet_av_arrangor_ansatt_id"),
            gyldigFra = row.localDateTime("gyldig_fra"),
        )
    }
}
