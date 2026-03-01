package no.nav.amt.deltaker.bff.deltaker.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.bff.db.toPGObject
import no.nav.amt.deltaker.bff.deltaker.model.Deltakeroppdatering
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

object DeltakerStatusRepository {
    fun insertIfNotExists(deltakerId: UUID, deltakerStatus: DeltakerStatus) {
        Database.query { session ->
            session.update(
                queryOf(
                    insertStatusSql,
                    buildInsertStatusParams(deltakerStatus, deltakerId),
                ),
            )
        }
    }

    fun batchInsert(deltakere: List<Deltakeroppdatering>) {
        Database.query { session ->
            session.batchPreparedNamedStatement(
                insertStatusSql,
                deltakere.map { buildInsertStatusParams(it.status, it.id) },
            )
        }
    }

    fun slettTidligereStatuser(deltakerId: UUID, deltakerStatus: DeltakerStatus) {
        Database.query { session ->
            session.update(
                queryOf(
                    slettTidligereStatuserSql,
                    buildSlettTidligereStatuserParams(deltakerStatus, deltakerId),
                ),
            )
        }
    }

    fun batchSlettTidligereStatuser(deltakere: List<Deltakeroppdatering>) {
        val slettTidligereStatuserParams = deltakere
            .map { buildSlettTidligereStatuserParams(it.status, it.id) }

        Database.query { session ->
            session.batchPreparedNamedStatement(slettTidligereStatuserSql, slettTidligereStatuserParams)
        }
    }

    fun getAktivDeltakerStatus(deltakerId: UUID): DeltakerStatus? = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM deltaker_status WHERE deltaker_id = :deltaker_id",
                mapOf("deltaker_id" to deltakerId),
            ).map(::rowMapper).asSingle,
        )
    }

    fun slettStatus(deltakerId: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM deltaker_status WHERE deltaker_id = :deltaker_id",
                    mapOf("deltaker_id" to deltakerId),
                ),
            )
        }
    }

    private fun rowMapper(row: Row) = DeltakerStatus(
        id = row.uuid("id"),
        type = DeltakerStatus.Type.valueOf(row.string("type")),
        aarsak = row.stringOrNull("aarsak")?.let { aarsak -> objectMapper.readValue(aarsak) },
        gyldigFra = row.localDateTime("gyldig_fra"),
        gyldigTil = null,
        opprettet = row.localDateTime("created_at"),
    )

    private val insertStatusSql =
        """
        INSERT INTO deltaker_status (
            id, 
            deltaker_id, 
            type, 
            aarsak, 
            gyldig_fra,
            created_at
        )
        VALUES (
            :id, 
            :deltaker_id, 
            :type, 
            :aarsak, 
            :gyldig_fra,
            :created_at
        )
        ON CONFLICT (id) DO NOTHING
        """.trimIndent()

    private fun buildInsertStatusParams(status: DeltakerStatus, deltakerId: UUID) = mapOf(
        "id" to status.id,
        "deltaker_id" to deltakerId,
        "type" to status.type.name,
        "aarsak" to toPGObject(status.aarsak),
        "gyldig_fra" to status.gyldigFra,
        "created_at" to status.opprettet,
    )

    private val slettTidligereStatuserSql =
        """
        DELETE FROM deltaker_status
        WHERE 
            deltaker_id = :deltaker_id 
            AND id != :id 
        """.trimIndent()

    private fun buildSlettTidligereStatuserParams(status: DeltakerStatus, deltakerId: UUID) =
        mapOf("id" to status.id, "deltaker_id" to deltakerId)
}
