package no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.bff.db.toPGObject
import no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse.model.UlestHendelse
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class UlestHendelseRepository {
    fun getForDeltaker(deltakerId: UUID): List<UlestHendelse> {
        val query = queryOf(
            """
            SELECT 
                id,
                deltaker_id,
                opprettet,
                ansvarlig,
                hendelse
            FROM ulest_hendelse
            WHERE deltaker_id = :deltaker_id
            """.trimIndent(),
            mapOf("deltaker_id" to deltakerId),
        ).map(::rowMapper).asList

        return Database.query { session -> session.run(query) }
    }

    fun get(id: UUID): Result<UlestHendelse> = runCatching {
        val query = queryOf(
            """
            SELECT 
                 id,
                 deltaker_id,
                 opprettet,
                 ansvarlig",
                 hendelse
             FROM ulest_hendelse uh
             WHERE uh.id = :id
            """.trimIndent(),
            mapOf("id" to id),
        ).map(::rowMapper).asSingle

        Database.query { session ->
            session.run(query)
                ?: throw NoSuchElementException("Ingen ulest hendelse med id $id")
        }
    }

    fun upsert(ulestHendelse: UlestHendelse) {
        val sql =
            """
            INSERT INTO ulest_hendelse(
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
                deltaker_id     	= :deltaker_id,
                opprettet 			= :opprettet,
                ansvarlig			= :ansvarlig,
                hendelse			= :hendelse,
                modified_at         = CURRENT_TIMESTAMP
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

    fun delete(id: UUID) = Database.query { session ->
        session.update(
            queryOf(
                "DELETE FROM ulest_hendelse WHERE id = :id",
                mapOf("id" to id),
            ),
        )
    }

    companion object {
        private fun rowMapper(row: Row): UlestHendelse = UlestHendelse(
            id = row.uuid("id"),
            opprettet = row.localDateTime("opprettet"),
            deltakerId = row.uuid("deltaker_id"),
            ansvarlig = row.stringOrNull("ansvarlig")?.let { objectMapper.readValue(it) },
            hendelse = objectMapper.readValue(row.string("hendelse")),
        )
    }
}
