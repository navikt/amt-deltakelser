package no.nav.amt.deltaker.deltaker.endring.fra.arrangor

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class EndringFraArrangorRepository {
    fun getForDeltaker(deltakerId: UUID): List<EndringFraArrangor> {
        val sql =
            """
            SELECT 
                id,
                deltaker_id,
                arrangor_ansatt_id,
                opprettet,
                endring
            FROM endring_fra_arrangor ea 
            WHERE ea.deltaker_id = :deltaker_id
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(
                    sql,
                    mapOf("deltaker_id" to deltakerId),
                ).map(::rowMapper).asList,
            )
        }
    }

    fun insert(endring: EndringFraArrangor) {
        val sql =
            """
            INSERT INTO endring_fra_arrangor (
                id, 
                deltaker_id, 
                arrangor_ansatt_id, 
                opprettet, endring
            )
            VALUES (
                :id, 
                :deltaker_id, 
                :arrangor_ansatt_id, 
                :opprettet, 
                :endring
            )
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()

        val params = mapOf(
            "id" to endring.id,
            "deltaker_id" to endring.deltakerId,
            "arrangor_ansatt_id" to endring.opprettetAvArrangorAnsattId,
            "opprettet" to endring.opprettet,
            "endring" to toPGObject(endring.endring),
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    fun deleteForDeltaker(deltakerId: UUID) = Database.query {
        it.update(
            queryOf(
                "DELETE FROM endring_fra_arrangor WHERE deltaker_id = :deltaker_id",
                mapOf("deltaker_id" to deltakerId),
            ),
        )
    }

    companion object {
        private fun rowMapper(row: Row): EndringFraArrangor = EndringFraArrangor(
            id = row.uuid("id"),
            deltakerId = row.uuid("deltaker_id"),
            opprettetAvArrangorAnsattId = row.uuid("arrangor_ansatt_id"),
            opprettet = row.localDateTime("opprettet"),
            endring = objectMapper.readValue(row.string("endring")),
        )
    }
}
