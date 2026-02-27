package no.nav.amt.deltaker.deltaker.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerEndringRepository {
    fun upsert(deltakerEndring: DeltakerEndring, behandletTidspunkt: LocalDateTime? = LocalDateTime.now()) {
        val sql =
            """
            INSERT INTO deltaker_endring (
                id, 
                deltaker_id, 
                endring, 
                endret, 
                endret_av, 
                endret_av_enhet, 
                forslag_id, 
                behandlet
            )
            VALUES (
                :id, 
                :deltaker_id, 
                :endring, 
                :endret, 
                :endret_av, 
                :endret_av_enhet, 
                :forslag_id, 
                :behandlet
            )
            ON CONFLICT (id) DO UPDATE SET 
                deltaker_id = :deltaker_id,
                endring = :endring,
                endret = :endret,
                endret_av = :endret_av,
                endret_av_enhet = :endret_av_enhet,
                forslag_id = :forslag_id,
                modified_at = CURRENT_TIMESTAMP,
                behandlet = :behandlet
            """.trimIndent()

        val params = mapOf(
            "id" to deltakerEndring.id,
            "deltaker_id" to deltakerEndring.deltakerId,
            "endring" to toPGObject(deltakerEndring.endring),
            "endret" to deltakerEndring.endret,
            "endret_av" to deltakerEndring.endretAv,
            "endret_av_enhet" to deltakerEndring.endretAvEnhet,
            "forslag_id" to deltakerEndring.forslag?.id,
            "behandlet" to behandletTidspunkt,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    fun get(id: UUID): Result<DeltakerEndring> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    selectDeltakerEndring("de.id = :id"),
                    mapOf("id" to id),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException()
        }
    }

    fun getForDeltaker(deltakerId: UUID): List<DeltakerEndring> = Database.query { session ->
        session.run(
            queryOf(
                selectDeltakerEndring("de.deltaker_id = :deltaker_id"),
                mapOf("deltaker_id" to deltakerId),
            ).map(::rowMapper).asList,
        )
    }

    fun getUbehandletDeltakelsesmengder(offset: Int = 0, limit: Int = 500): List<DeltakerEndring> {
        val sql = selectDeltakerEndring(
            """
            de.behandlet IS NULL 
            AND ds.type in ('${DeltakerStatus.Type.VENTER_PA_OPPSTART.name}', '${DeltakerStatus.Type.DELTAR.name}')
            AND de.endring->>'type' = :type
            AND de.endring->>'gyldigFra' <= :now
            """.trimIndent(),
            offset,
            limit,
        )

        val params = mapOf(
            "type" to DeltakerEndring.Endring.EndreDeltakelsesmengde::class.simpleName,
            "now" to LocalDate.now(),
        )

        return Database.query { session ->
            session.run(queryOf(sql, params).map(::rowMapper).asList)
        }
    }

    fun deleteForDeltaker(deltakerId: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM deltaker_endring WHERE deltaker_id = :deltaker_id",
                    mapOf("deltaker_id" to deltakerId),
                ),
            )
        }
    }

    companion object {
        private fun rowMapper(row: Row) = DeltakerEndring(
            id = row.uuid("de.id"),
            deltakerId = row.uuid("de.deltaker_id"),
            endring = objectMapper.readValue(row.string("de.endring")),
            endretAv = row.uuid("de.endret_av"),
            endretAvEnhet = row.uuid("de.endret_av_enhet"),
            endret = row.localDateTime("de.endret"),
            forslag = row.uuidOrNull("f.id")?.let { ForslagRepository.rowMapper(row) },
        )

        private fun selectDeltakerEndring(
            whereClause: String,
            offset: Int? = null,
            limit: Int? = null,
        ) = """
            SELECT 
                de.id               AS "de.id",
                de.deltaker_id      AS "de.deltaker_id",
                de.endring          AS "de.endring",
                de.endret           AS "de.endret",
                de.endret_av        AS "de.endret_av",
                de.endret_av_enhet  AS "de.endret_av_enhet",
                de.modified_at      AS "de.modified_at",
                f.id                AS "f.id",
                f.deltaker_id       AS "f.deltaker_id",
                f.arrangoransatt_id AS "f.arrangoransatt_id",
                f.opprettet         AS "f.opprettet",
                f.begrunnelse       AS "f.begrunnelse",
                f.endring           AS "f.endring",
                f.status            AS "f.status"
            FROM 
                deltaker_endring de
                JOIN deltaker_status ds ON 
                    ds.deltaker_id = de.deltaker_id
                    AND ds.gyldig_til IS NULL
                    AND ds.gyldig_fra <= CURRENT_TIMESTAMP
                    AND ds.type != 'FEILREGISTRERT'
                LEFT JOIN forslag f ON f.id = de.forslag_id
            WHERE $whereClause
            ORDER BY de.created_at
            ${offset?.let { "OFFSET $it" } ?: ""}
            ${limit?.let { "LIMIT $it" } ?: ""}
            """.trimIndent()
    }
}
