package no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.bff.db.toPGObject
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelseFlags
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelseTypeCounts
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class UlestHendelseRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getForDeltaker(deltakerId: UUID): List<UlestHendelse> {
        val query = queryOf(
            """
            SELECT 
                id,
                deltaker_id,
                opprettet,
                ansvarlig,
                hendelse
            FROM ulest_hendelse
            WHERE deltaker_id = :deltaker_id
            """.trimIndent(),
            mapOf("deltaker_id" to deltakerId),
        ).map(::rowMapper).asList

        return Database.query { session -> session.run(query) }
    }

    fun getForDeltakere(deltakerIder: Set<UUID>): Map<UUID, UlestHendelseFlags> {
        if (deltakerIder.isEmpty()) return emptyMap()

        val query = queryOf(
            """
            SELECT 
                deltaker_id,
                COALESCE(BOOL_OR(hendelse->>'type' IN ('InnbyggerGodkjennUtkast', 'NavGodkjennUtkast')), false) AS er_ny_deltaker,
                COALESCE(BOOL_OR(hendelse->>'type' IN ('IkkeAktuell', 'AvsluttDeltakelse', 'AvbrytDeltakelse', 'ReaktiverDeltakelse')), false) AS har_oppdatering_fra_nav
            FROM ulest_hendelse
            WHERE deltaker_id = ANY(:deltaker_ider)
            GROUP BY deltaker_id
            """.trimIndent(),
            mapOf("deltaker_ider" to deltakerIder.toTypedArray()),
        ).map { row ->
            row.uuid("deltaker_id") to UlestHendelseFlags(
                erNyDeltaker = row.boolean("er_ny_deltaker"),
                harOppdateringFraNav = row.boolean("har_oppdatering_fra_nav"),
            )
        }.asList

        return Database.query { session -> session.run(query) }.toMap()
    }

    fun getTypeCountsForDeltakere(deltakerIder: Set<UUID>): UlestHendelseTypeCounts {
        if (deltakerIder.isEmpty()) return UlestHendelseTypeCounts()

        val query = queryOf(
            """
            SELECT
                COUNT(DISTINCT deltaker_id) FILTER (
                    WHERE hendelse->>'type' IN ('InnbyggerGodkjennUtkast', 'NavGodkjennUtkast')
                ) AS er_ny_deltaker_count,
                COUNT(DISTINCT deltaker_id) FILTER (
                    WHERE hendelse->>'type' IN ('IkkeAktuell', 'AvsluttDeltakelse', 'AvbrytDeltakelse', 'ReaktiverDeltakelse')
                ) AS har_oppdatering_fra_nav_count
            FROM ulest_hendelse
            WHERE deltaker_id = ANY(:deltaker_ider)
            """.trimIndent(),
            mapOf("deltaker_ider" to deltakerIder.toTypedArray()),
        ).map { row ->
            UlestHendelseTypeCounts(
                erNyDeltaker = row.int("er_ny_deltaker_count"),
                harOppdateringFraNav = row.int("har_oppdatering_fra_nav_count"),
            )
        }.asSingle

        return Database.query { session ->
            session.run(query) ?: UlestHendelseTypeCounts()
        }
    }

    // Used by tests only
    fun get(id: UUID): Result<UlestHendelse> = runCatching {
        val query = queryOf(
            """
            SELECT 
                 id,
                 deltaker_id,
                 opprettet,
                 ansvarlig",
                 hendelse
             FROM ulest_hendelse uh
             WHERE uh.id = :id
            """.trimIndent(),
            mapOf("id" to id),
        ).map(::rowMapper).asSingle

        Database.query { session ->
            session.run(query)
                ?: throw NoSuchElementException("Ingen ulest hendelse med id $id")
        }
    }

    fun upsert(ulestHendelse: UlestHendelse) {
        val sql =
            """
            INSERT INTO ulest_hendelse(
                id, 
                deltaker_id, 
                opprettet, 
                ansvarlig, 
                hendelse
            )
            VALUES (
                :id,
                :deltaker_id,
                :opprettet,
                :ansvarlig,
                :hendelse
            )
            ON CONFLICT (id) DO UPDATE SET
                deltaker_id     	= :deltaker_id,
                opprettet 			= :opprettet,
                ansvarlig			= :ansvarlig,
                hendelse			= :hendelse,
                modified_at         = CURRENT_TIMESTAMP
            """.trimIndent()

        val params = mapOf(
            "id" to ulestHendelse.id,
            "deltaker_id" to ulestHendelse.deltakerId,
            "opprettet" to ulestHendelse.opprettet,
            "ansvarlig" to toPGObject(ulestHendelse.ansvarlig),
            "hendelse" to toPGObject(ulestHendelse.hendelse),
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    fun delete(id: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM ulest_hendelse WHERE id = :id",
                    mapOf("id" to id),
                ),
            )
        }
        log.info("Slettet ulest hendelse $id")
    }

    companion object {
        private fun rowMapper(row: Row) = UlestHendelse(
            id = row.uuid("id"),
            opprettet = row.localDateTime("opprettet"),
            deltakerId = row.uuid("deltaker_id"),
            ansvarlig = row.stringOrNull("ansvarlig")?.let { objectMapper.readValue(it) },
            hendelse = objectMapper.readValue(row.string("hendelse")),
        )
    }
}
