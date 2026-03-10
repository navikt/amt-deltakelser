package no.nav.amt.deltaker.bff.deltaker.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.api.paamelding.response.OpprettKladdResponse
import no.nav.amt.deltaker.bff.db.toPGObject
import no.nav.amt.deltaker.bff.deltaker.model.AVSLUTTENDE_STATUSER
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltaker.model.Deltakeroppdatering
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class DeltakerRepository {
    fun getAntallDeltakereForDeltakerliste(deltakerlisteId: UUID): Int = Database.query { session ->
        session.run(
            queryOf(
                "SELECT COUNT(*) FROM deltaker WHERE deltakerliste_id = :deltakerliste_id",
                mapOf("deltakerliste_id" to deltakerlisteId),
            ).map { it.int(1) }.asSingle,
        ) ?: 0
    }

    fun opprettKladd(kladd: OpprettKladdResponse) {
        val sql =
            """
            INSERT INTO deltaker (
                id, 
                person_id, 
                deltakerliste_id, 
                startdato, 
                sluttdato, 
                dager_per_uke, 
                deltakelsesprosent, 
                bakgrunnsinformasjon, 
                innhold
            )
            VALUES (
                :id, 
                :person_id, 
                :deltakerlisteId, 
                :startdato, 
                :sluttdato, 
                :dagerPerUke, 
                :deltakelsesprosent, 
                :bakgrunnsinformasjon, 
                :innhold
            )
            """.trimIndent()

        val parameters = mapOf(
            "id" to kladd.id,
            "person_id" to kladd.navBruker.personId,
            "deltakerlisteId" to kladd.deltakerlisteId,
            "startdato" to kladd.startdato,
            "sluttdato" to kladd.sluttdato,
            "dagerPerUke" to kladd.dagerPerUke,
            "deltakelsesprosent" to kladd.deltakelsesprosent,
            "bakgrunnsinformasjon" to kladd.bakgrunnsinformasjon,
            "innhold" to toPGObject(kladd.deltakelsesinnhold),
        )

        Database.query { session -> session.update(queryOf(sql, parameters)) }
    }

    fun upsert(deltaker: Deltaker) {
        val sql =
            """
            INSERT INTO deltaker (
                id, 
                person_id, 
                deltakerliste_id, 
                startdato, 
                sluttdato, 
                dager_per_uke, 
                deltakelsesprosent, 
                bakgrunnsinformasjon, 
                innhold, 
                historikk, 
                kan_endres, 
                modified_at,
                er_manuelt_delt_med_arrangor
            )
            VALUES (
                :id, 
                :person_id, 
                :deltakerlisteId, 
                :startdato, 
                :sluttdato, 
                :dagerPerUke, 
                :deltakelsesprosent, 
                :bakgrunnsinformasjon, 
                :innhold, 
                :historikk, 
                :kan_endres, 
                :modified_at,
                :er_manuelt_delt_med_arrangor
            )
            ON CONFLICT (id) DO UPDATE SET 
                person_id            = :person_id,
                startdato            = :startdato,
                sluttdato            = :sluttdato,
                dager_per_uke        = :dagerPerUke,
                deltakelsesprosent   = :deltakelsesprosent,
                bakgrunnsinformasjon = :bakgrunnsinformasjon,
                innhold              = :innhold,
                historikk            = :historikk,
                kan_endres           = :kan_endres,
                modified_at          = :modified_at,
                er_manuelt_delt_med_arrangor = :er_manuelt_delt_med_arrangor                
            """.trimIndent()

        val parameters = mapOf(
            "id" to deltaker.id,
            "person_id" to deltaker.navBruker.personId,
            "deltakerlisteId" to deltaker.deltakerliste.id,
            "startdato" to deltaker.startdato,
            "sluttdato" to deltaker.sluttdato,
            "dagerPerUke" to deltaker.dagerPerUke,
            "deltakelsesprosent" to deltaker.deltakelsesprosent,
            "bakgrunnsinformasjon" to deltaker.bakgrunnsinformasjon,
            "innhold" to toPGObject(deltaker.deltakelsesinnhold),
            "historikk" to toPGObject(deltaker.historikk),
            "kan_endres" to deltaker.kanEndres,
            "modified_at" to deltaker.sistEndret,
            "er_manuelt_delt_med_arrangor" to deltaker.erManueltDeltMedArrangor,
        )

        Database.query { session ->
            session.update(queryOf(sql, parameters))
        }
    }

    fun get(id: UUID): Result<Deltaker> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    getDeltakerSql("d.id = :id"),
                    mapOf("id" to id),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Ingen deltaker med id $id")
        }
    }

    fun getMany(ider: List<UUID>): List<Deltaker> {
        if (ider.isEmpty()) return emptyList()

        return Database.query { session ->
            session.run(
                queryOf(
                    getDeltakerSql("d.id = ANY(:ider)"),
                    mapOf("ider" to ider.toTypedArray()),
                ).map(::rowMapper).asList,
            )
        }
    }

    fun getMany(
        personident: String,
        deltakerlisteId: UUID,
    ): List<Deltaker> = Database.query { session ->
        session.run(
            queryOf(
                getDeltakerSql("nb.personident = :personident AND d.deltakerliste_id = :deltakerliste_id"),
                mapOf(
                    "personident" to personident,
                    "deltakerliste_id" to deltakerlisteId,
                ),
            ).map(::rowMapper).asList,
        )
    }

    fun getMany(personident: String): List<Deltaker> = Database.query { session ->
        session.run(
            queryOf(
                getDeltakerSql("nb.personident = :personident"),
                mapOf("personident" to personident),
            ).map(::rowMapper).asList,
        )
    }

    fun getKladderForDeltakerliste(deltakerlisteId: UUID): List<Deltaker> = Database.query { session ->
        session.run(
            queryOf(
                getDeltakerSql("d.deltakerliste_id = :deltakerliste_id AND ds.type = 'KLADD'"),
                mapOf("deltakerliste_id" to deltakerlisteId),
            ).map(::rowMapper).asList,
        )
    }

    fun getKladdForDeltakerliste(
        deltakerlisteId: UUID,
        personident: String,
    ): Result<Deltaker> = runCatching {
        val sql = getDeltakerSql(
            """
            d.deltakerliste_id = :deltakerliste_id
            AND nb.personident = :personident
            AND ds.type = 'KLADD'
            """.trimIndent(),
        )

        Database.query { session ->
            session.run(
                queryOf(
                    sql,
                    mapOf(
                        "deltakerliste_id" to deltakerlisteId,
                        "personident" to personident,
                    ),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Ingen kladd for deltakerliste $deltakerlisteId og personident")
        }
    }

    fun getTidligereAvsluttedeDeltakelser(deltakerId: UUID): List<UUID> {
        val sql =
            """
            SELECT d2.id
            FROM 
                deltaker d
                JOIN deltaker d2 ON 
                    d.person_id = d2.person_id
                    AND d.deltakerliste_id = d2.deltakerliste_id
                JOIN deltaker_status ds ON d2.id = ds.deltaker_id
            WHERE 
                d.id = ?
                AND d.kan_endres = TRUE
                AND ds.type in (${avsluttendeDeltakerStatuser.joinToString { "?" }})
                AND d2.id != d.id
            """.trimIndent()

        val query = queryOf(
            sql,
            deltakerId,
            *avsluttendeDeltakerStatuser.toTypedArray(),
        ).map { it.uuid("id") }.asList

        return Database.query { session -> session.run(query) }
    }

    fun getUtdaterteKladder(sistEndret: LocalDateTime): List<Deltaker> = Database.query { session ->
        session.run(
            queryOf(
                getDeltakerSql("ds.type = 'KLADD' AND d.modified_at < :sist_endret"),
                mapOf("sist_endret" to sistEndret),
            ).map(::rowMapper).asList,
        )
    }

    fun slettDeltaker(deltakerId: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM deltaker WHERE id = :deltaker_id",
                    mapOf("deltaker_id" to deltakerId),
                ),
            )
        }
    }

    fun settKanEndres(
        deltakerId: UUID,
        kanEndres: Boolean,
    ) {
        val sql =
            """
            UPDATE deltaker
            SET 
                kan_endres = :kan_endres, 
                modified_at = CURRENT_TIMESTAMP
            WHERE id = :deltaker_id
            """.trimIndent()

        val parameters = mapOf(
            "kan_endres" to kanEndres,
            "deltaker_id" to deltakerId,
        )

        Database.query { session -> session.update(queryOf(sql, parameters)) }
    }

    fun disableKanEndresMany(ider: List<UUID>) {
        if (ider.isEmpty()) return

        val sql =
            """
            UPDATE deltaker
            SET 
                kan_endres = FALSE, 
                modified_at = CURRENT_TIMESTAMP
            WHERE id = ANY(:ider)
            """.trimIndent()

        val parameters = mapOf("ider" to ider.toTypedArray())

        Database.query { session -> session.update(queryOf(sql, parameters)) }
    }

    fun update(deltaker: Deltakeroppdatering) {
        val params = mapOf(
            "id" to deltaker.id,
            "startdato" to deltaker.startdato,
            "sluttdato" to deltaker.sluttdato,
            "dagerPerUke" to deltaker.dagerPerUke,
            "deltakelsesprosent" to deltaker.deltakelsesprosent,
            "bakgrunnsinformasjon" to deltaker.bakgrunnsinformasjon,
            "innhold" to toPGObject(deltaker.deltakelsesinnhold),
            "historikk" to toPGObject(deltaker.historikk),
            "modified_at" to deltaker.sistEndret,
        )

        Database.query { session ->
            session.update(
                queryOf(
                    updateDeltakerSql(false),
                    params,
                ),
            )
        }
    }

    fun updateBatch(deltakere: List<Deltakeroppdatering>) {
        Database.query { session ->
            session.batchPreparedNamedStatement(
                updateDeltakerSql(true),
                deltakere.map { batchUpdateDeltakerParams(it) },
            )
        }
    }

    fun getForDeltakerliste(deltakerlisteId: UUID): List<Deltaker> = Database.query { session ->
        session.run(
            queryOf(
                getDeltakerSql("dl.id = :deltakerliste_id"),
                mapOf("deltakerliste_id" to deltakerlisteId),
            ).map(::rowMapper).asList,
        )
    }

    fun oppdaterSistBesokt(
        deltakerId: UUID,
        sistBesokt: ZonedDateTime,
    ) {
        val sql =
            """
            UPDATE deltaker 
            SET 
                sist_besokt = :sist_besokt, 
                modified_at = CURRENT_TIMESTAMP 
            WHERE id = :id
            """.trimIndent()

        val params = mapOf(
            "id" to deltakerId,
            "sist_besokt" to sistBesokt,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    companion object {
        private val avsluttendeDeltakerStatuser = AVSLUTTENDE_STATUSER.map { it.name }

        private fun getDeltakerSql(whereClause: String) =
            """
            SELECT 
                d.id AS "d.id",
                d.person_id AS "d.person_id",
                d.deltakerliste_id AS "d.deltakerliste_id",
                d.startdato AS "d.startdato",
                d.sluttdato AS "d.sluttdato",
                d.dager_per_uke AS "d.dager_per_uke",
                d.deltakelsesprosent AS "d.deltakelsesprosent",
                d.bakgrunnsinformasjon AS "d.bakgrunnsinformasjon",
                d.innhold AS "d.innhold",
                d.historikk AS "d.historikk",                
                d.kan_endres AS "d.kan_endres",
                d.er_manuelt_delt_med_arrangor AS "d.er_manuelt_delt_med_arrangor",
                d.created_at AS "d.created_at",
                d.modified_at AS "d.modified_at",

                nb.personident AS "nb.personident",
                nb.fornavn AS "nb.fornavn",
                nb.mellomnavn AS "nb.mellomnavn",
                nb.etternavn AS "nb.etternavn",
                nb.adressebeskyttelse AS "nb.adressebeskyttelse",
                nb.oppfolgingsperioder AS "nb.oppfolgingsperioder",
                nb.innsatsgruppe AS "nb.innsatsgruppe",
                nb.er_skjermet AS "nb.er_skjermet",
                nb.adresse AS "nb.adresse",
                nb.nav_enhet_id AS "nb.nav_enhet_id",
                nb.nav_veileder_id AS "nb.nav_veileder_id",

                ds.id AS "ds.id",
                ds.deltaker_id AS "ds.deltaker_id",
                ds.type AS "ds.type",
                ds.aarsak AS "ds.aarsak",
                ds.gyldig_fra AS "ds.gyldig_fra",
                ds.created_at AS "ds.created_at",

                dl.id AS "dl.id",
                dl.navn AS "dl.navn",
                dl.status AS "dl.status",
                dl.start_dato AS "dl.start_dato",
                dl.slutt_dato AS "dl.slutt_dato",
                dl.oppstart AS "dl.oppstart",
                dl.apent_for_pamelding AS "dl.apent_for_pamelding",
                dl.antall_plasser AS "dl.antall_plasser",
                dl.oppmote_sted AS "dl.oppmote_sted",
                dl.pameldingstype AS "dl.pameldingstype",
                a.id AS "a.id",
                a.navn AS "a.navn",
                a.organisasjonsnummer AS "a.organisasjonsnummer",
                a.overordnet_arrangor_id AS "a.overordnet_arrangor_id",
                oa.navn AS "oa.navn",
                t.id AS "t.id",
                t.navn AS "t.navn",
                t.tiltakskode AS "t.tiltakskode",
                t.innsatsgrupper AS "t.innsatsgrupper",
                t.innhold AS "t.innhold"
            FROM deltaker d 
                JOIN nav_bruker nb ON d.person_id = nb.person_id
                JOIN deltaker_status ds ON d.id = ds.deltaker_id
                JOIN deltakerliste dl ON d.deltakerliste_id = dl.id
                JOIN arrangor a ON a.id = dl.arrangor_id
                JOIN tiltakstype t ON t.id = dl.tiltakstype_id
                LEFT JOIN arrangor oa ON oa.id = a.overordnet_arrangor_id
            WHERE 
                $whereClause
            """.trimIndent()

        private fun batchUpdateDeltakerParams(deltaker: Deltakeroppdatering) = mapOf(
            "id" to deltaker.id,
            "startdato" to deltaker.startdato,
            "sluttdato" to deltaker.sluttdato,
            "dagerPerUke" to deltaker.dagerPerUke,
            "deltakelsesprosent" to deltaker.deltakelsesprosent,
            "bakgrunnsinformasjon" to deltaker.bakgrunnsinformasjon,
            "innhold" to toPGObject(deltaker.deltakelsesinnhold),
            "historikk" to toPGObject(deltaker.historikk),
            "modified_at" to deltaker.sistEndret,
            "er_manuelt_delt_med_arrangor" to deltaker.erManueltDeltMedArrangor,
        )

        private fun updateDeltakerSql(erBatchUpdate: Boolean): String =
            """
            UPDATE deltaker SET 
                startdato            = :startdato,
                sluttdato            = :sluttdato,
                dager_per_uke        = :dagerPerUke,
                deltakelsesprosent   = :deltakelsesprosent,
                bakgrunnsinformasjon = :bakgrunnsinformasjon,
                innhold              = :innhold,
                historikk            = :historikk,
                ${if (erBatchUpdate) "er_manuelt_delt_med_arrangor = :er_manuelt_delt_med_arrangor," else ""}
                modified_at          = :modified_at
            WHERE id = :id
            """.trimIndent()

        private fun mapDeltakerStatus(row: Row) = DeltakerStatus(
            id = row.uuid("ds.id"),
            type = DeltakerStatus.Type.valueOf(row.string("ds.type")),
            aarsak = row.stringOrNull("ds.aarsak")?.let { objectMapper.readValue(it) },
            gyldigFra = row.localDateTime("ds.gyldig_fra"),
            gyldigTil = null,
            opprettet = row.localDateTime("ds.created_at"),
        )

        private fun rowMapper(row: Row) = Deltaker(
            id = row.uuid("d.id"),
            navBruker = NavBruker(
                personId = row.uuid("d.person_id"),
                personident = row.string("nb.personident"),
                fornavn = row.string("nb.fornavn"),
                mellomnavn = row.stringOrNull("nb.mellomnavn"),
                etternavn = row.string("nb.etternavn"),
                adressebeskyttelse = row.stringOrNull("nb.adressebeskyttelse")?.let { Adressebeskyttelse.valueOf(it) },
                oppfolgingsperioder = row.stringOrNull("nb.oppfolgingsperioder")?.let { objectMapper.readValue(it) } ?: emptyList(),
                innsatsgruppe = row.stringOrNull("nb.innsatsgruppe")?.let { Innsatsgruppe.valueOf(it) },
                adresse = row.stringOrNull("nb.adresse")?.let { objectMapper.readValue(it) },
                erSkjermet = row.boolean("nb.er_skjermet"),
                navEnhetId = row.uuidOrNull("nb.nav_enhet_id"),
                navVeilederId = row.uuidOrNull("nb.nav_veileder_id"),
                telefon = null,
                epost = null,
            ),
            deltakerliste = DeltakerlisteRepository.rowMapper(row),
            startdato = row.localDateOrNull("d.startdato"),
            sluttdato = row.localDateOrNull("d.sluttdato"),
            dagerPerUke = row.floatOrNull("d.dager_per_uke"),
            deltakelsesprosent = row.floatOrNull("d.deltakelsesprosent"),
            bakgrunnsinformasjon = row.stringOrNull("d.bakgrunnsinformasjon"),
            deltakelsesinnhold = row.stringOrNull("d.innhold")?.let { objectMapper.readValue(it) },
            status = mapDeltakerStatus(row),
            historikk = objectMapper.readValue(row.string("d.historikk").trim()),
            kanEndres = row.boolean("d.kan_endres"),
            erManueltDeltMedArrangor = row.boolean("d.er_manuelt_delt_med_arrangor"),
            opprettet = row.localDateTime("d.created_at"),
            sistEndret = row.localDateTime("d.modified_at"),
        )
    }
}
