package no.nav.amt.deltaker.bff.navansatt

import kotliquery.Row
import kotliquery.queryOf
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
                epost, 
                telefon 
            )
            VALUES (
                :id, 
                :nav_ident, 
                :navn, 
                :epost, 
                :telefon 
            ) 
            ON CONFLICT (id) DO UPDATE SET
                nav_ident = :nav_ident,
                navn = :navn,
                epost = :epost,
                telefon = :telefon,
                modified_at = CURRENT_TIMESTAMP
            RETURNING *
            """.trimIndent()

        val query = queryOf(
            sql,
            mapOf(
                "id" to navAnsatt.id,
                "nav_ident" to navAnsatt.navIdent,
                "navn" to navAnsatt.navn,
                "epost" to navAnsatt.epost,
                "telefon" to navAnsatt.telefon,
            ),
        ).map(::rowMapper).asSingle

        return Database.query { session ->
            session.run(query) ?: throw RuntimeException("Noe gikk galt ved lagring av nav-ansatt")
        }
    }

    fun get(id: UUID): NavAnsatt? = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM nav_ansatt WHERE id = :id",
                mapOf("id" to id),
            ).map(::rowMapper).asSingle,
        )
    }

    fun get(navIdent: String): NavAnsatt? = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM nav_ansatt WHERE nav_ident = :nav_ident",
                mapOf("nav_ident" to navIdent),
            ).map(::rowMapper).asSingle,
        )
    }

    fun delete(id: UUID) = Database.query { session ->
        session.update(
            queryOf(
                "DELETE FROM nav_ansatt WHERE id = :id",
                mapOf("id" to id),
            ),
        )
    }

    fun getMany(veilederIdenter: List<UUID>): List<NavAnsatt> {
        if (veilederIdenter.isEmpty()) return emptyList()

        return Database.query { session ->
            session.run(
                queryOf(
                    "SELECT * FROM nav_ansatt WHERE id IN (${veilederIdenter.joinToString { "?" }})",
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
            telefon = row.stringOrNull("telefon"),
            epost = row.stringOrNull("epost"),
            navEnhetId = null, // Should be same as amt-deltaker?
        )
    }
}
