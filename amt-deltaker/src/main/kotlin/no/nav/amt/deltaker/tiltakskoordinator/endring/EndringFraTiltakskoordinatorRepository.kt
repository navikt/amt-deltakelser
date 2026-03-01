package no.nav.amt.deltaker.tiltakskoordinator.endring

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class EndringFraTiltakskoordinatorRepository {
    fun insert(endringer: List<EndringFraTiltakskoordinator>) {
        val sql =
            """
            INSERT INTO endring_fra_tiltakskoordinator (
                id, 
                deltaker_id, 
                nav_ansatt_id, 
                nav_enhet_id, 
                endret, 
                endring
            ) 
            VALUES (
                :id, 
                :deltaker_id, 
                :nav_ansatt_id, 
                :nav_enhet_id, 
                :endret, 
                :endring
            )
            """.trimIndent()

        val params = endringer.map { endring ->
            mapOf(
                "id" to endring.id,
                "deltaker_id" to endring.deltakerId,
                "nav_ansatt_id" to endring.endretAv,
                "nav_enhet_id" to endring.endretAvEnhet,
                "endret" to endring.endret,
                "endring" to toPGObject(endring.endring),
            )
        }

        Database.query { session -> session.batchPreparedNamedStatement(sql, params) }
    }

    fun getForDeltaker(deltakerId: UUID): List<EndringFraTiltakskoordinator> {
        val sql =
            """
            SELECT 
                id,
                deltaker_id,
                nav_ansatt_id,
                nav_enhet_id,
                endret,
                endring
            FROM endring_fra_tiltakskoordinator 
            WHERE deltaker_id = :deltaker_id            
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

    fun get(id: UUID): EndringFraTiltakskoordinator? {
        val sql =
            """
            SELECT 
                id,
                deltaker_id,
                nav_ansatt_id,
                nav_enhet_id,
                endret,
                endring
            FROM endring_fra_tiltakskoordinator 
            WHERE id = :id
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(sql, mapOf("id" to id)).map(::rowMapper).asSingle,
            )
        }
    }

    fun deleteForDeltaker(deltakerId: UUID) = Database.query { session ->
        session.update(
            queryOf(
                "DELETE FROM endring_fra_tiltakskoordinator WHERE deltaker_id = :deltaker_id",
                mapOf("deltaker_id" to deltakerId),
            ),
        )
    }

    companion object {
        private fun rowMapper(row: Row): EndringFraTiltakskoordinator = EndringFraTiltakskoordinator(
            id = row.uuid("id"),
            deltakerId = row.uuid("deltaker_id"),
            endretAv = row.uuid("nav_ansatt_id"),
            endretAvEnhet = row.uuid("nav_enhet_id"),
            endret = row.localDateTime("endret"),
            endring = objectMapper.readValue(row.string("endring")),
        )
    }
}
