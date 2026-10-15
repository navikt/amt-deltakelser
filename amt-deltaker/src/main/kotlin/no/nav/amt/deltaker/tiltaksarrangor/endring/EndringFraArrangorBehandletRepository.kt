package no.nav.amt.deltaker.tiltaksarrangor.endring

import kotliquery.queryOf
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

class EndringFraArrangorBehandletRepository {
    fun exists(id: UUID): Boolean = Database.query { session ->
        session.run(
            queryOf(
                "SELECT EXISTS (SELECT 1 FROM endring_fra_arrangor_behandlet WHERE id = ?)",
                id,
            ).map { it.boolean("exists") }.asSingle,
        ) ?: false
    }

    /**
     * Registrerer meldings-ID-en som behandlet. Må kjøres i samme transaksjon som deltakeroppdateringen,
     * hvis meldingen fører til en slik.
     */
    fun markerSomBehandlet(
        id: UUID,
        deltakerId: UUID,
    ): Boolean {
        val sql =
            """
            INSERT INTO endring_fra_arrangor_behandlet (
                id,
                deltaker_id
            )
            VALUES (
                :id,
                :deltaker_id
            )
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()

        val params = mapOf(
            "id" to id,
            "deltaker_id" to deltakerId,
        )

        return Database.query { session -> session.update(queryOf(sql, params)) } > 0
    }
}
