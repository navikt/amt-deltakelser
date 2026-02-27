package no.nav.amt.deltaker.bff.deltaker.vurdering

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

class VurderingRepository {
    fun getForDeltaker(deltakerId: UUID): List<Vurdering> = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM vurdering WHERE deltaker_id = :deltaker_id",
                mapOf("deltaker_id" to deltakerId),
            ).map(::rowMapper).asList,
        )
    }

    fun get(id: UUID): Result<Vurdering> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    "SELECT * FROM vurdering WHERE id = :id",
                    mapOf("id" to id),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Ingen vurderinger med id $id")
        }
    }

    fun upsert(vurdering: Vurdering) {
        val sql =
            """
            INSERT INTO vurdering(
                id,
                deltaker_id,
                opprettet_av_arrangor_ansatt_id,
                opprettet,
                begrunnelse,
                vurderingstype
            )
            VALUES (
                :id,
                :deltaker_id,
                :opprettet_av_arrangor_ansatt_id,
                :opprettet,
                :begrunnelse,
                :vurderingstype
            )
            ON CONFLICT (id) DO UPDATE SET
                opprettet = :opprettet,
                begrunnelse	= :begrunnelse,
                vurderingstype = :vurderingstype,
                modified_at = CURRENT_TIMESTAMP
            """.trimIndent()

        val params = mapOf(
            "id" to vurdering.id,
            "deltaker_id" to vurdering.deltakerId,
            "opprettet_av_arrangor_ansatt_id" to vurdering.opprettetAvArrangorAnsattId,
            "opprettet" to vurdering.opprettet,
            "begrunnelse" to vurdering.begrunnelse,
            "vurderingstype" to vurdering.vurderingstype.name,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    companion object {
        private fun rowMapper(row: Row): Vurdering = Vurdering(
            id = row.uuid("id"),
            deltakerId = row.uuid("deltaker_id"),
            opprettetAvArrangorAnsattId = row.uuid("opprettet_av_arrangor_ansatt_id"),
            opprettet = row.localDateTime("opprettet"),
            vurderingstype = Vurderingstype.valueOf(row.string("vurderingstype")),
            begrunnelse = row.stringOrNull("begrunnelse"),
        )
    }
}
