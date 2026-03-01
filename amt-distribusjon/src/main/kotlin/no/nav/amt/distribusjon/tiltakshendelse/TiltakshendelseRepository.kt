package no.nav.amt.distribusjon.tiltakshendelse

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.distribusjon.tiltakshendelse.model.Tiltakshendelse
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

class TiltakshendelseRepository {
    fun upsert(tiltakshendelse: Tiltakshendelse) {
        val sql =
            """
            INSERT INTO tiltakshendelse (
                id, 
                type, 
                deltaker_id, 
                forslag_id, 
                hendelser, 
                personident, 
                aktiv, 
                tekst, 
                tiltakskode
            )
            VALUES (
                :id, 
                :type, 
                :deltaker_id, 
                :forslag_id, 
                :hendelser, 
                :personident, 
                :aktiv, 
                :tekst, 
                :tiltakskode
            )
            ON CONFLICT (id) DO UPDATE SET
                hendelser = :hendelser,
                personident = :personident,
                aktiv = :aktiv,
                tekst = :tekst,
                modified_at = CURRENT_TIMESTAMP                
            """.trimIndent()

        val params = mapOf(
            "id" to tiltakshendelse.id,
            "type" to tiltakshendelse.type.name,
            "deltaker_id" to tiltakshendelse.deltakerId,
            "forslag_id" to tiltakshendelse.forslagId,
            "hendelser" to tiltakshendelse.hendelser.toTypedArray(),
            "personident" to tiltakshendelse.personident,
            "aktiv" to tiltakshendelse.aktiv,
            "tekst" to tiltakshendelse.tekst,
            "tiltakskode" to tiltakshendelse.tiltakskode.name,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    fun get(id: UUID): Result<Tiltakshendelse> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    "SELECT * FROM tiltakshendelse WHERE id = :id",
                    mapOf("id" to id),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke tiltakshendelse $id")
        }
    }

    fun getHendelse(deltakerId: UUID, hendelseType: Tiltakshendelse.Type): Result<Tiltakshendelse> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    "SELECT * FROM tiltakshendelse WHERE deltaker_id = :deltaker_id AND type = :type",
                    mapOf(
                        "deltaker_id" to deltakerId,
                        "type" to hendelseType.name,
                    ),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke tiltakshendelse for deltaker $deltakerId og type $hendelseType")
        }
    }

    fun getForslagHendelse(forslagId: UUID): Result<Tiltakshendelse> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    "SELECT * FROM tiltakshendelse WHERE forslag_id = :forslag_id",
                    mapOf("forslag_id" to forslagId),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke tiltakshendelse for med forslagId $forslagId")
        }
    }

    fun getByHendelseId(hendelseId: UUID): Result<Tiltakshendelse> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    "SELECT * FROM tiltakshendelse WHERE hendelser @> ARRAY[:hendelse_id]::uuid[]",
                    mapOf("hendelse_id" to hendelseId),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke tiltakshendelse for hendelse $hendelseId")
        }
    }

    companion object {
        private fun rowMapper(row: Row) = Tiltakshendelse(
            id = row.uuid("id"),
            type = Tiltakshendelse.Type.valueOf(row.string("type")),
            deltakerId = row.uuid("deltaker_id"),
            forslagId = row.uuidOrNull("forslag_id"),
            hendelser = row.array<UUID>("hendelser").toList(),
            personident = row.string("personident"),
            aktiv = row.boolean("aktiv"),
            tekst = row.string("tekst"),
            tiltakskode = Tiltakskode.valueOf(row.string("tiltakskode")),
            opprettet = row.localDateTime("created_at"),
        )
    }
}
