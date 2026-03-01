package no.nav.amt.distribusjon.varsel

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.distribusjon.varsel.model.Varsel
import no.nav.amt.lib.utils.database.Database
import java.time.ZoneId
import java.util.UUID

class VarselRepository {
    fun upsert(varsel: Varsel) {
        val sql =
            """
            INSERT INTO varsel (
                id, 
                type, 
                hendelser, 
                status, 
                tekst, 
                aktiv_fra, 
                aktiv_til, 
                deltaker_id, 
                personident, 
                er_eksternt_varsel, 
                revarsel_for_varsel,
                revarsles
            )
            VALUES (
                :id, 
                :type, 
                :hendelser, 
                :status, 
                :tekst, 
                :aktiv_fra, 
                :aktiv_til, 
                :deltaker_id, 
                :personident, 
                :er_eksternt_varsel, 
                :revarsel_for_varsel,
                :revarsles
            )
            ON CONFLICT (id) DO UPDATE SET
                type = :type,
                hendelser = :hendelser,
                status = :status,
                tekst = :tekst,
                aktiv_fra = :aktiv_fra,
                aktiv_til = :aktiv_til,
                er_eksternt_varsel = :er_eksternt_varsel,
                revarsel_for_varsel = :revarsel_for_varsel,
                revarsles = :revarsles,
                modified_at = CURRENT_TIMESTAMP
            """.trimIndent()

        val params = mapOf(
            "id" to varsel.id,
            "type" to varsel.type.name,
            "status" to varsel.status.name,
            "hendelser" to varsel.hendelser.toTypedArray(),
            "tekst" to varsel.tekst,
            "aktiv_fra" to varsel.aktivFra,
            "aktiv_til" to varsel.aktivTil,
            "deltaker_id" to varsel.deltakerId,
            "personident" to varsel.personident,
            "er_eksternt_varsel" to varsel.erEksterntVarsel,
            "revarsles" to varsel.revarsles,
            "revarsel_for_varsel" to varsel.revarselForVarsel,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    fun getSisteVarsel(deltakerId: UUID, type: Varsel.Type): Result<Varsel> = runCatching {
        val sql =
            """
            SELECT * 
            FROM varsel
            WHERE 
                deltaker_id = :deltaker_id 
                AND type = :type
            ORDER BY aktiv_fra DESC
            LIMIT 1
            """.trimIndent()

        Database.query { session ->
            session.run(
                queryOf(
                    sql,
                    mapOf(
                        "deltaker_id" to deltakerId,
                        "type" to type.name,
                    ),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ingen varsel av type $type for deltaker $deltakerId")
        }
    }

    fun getAktiveEllerVentendeBeskjeder(deltakerId: UUID): List<Varsel> {
        val sql =
            """
            SELECT * 
            FROM varsel
            WHERE 
                deltaker_id = :deltaker_id 
                AND type = 'BESKJED'
                AND (
                    status = 'VENTER_PA_UTSENDELSE' 
                    OR status = 'AKTIV'
                )
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

    fun getAktivt(deltakerId: UUID): Result<Varsel> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    "SELECT * FROM varsel WHERE deltaker_id = :deltaker_id AND status = 'AKTIV'",
                    mapOf("deltaker_id" to deltakerId),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke varsel for deltakerId: $deltakerId")
        }
    }

    fun get(id: UUID): Result<Varsel> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    "SELECT * FROM varsel WHERE id = :id",
                    mapOf("id" to id),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke varsel $id")
        }
    }

    fun getByHendelseId(hendelseId: UUID): Result<Varsel> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    "SELECT * FROM varsel WHERE hendelser @> ARRAY[:hendelse_id]::uuid[]",
                    mapOf("hendelse_id" to hendelseId),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke varsel for hendelse $hendelseId")
        }
    }

    fun getVentendeVarsel(deltakerId: UUID): Result<Varsel> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    "SELECT * FROM varsel WHERE deltaker_id = :deltaker_id AND status = 'VENTER_PA_UTSENDELSE'",
                    mapOf("deltaker_id" to deltakerId),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke ventende varsel for deltaker $deltakerId")
        }
    }

    fun getVarslerSomSkalSendes(): List<Varsel> = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM varsel WHERE status = 'VENTER_PA_UTSENDELSE' AND aktiv_fra < CURRENT_TIMESTAMP",
            ).map(::rowMapper).asList,
        )
    }

    fun getVarslerSomSkalRevarsles(): List<Varsel> = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM varsel WHERE revarsles < CURRENT_TIMESTAMP",
            ).map(::rowMapper).asList,
        )
    }

    fun stoppRevarsler(deltakerId: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "UPDATE varsel SET revarsles = null WHERE deltaker_id = ? AND revarsles IS NOT NULL",
                    deltakerId,
                ),
            )
        }
    }

    companion object {
        private fun rowMapper(row: Row) = Varsel(
            id = row.uuid("id"),
            type = Varsel.Type.valueOf(row.string("type")),
            hendelser = row.array<UUID>("hendelser").toList(),
            status = Varsel.Status.valueOf(row.string("status")),
            aktivFra = row.zonedDateTime("aktiv_fra").withZoneSameInstant(ZoneId.of("Z")),
            aktivTil = row.zonedDateTimeOrNull("aktiv_til")?.withZoneSameInstant(ZoneId.of("Z")),
            deltakerId = row.uuid("deltaker_id"),
            personident = row.string("personident"),
            tekst = row.string("tekst"),
            erEksterntVarsel = row.boolean("er_eksternt_varsel"),
            revarselForVarsel = row.uuidOrNull("revarsel_for_varsel"),
            revarsles = row.zonedDateTimeOrNull("revarsles"),
        )
    }
}
