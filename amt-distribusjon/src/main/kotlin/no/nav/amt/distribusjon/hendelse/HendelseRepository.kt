package no.nav.amt.distribusjon.hendelse

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.distribusjon.application.plugins.objectMapper
import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.distribusjon.hendelse.model.Hendelse
import no.nav.amt.distribusjon.journalforing.model.HendelseMedJournalforingstatus
import no.nav.amt.distribusjon.journalforing.model.Journalforingstatus
import no.nav.amt.distribusjon.utils.DbUtils.toPGObject
import no.nav.amt.lib.utils.database.Database
import java.time.LocalDateTime
import java.util.UUID

class HendelseRepository {
    fun insert(hendelse: Hendelse) {
        val sql =
            """
            INSERT INTO hendelse (
                id, 
                deltaker_id, 
                deltaker, 
                ansvarlig, 
                payload, 
                distribusjonskanal, 
                manuelloppfolging
            )
            VALUES (
                :id, 
                :deltaker_id, 
                :deltaker, 
                :ansvarlig, 
                :payload, 
                :distribusjonskanal, 
                :manuelloppfolging
            )
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()

        val params = mapOf(
            "id" to hendelse.id,
            "deltaker_id" to hendelse.deltaker.id,
            "deltaker" to toPGObject(hendelse.deltaker),
            "ansvarlig" to toPGObject(hendelse.ansvarlig),
            "payload" to toPGObject(hendelse.payload),
            "distribusjonskanal" to hendelse.distribusjonskanal.name,
            "manuelloppfolging" to hendelse.manuellOppfolging,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    fun getIkkeJournalforteHendelser(opprettet: LocalDateTime): List<HendelseMedJournalforingstatus> {
        val sql =
            """
            $ikkeJournalforteHendelserBaseSql                
            WHERE
                js.journalpost_id IS NULL
                AND js.kan_ikke_journalfores IS NOT TRUE
                AND h.created_at < :opprettet
                            
            UNION ALL
            
            $ikkeJournalforteHendelserBaseSql                
            WHERE
                js.bestillingsid IS NULL
                AND js.kan_ikke_distribueres IS NOT TRUE
                AND h.distribusjonskanal NOT IN ('DITT_NAV','SDP')
                AND h.created_at < :opprettet
                -- utelukker records fra første spørring
                AND NOT (
                    js.journalpost_id IS NULL
                    AND js.kan_ikke_journalfores IS NOT TRUE
                )                                                         
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(
                    sql,
                    mapOf("opprettet" to opprettet),
                ).map(::hendelseMedJournalforingstatusRowMapper).asList,
            )
        }
    }

    fun getHendelser(hendelseIder: List<UUID>): List<Hendelse> {
        if (hendelseIder.isEmpty()) return emptyList()

        return Database.query { session ->
            session.run(
                queryOf(
                    """SELECT * FROM hendelse WHERE id IN (${hendelseIder.joinToString { "?" }})""",
                    *hendelseIder.toTypedArray(),
                ).map(::hendelseRowMapper).asList,
            )
        }
    }

    companion object {
        private val ikkeJournalforteHendelserBaseSql =
            """
            SELECT
                h.id,
                h.deltaker,
                h.ansvarlig,
                h.payload,
                h.created_at,
                h.distribusjonskanal,
                h.manuelloppfolging,
                js.journalpost_id,
                js.bestillingsid,
                js.kan_ikke_distribueres,
                js.kan_ikke_journalfores
            FROM 
                hendelse h
                JOIN journalforingstatus js ON h.id = js.hendelse_id            
            """.trimIndent()

        private fun hendelseRowMapper(row: Row) = Hendelse(
            id = row.uuid("id"),
            deltaker = objectMapper.readValue(row.string("deltaker")),
            ansvarlig = objectMapper.readValue(row.string("ansvarlig")),
            payload = objectMapper.readValue(row.string("payload")),
            opprettet = row.localDateTime("created_at"),
            distribusjonskanal = Distribusjonskanal.valueOf(row.string("distribusjonskanal")),
            manuellOppfolging = row.boolean("manuelloppfolging"),
        )

        private fun hendelseMedJournalforingstatusRowMapper(row: Row) = HendelseMedJournalforingstatus(
            hendelse = hendelseRowMapper(row),
            journalforingstatus = Journalforingstatus(
                hendelseId = row.uuid("id"),
                journalpostId = row.stringOrNull("journalpost_id"),
                bestillingsId = row.uuidOrNull("bestillingsid"),
                kanIkkeDistribueres = row.boolean("kan_ikke_distribueres"),
                kanIkkeJournalfores = row.boolean("kan_ikke_journalfores"),
            ),
        )
    }
}
