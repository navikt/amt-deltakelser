package no.nav.amt.deltaker.digitalbruker

import kotliquery.queryOf
import no.nav.amt.lib.utils.database.Database

object DigitalBrukerCacheRepository {
    /**
     * Henter cache-entries for et sett med personidenter.
     * Returnerer kun entries som er yngre enn 24 timer og finnes i databasen — manglende personidenter er ikke med i resultatet.
     */
    fun hentForPersonidenter(personidenter: Set<String>): Map<String, DigitalBrukerCacheEntry> {
        if (personidenter.isEmpty()) return emptyMap()

        val sql =
            """
            SELECT 
                personident, 
                er_digital
            FROM digital_bruker_cache
            WHERE 
                personident = ANY(:personidenter)
                AND modified_at > NOW() - INTERVAL '24 hours'
            """.trimIndent()

        return Database
            .query { session ->
                session.run(
                    queryOf(sql, mapOf("personidenter" to personidenter.toTypedArray()))
                        .map { row ->
                            DigitalBrukerCacheEntry(
                                personident = row.string("personident"),
                                erDigital = row.boolean("er_digital"),
                            )
                        }.asList,
                )
            }.associateBy { it.personident }
    }

    /**
     * Upsert-er en batch med cache-entries. Oppdaterer `er_digital` og `modified_at`
     * hvis personidenten allerede finnes.
     */
    fun upsertBatch(entries: List<Pair<String, Boolean>>) {
        if (entries.isEmpty()) return

        val sql =
            """
            INSERT INTO digital_bruker_cache (
                personident, 
                er_digital 
            )
            VALUES (
                :personident, 
                :er_digital 
            )
            ON CONFLICT (personident) DO UPDATE SET
                er_digital = :er_digital,
                modified_at = NOW()
            """.trimIndent()

        val params = entries.map {
            mapOf(
                "personident" to it.first,
                "er_digital" to it.second,
            )
        }

        Database.query { session ->
            session.batchPreparedNamedStatement(sql, params)
        }
    }
}
