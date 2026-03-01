package no.nav.amt.deltaker.deltaker.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.deltaker.db.DbUtils.sqlPlaceholders
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.IKKE_AVSLUTTENDE_STATUSER
import no.nav.amt.deltaker.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import java.util.UUID

class DeltakerRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getKladdForDeltakerliste(deltakerlisteId: UUID, personident: String): Result<Deltaker> = runCatching {
        val sql = buildDeltakerSql(
            "getKladdForDeltakerliste",
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
                ).map(::deltakerRowMapper).asSingle,
            ) ?: throw NoSuchElementException("Ingen deltaker med deltakerlisteId $deltakerlisteId og personident")
        }
    }

    fun getAntallDeltakereForDeltakerliste(deltakerlisteId: UUID): Int = Database.query { session ->
        session.run(
            queryOf(
                "SELECT COUNT(*) FROM deltaker WHERE deltakerliste_id = :deltakerliste_id",
                mapOf("deltakerliste_id" to deltakerlisteId),
            ).map { it.int(1) }.asSingle,
        ) ?: 0
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
                kilde, 
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
                :kilde, 
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
                kilde                = :kilde,
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
            "kilde" to deltaker.kilde.name,
            "modified_at" to deltaker.sistEndret,
            "er_manuelt_delt_med_arrangor" to deltaker.erManueltDeltMedArrangor,
        )

        Database.query { session -> session.update(queryOf(sql, parameters)) }
        log.info("Opprettet/oppdaterte deltaker med id ${deltaker.id}")
    }

    fun get(id: UUID): Result<Deltaker> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    buildDeltakerSql(
                        methodName = "get",
                        whereClause = "d.id = :id",
                        limit = null,
                    ),
                    mapOf("id" to id),
                ).map(::deltakerRowMapper).asSingle,
            ) ?: throw NoSuchElementException("Ingen deltaker med id $id")
        }
    }

    fun getMany(deltakerIder: Set<UUID>): List<Deltaker> {
        if (deltakerIder.isEmpty()) return emptyList()

        return Database.query { session ->
            session.run(
                queryOf(
                    buildDeltakerSql(
                        methodName = "getMany",
                        whereClause = "d.id IN (${sqlPlaceholders(deltakerIder.size)})",
                        limit = deltakerIder.size,
                    ),
                    *deltakerIder.toTypedArray(),
                ).map(::deltakerRowMapper).asList,
            )
        }
    }

    fun getFlereForPerson(personIdent: String): List<Deltaker> = Database.query { session ->
        session.run(
            queryOf(
                buildDeltakerSql("getFlereForPerson", "nb.personident = :personident"),
                mapOf("personident" to personIdent),
            ).map(::deltakerRowMapper).asList,
        )
    }

    fun getFlereForPerson(personIdent: String, deltakerlisteId: UUID): List<Deltaker> = Database.query { session ->
        session.run(
            queryOf(
                buildDeltakerSql(
                    "getFlereForPersonDeltakerliste",
                    "nb.personident = :personident AND d.deltakerliste_id = :deltakerliste_id",
                ),
                mapOf(
                    "personident" to personIdent,
                    "deltakerliste_id" to deltakerlisteId,
                ),
            ).map(::deltakerRowMapper).asList,
        )
    }

    fun getDeltakerHvorSluttdatoSkalEndres(deltakerlisteId: UUID): List<Deltaker> = Database.query { session ->
        session.run(
            queryOf(
                buildDeltakerSql(
                    methodName = "getDeltakerHvorSluttdatoSkalEndres",
                    whereClause =
                        """
                        d.deltakerliste_id = :deltakerliste_id 
                        AND ds.type IN ($IKKE_AVSLUTTENDE_STATUSER_DELIMITED)
                        AND d.sluttdato IS NOT NULL
                        AND dl.slutt_dato IS NOT NULL
                        AND d.sluttdato > dl.slutt_dato
                        """.trimIndent(),
                ),
                mapOf("deltakerliste_id" to deltakerlisteId),
            ).map(::deltakerRowMapper).asList,
        )
    }

    fun getDeltakereForAvsluttetDeltakerliste(deltakerListeId: UUID): List<Deltaker> = Database.query { session ->
        session.run(
            queryOf(
                buildDeltakerSql(
                    methodName = "getDeltakereForAvbruttDeltakerliste",
                    whereClause = "d.deltakerliste_id = :deltakerliste_id AND ds.type != '${DeltakerStatus.Type.KLADD.name}'",
                    limit = 5_000, // enkelte deltakerlister kan inneholde mange deltakere
                ),
                mapOf("deltakerliste_id" to deltakerListeId),
            ).map(::deltakerRowMapper).asList,
        )
    }

    fun getDeltakerIderForTiltakskode(tiltakskode: Tiltakskode): List<UUID> {
        val sql =
            """ 
            SELECT d.id
            FROM 
                deltaker d
                JOIN deltakerliste dl ON d.deltakerliste_id = dl.id
                JOIN tiltakstype t ON t.id = dl.tiltakstype_id
            WHERE t.tiltakskode = :tiltakskode
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(
                    sql,
                    mapOf("tiltakskode" to tiltakskode.name),
                ).map { it.uuid("id") }.asList,
            )
        }
    }

    fun skalHaStatusDeltar(): List<Deltaker> {
        val sql = buildDeltakerSql(
            "skalHaStatusDeltar",
            """
            ds.type = '${DeltakerStatus.Type.VENTER_PA_OPPSTART.name}'
            AND d.startdato <= CURRENT_DATE
            AND (d.sluttdato IS NULL OR d.sluttdato >= CURRENT_DATE)
            """.trimIndent(),
        )

        return Database.query { session ->
            session.run(queryOf(sql).map(::deltakerRowMapper).asList)
        }
    }

    fun getDeltakereHvorSluttdatoHarPassert(): List<Deltaker> {
        val sql = buildDeltakerSql(
            "getSluttdatoHarPassert",
            """
            ds.type IN ($SLUTTDATO_PASSERT_STATUSER_DELIMITED)
            AND d.sluttdato < CURRENT_DATE
            """.trimIndent(),
        )

        return Database.query { session ->
            session.run(queryOf(sql).map(::deltakerRowMapper).asList)
        }
    }

    fun getDeltakereSomDeltarPaAvsluttetDeltakerliste(): List<Deltaker> {
        val sql = buildDeltakerSql(
            "getDeltakereSomDeltar",
            """
            ds.type IN ($IKKE_AVSLUTTENDE_STATUSER_DELIMITED)
            AND dl.status IN ($AVSLUTTENDE_DELTAKERLISTE_STATUSER_DELIMITED)
            """.trimIndent(),
        )

        return Database.query { session ->
            session.run(queryOf(sql).map(::deltakerRowMapper).asList)
        }
    }

    fun getDeltakereMedStatus(statusType: DeltakerStatus.Type): List<UUID> {
        val sql =
            """
            SELECT d.id
            FROM 
                deltaker d
                JOIN deltaker_status ds ON d.id = ds.deltaker_id
            WHERE 
                ds.type = :status_type
                AND ds.gyldig_til IS NULL
                AND ds.gyldig_fra < CURRENT_TIMESTAMP
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(
                    sql,
                    mapOf("status_type" to statusType.name),
                ).map { it.uuid("id") }.asList,
            )
        }
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

    companion object {
        private val IKKE_AVSLUTTENDE_STATUSER_DELIMITED = IKKE_AVSLUTTENDE_STATUSER.joinToString { "'${it.name}'" }

        private val SLUTTDATO_PASSERT_STATUSER_DELIMITED = setOf(
            DeltakerStatus.Type.VENTER_PA_OPPSTART,
            DeltakerStatus.Type.DELTAR,
        ).joinToString { "'${it.name}'" }

        private val AVSLUTTENDE_DELTAKERLISTE_STATUSER_DELIMITED = setOf(
            GjennomforingStatusType.AVSLUTTET,
            GjennomforingStatusType.AVBRUTT,
            GjennomforingStatusType.AVLYST,
        ).joinToString { "'${it.name}'" }

        private fun deltakerRowMapper(row: Row): Deltaker {
            val status = DeltakerStatus.Type.valueOf(row.string("ds.type"))

            val deltaker = Deltaker(
                id = row.uuid("d.id"),
                navBruker = NavBruker(
                    personId = row.uuid("d.person_id"),
                    personident = row.string("nb.personident"),
                    fornavn = row.string("nb.fornavn"),
                    mellomnavn = row.stringOrNull("nb.mellomnavn"),
                    etternavn = row.string("nb.etternavn"),
                    navVeilederId = row.uuidOrNull("nb.nav_veileder_id"),
                    navEnhetId = row.uuidOrNull("nb.nav_enhet_id"),
                    telefon = row.stringOrNull("nb.telefonnummer"),
                    epost = row.stringOrNull("nb.epost"),
                    erSkjermet = row.boolean("nb.er_skjermet"),
                    adresse = row.stringOrNull("nb.adresse")?.let { objectMapper.readValue(it) },
                    adressebeskyttelse = row.stringOrNull("nb.adressebeskyttelse")?.let { Adressebeskyttelse.valueOf(it) },
                    oppfolgingsperioder = row.stringOrNull("nb.oppfolgingsperioder")?.let { objectMapper.readValue(it) } ?: emptyList(),
                    innsatsgruppe = row.stringOrNull("nb.innsatsgruppe")?.let { Innsatsgruppe.valueOf(it) },
                ),
                deltakerliste = DeltakerlisteRepository.rowMapper(row),
                startdato = row.localDateOrNull("d.startdato"),
                sluttdato = row.localDateOrNull("d.sluttdato"),
                dagerPerUke = row.floatOrNull("d.dager_per_uke"),
                deltakelsesprosent = row.floatOrNull("d.deltakelsesprosent"),
                bakgrunnsinformasjon = row.stringOrNull("d.bakgrunnsinformasjon"),
                deltakelsesinnhold = row.stringOrNull("d.innhold")?.let { objectMapper.readValue(it) },
                status = DeltakerStatus(
                    id = row.uuid("ds.id"),
                    type = status,
                    aarsak = row.stringOrNull("ds.aarsak")?.let { objectMapper.readValue(it) },
                    gyldigFra = row.localDateTime("ds.gyldig_fra"),
                    gyldigTil = row.localDateTimeOrNull("ds.gyldig_til"),
                    opprettet = row.localDateTime("ds.created_at"),
                ),
                vedtaksinformasjon = row.localDateTimeOrNull("v.opprettet")?.let { opprettet ->
                    Vedtaksinformasjon(
                        fattet = row.localDateTimeOrNull("v.fattet"),
                        fattetAvNav = row.boolean("v.fattet_av_nav"),
                        opprettet = opprettet,
                        opprettetAv = row.uuid("v.opprettet_av"),
                        opprettetAvEnhet = row.uuid("v.opprettet_av_enhet"),
                        sistEndret = row.localDateTime("v.sist_endret"),
                        sistEndretAv = row.uuid("v.sist_endret_av"),
                        sistEndretAvEnhet = row.uuid("v.sist_endret_av_enhet"),
                    )
                },
                sistEndret = row.localDateTime("d.modified_at"),
                kilde = Kilde.valueOf(row.string("d.kilde")),
                erManueltDeltMedArrangor = row.boolean("d.er_manuelt_delt_med_arrangor"),
                opprettet = row.localDateTime("d.created_at"),
            )

            return if (status == DeltakerStatus.Type.FEILREGISTRERT) {
                deltaker.copy(
                    startdato = null,
                    sluttdato = null,
                    dagerPerUke = null,
                    deltakelsesprosent = null,
                    bakgrunnsinformasjon = null,
                    deltakelsesinnhold = null,
                )
            } else {
                deltaker
            }
        }

        private fun buildDeltakerSql(
            methodName: String,
            whereClause: String,
            limit: Int? = 500,
        ): String = """
        SELECT 
            1 AS "$methodName",
            d.id AS "d.id",
            d.person_id AS "d.person_id",
            d.deltakerliste_id AS "d.deltakerliste_id",
            d.startdato AS "d.startdato",
            d.sluttdato AS "d.sluttdato",
            d.dager_per_uke AS "d.dager_per_uke",
            d.deltakelsesprosent AS "d.deltakelsesprosent",
            d.bakgrunnsinformasjon AS "d.bakgrunnsinformasjon",
            d.innhold AS "d.innhold",
            d.modified_at AS "d.modified_at",
            d.kilde AS "d.kilde",
            d.er_manuelt_delt_med_arrangor AS "d.er_manuelt_delt_med_arrangor",
            d.created_at AS "d.created_at",
            nb.personident AS "nb.personident",
            nb.fornavn AS "nb.fornavn",
            nb.mellomnavn AS "nb.mellomnavn",
            nb.etternavn AS "nb.etternavn",
            nb.nav_veileder_id AS "nb.nav_veileder_id",
            nb.nav_enhet_id AS "nb.nav_enhet_id",
            nb.telefonnummer AS "nb.telefonnummer",
            nb.epost AS "nb.epost",
            nb.er_skjermet AS "nb.er_skjermet",
            nb.adresse AS "nb.adresse",
            nb.adressebeskyttelse AS "nb.adressebeskyttelse",
            nb.oppfolgingsperioder AS "nb.oppfolgingsperioder",
            nb.innsatsgruppe AS "nb.innsatsgruppe",
            ds.id AS "ds.id",
            ds.deltaker_id AS "ds.deltaker_id",
            ds.type AS "ds.type",
            ds.aarsak AS "ds.aarsak",
            ds.gyldig_fra AS "ds.gyldig_fra",
            ds.gyldig_til AS "ds.gyldig_til",
            ds.created_at AS "ds.created_at",
            ds.modified_at AS "ds.modified_at",
            dl.id AS "dl.id",
            dl.navn AS "dl.navn",
            dl.gjennomforingstype AS "dl.gjennomforingstype",
            dl.status AS "dl.status",
            dl.start_dato AS "dl.start_dato",
            dl.slutt_dato AS "dl.slutt_dato",
            dl.oppstart AS "dl.oppstart",
            dl.apent_for_pamelding AS "dl.apent_for_pamelding",
            dl.oppmote_sted AS "dl.oppmote_sted",
            dl.pameldingstype AS "dl.pameldingstype",
            a.navn AS "a.navn",
            a.id AS "a.id",
            a.organisasjonsnummer AS "a.organisasjonsnummer",
            a.overordnet_arrangor_id AS "a.overordnet_arrangor_id",
            t.id AS "t.id",
            t.navn AS "t.navn",
            t.tiltakskode AS "t.tiltakskode",
            t.innsatsgrupper AS "t.innsatsgrupper",
            t.innhold AS "t.innhold",
            v.fattet AS "v.fattet",
            v.fattet_av_nav AS "v.fattet_av_nav",
            v.created_at AS "v.opprettet",
            v.opprettet_av AS "v.opprettet_av",
            v.opprettet_av_enhet AS "v.opprettet_av_enhet",
            v.modified_at AS "v.sist_endret",
            v.sist_endret_av AS "v.sist_endret_av",
            v.sist_endret_av_enhet AS "v.sist_endret_av_enhet"
        FROM 
            deltaker d 
            JOIN nav_bruker nb ON d.person_id = nb.person_id
            JOIN deltaker_status ds ON 
                d.id = ds.deltaker_id
                AND ds.gyldig_til IS NULL 
                AND ds.gyldig_fra <= CURRENT_TIMESTAMP                
            JOIN deltakerliste dl ON d.deltakerliste_id = dl.id
            JOIN arrangor a ON a.id = dl.arrangor_id
            JOIN tiltakstype t ON t.id = dl.tiltakstype_id
            LEFT JOIN vedtak v ON 
                d.id = v.deltaker_id 
                AND v.gyldig_til IS NULL
            WHERE $whereClause
            ${limit?.let { "LIMIT $limit" } ?: ""}
      """
    }
}
