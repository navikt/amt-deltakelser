package no.nav.amt.deltaker.navansatt

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.deltaker.db.DbUtils.sqlPlaceholders
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

class NavAnsattRepository {
    fun upsert(navAnsatt: NavAnsatt): NavAnsatt {
        val sql =
            """
            INSERT INTO nav_ansatt (
                id, 
                nav_ident, 
                navn, 
                telefonnummer, 
                epost, 
                nav_enhet_id
            )
            VALUES (
                :id, 
                :nav_ident, 
                :navn, 
                :telefonnummer, 
                :epost, 
                :nav_enhet_id
            ) 
            ON CONFLICT (id) DO UPDATE SET
                nav_ident = :nav_ident,
                navn = :navn,
                telefonnummer = :telefonnummer,
                epost = :epost,
                modified_at = CURRENT_TIMESTAMP,
                nav_enhet_id = :nav_enhet_id
            RETURNING *
            """.trimIndent()

        val params = mapOf(
            "id" to navAnsatt.id,
            "nav_ident" to navAnsatt.navIdent,
            "navn" to navAnsatt.navn,
            "telefonnummer" to navAnsatt.telefon,
            "epost" to navAnsatt.epost,
            "nav_enhet_id" to navAnsatt.navEnhetId,
        )

        return Database.query { session ->
            session.run(queryOf(sql, params).map(::rowMapper).asSingle)
                ?: throw RuntimeException("Noe gikk galt ved lagring av Nav-ansatt")
        }
    }

    fun getOrThrow(id: UUID): NavAnsatt = get(id) ?: throw NoSuchElementException("Fant ikke Nav-ansatt med id $id")

    fun get(id: UUID): NavAnsatt? = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM nav_ansatt WHERE id = :id",
                mapOf("id" to id),
            ).map(::rowMapper).asSingle,
        )
    }

    fun getOrThrow(navIdent: String): NavAnsatt =
        get(navIdent) ?: throw NoSuchElementException("Fant ikke Nav-ansatt med navIdent $navIdent")

    fun get(navIdent: String): NavAnsatt? = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM nav_ansatt WHERE nav_ident = :nav_ident",
                mapOf("nav_ident" to navIdent),
            ).map(::rowMapper).asSingle,
        )
    }

    fun delete(id: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM nav_ansatt WHERE id = :id",
                    mapOf("id" to id),
                ),
            )
        }
    }

    fun getMany(veilederIdenter: List<String>): List<NavAnsatt> {
        if (veilederIdenter.isEmpty()) return emptyList()

        val sql =
            """
            SELECT * 
            FROM nav_ansatt 
            WHERE nav_ident IN (${sqlPlaceholders(veilederIdenter.size)})
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(
                    sql,
                    *veilederIdenter.toTypedArray(),
                ).map(::rowMapper).asList,
            )
        }
    }

    companion object {
        private fun rowMapper(row: Row) = NavAnsatt(
            id = row.uuid("id"),
            navIdent = row.string("nav_ident"),
            navn = row.string("navn"),
            epost = row.stringOrNull("epost"),
            telefon = row.stringOrNull("telefonnummer"),
            navEnhetId = row.uuidOrNull("nav_enhet_id"),
        )
    }
}
