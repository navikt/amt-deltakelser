package no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelse
import no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelseType
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class UlestHendelseRepository {
    fun upsert(ulestHendelse: UlestHendelse) {
        val sql =
            """
            INSERT INTO ulest_hendelse (
                id,
                deltaker_id,
                opprettet,
                ansvarlig,
                hendelse
            )
            VALUES (
                :id,
                :deltaker_id,
                :opprettet,
                :ansvarlig,
                :hendelse
            )
            ON CONFLICT (id) DO UPDATE SET
                deltaker_id = :deltaker_id,
                opprettet = :opprettet,
                ansvarlig = :ansvarlig,
                hendelse = :hendelse,
                modified_at = CURRENT_TIMESTAMP
            """.trimIndent()

        val params = mapOf(
            "id" to ulestHendelse.id,
            "deltaker_id" to ulestHendelse.deltakerId,
            "opprettet" to ulestHendelse.opprettet,
            "ansvarlig" to toPGObject(ulestHendelse.ansvarlig),
            "hendelse" to toPGObject(ulestHendelse.hendelse),
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    fun delete(id: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM ulest_hendelse WHERE id = :id",
                    mapOf("id" to id),
                ),
            )
        }
    }

    internal fun get(id: UUID): UlestHendelse? = Database.query { session ->
        session.run(
            queryOf(
                """
                SELECT
                    id,
                    deltaker_id,
                    opprettet,
                    ansvarlig,
                    hendelse
                FROM ulest_hendelse
                WHERE id = :id
                """.trimIndent(),
                mapOf("id" to id),
            ).map(::rowMapper).asSingle,
        )
    }

    companion object {
        private fun rowMapper(row: Row): UlestHendelse = UlestHendelse(
            id = row.uuid("id"),
            deltakerId = row.uuid("deltaker_id"),
            opprettet = row.localDateTime("opprettet"),
            ansvarlig = row.stringOrNull("ansvarlig")?.let { objectMapper.readValue(it) },
            hendelse = objectMapper.readValue<UlestHendelseType>(row.string("hendelse")),
        )
    }
}
