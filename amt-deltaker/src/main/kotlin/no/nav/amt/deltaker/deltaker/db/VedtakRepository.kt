package no.nav.amt.deltaker.deltaker.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class VedtakRepository {
    fun upsert(vedtak: Vedtak): Vedtak {
        val sql =
            """
            INSERT INTO vedtak (
                id,
                deltaker_id,
                fattet,
                gyldig_til,
                deltaker_ved_vedtak,
                fattet_av_nav,
                opprettet_av,
                opprettet_av_enhet,
                sist_endret_av,
                sist_endret_av_enhet)
            VALUES (
                :id,
                :deltaker_id,
                :fattet, 
                :gyldig_til,
                :deltaker_ved_vedtak,
                :fattet_av_nav,
                :opprettet_av,
                :opprettet_av_enhet,
                :sist_endret_av,
                :sist_endret_av_enhet
            )
            ON CONFLICT (id) DO UPDATE SET 
                fattet                = :fattet,
                gyldig_til            = :gyldig_til,
                deltaker_ved_vedtak   = :deltaker_ved_vedtak,
                fattet_av_nav         = :fattet_av_nav,
                modified_at           = CURRENT_TIMESTAMP,
                sist_endret_av        = :sist_endret_av,
                sist_endret_av_enhet  = :sist_endret_av_enhet
            RETURNING *
            """.trimIndent()

        val params = mapOf(
            "id" to vedtak.id,
            "deltaker_id" to vedtak.deltakerId,
            "fattet" to vedtak.fattet,
            "gyldig_til" to vedtak.gyldigTil,
            "deltaker_ved_vedtak" to toPGObject(vedtak.deltakerVedVedtak),
            "fattet_av_nav" to vedtak.fattetAvNav,
            "opprettet_av" to vedtak.opprettetAv,
            "opprettet_av_enhet" to vedtak.opprettetAvEnhet,
            "sist_endret_av" to vedtak.sistEndretAv,
            "sist_endret_av_enhet" to vedtak.sistEndretAvEnhet,
        )

        return Database.query { session ->
            session.run(
                queryOf(sql, params).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Noe gikk galt med upsert av vedtak ${vedtak.id}")
        }
    }

    fun get(id: UUID): Vedtak? = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM vedtak WHERE id = :id",
                mapOf("id" to id),
            ).map(::rowMapper).asSingle,
        )
    }

    fun getForDeltaker(deltakerId: UUID): Vedtak? = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM vedtak WHERE deltaker_id = :deltaker_id",
                mapOf("deltaker_id" to deltakerId),
            ).map(::rowMapper).asSingle,
        )
    }

    fun deleteForDeltaker(deltakerId: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM vedtak WHERE deltaker_id = :deltaker_id",
                    mapOf("deltaker_id" to deltakerId),
                ),
            )
        }
    }

    companion object {
        private fun rowMapper(row: Row) = Vedtak(
            id = row.uuid("id"),
            deltakerId = row.uuid("deltaker_id"),
            fattet = row.localDateTimeOrNull("fattet"),
            gyldigTil = row.localDateTimeOrNull("gyldig_til"),
            deltakerVedVedtak = objectMapper.readValue(row.string("deltaker_ved_vedtak")),
            fattetAvNav = row.boolean("fattet_av_nav"),
            opprettet = row.localDateTime("created_at"),
            opprettetAv = row.uuid("opprettet_av"),
            opprettetAvEnhet = row.uuid("opprettet_av_enhet"),
            sistEndret = row.localDateTime("modified_at"),
            sistEndretAv = row.uuid("sist_endret_av"),
            sistEndretAvEnhet = row.uuid("sist_endret_av_enhet"),
        )
    }
}
