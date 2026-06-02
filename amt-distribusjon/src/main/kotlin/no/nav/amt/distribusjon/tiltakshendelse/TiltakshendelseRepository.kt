package no.nav.amt.distribusjon.tiltakshendelse

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.distribusjon.tiltakshendelse.model.Tiltakshendelse
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

class TiltakshendelseRepository {
    fun upsert(tiltakshendelse: Tiltakshendelse): Tiltakshendelse {
        val sql = if (tiltakshendelse.forslagId == null) {
            // Utkast har ikke forslagId og må derfor håndteres med konflikt på primærnøkkelen (id).
            UPSERT_BY_ID_SQL
        } else {
            // Forslag håndteres med konflikt på unik forslagId for å støtte idempotent reprosessering.
            UPSERT_BY_FORSLAG_ID_SQL
        }

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

        return Database.query { session ->
            session.run(
                queryOf(sql, params).map(::rowMapper).asSingle,
            ) ?: error("Klarte ikke å upserte tiltakshendelse ${tiltakshendelse.id}")
        }
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

    fun getHendelse(
        deltakerId: UUID,
        hendelseType: Tiltakshendelse.Type,
    ): Result<Tiltakshendelse> = runCatching {
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

        private const val ID_COLUMN = "id"
        private const val FORSLAG_ID_COLUMN = "forslag_id"

        private val UPSERT_BY_ID_SQL = createUpsertSql(ID_COLUMN)
        private val UPSERT_BY_FORSLAG_ID_SQL = createUpsertSql(FORSLAG_ID_COLUMN)

        private fun createUpsertSql(conflictColumn: String) =
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
            ON CONFLICT ($conflictColumn) DO UPDATE SET
                hendelser = EXCLUDED.hendelser,
                personident = EXCLUDED.personident,
                aktiv = EXCLUDED.aktiv,
                tekst = EXCLUDED.tekst,
                modified_at = CURRENT_TIMESTAMP
            RETURNING *
            """.trimIndent()
    }
}
