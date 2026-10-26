package no.nav.amt.deltaker.repository

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.enkeltplass.EnkeltplassGjennomforingUpdateDbo
import no.nav.amt.deltaker.model.Gjennomforing
import no.nav.amt.deltaker.repository.dbo.GjennomforingInsertDbo
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.utils.prefixColumn
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.util.UUID

class GjennomforingRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    // blir brukt av GjennomforingConsumer
    fun upsert(gjennomforing: Gjennomforing) {
        val sql =
            """
            INSERT INTO gjennomforing(
                id, 
                navn, 
                gjennomforingstype,
                status, 
                arrangor_id,  
                tiltakstype_id, 
                start_dato, 
                slutt_dato,
                antall_plasser,
                oppstart,
                apent_for_pamelding,
                oppmote_sted,
                pameldingstype,
                lopenummer,
                tilgjengelig_fom
            )
            VALUES (
                :id,
                :navn,
                :gjennomforingstype,
                :status,
                :arrangor_id,
                :tiltakstype_id,
                :start_dato,
                :slutt_dato,
                :antall_plasser,
                :oppstart,
                :apent_for_pamelding,
                :oppmote_sted,
                :pameldingstype,
                :lopenummer,
                :tilgjengelig_fom
            )
            ON CONFLICT (id) DO UPDATE SET
                navn     				= :navn,
                gjennomforingstype      = :gjennomforingstype,
                status					= :status,
                arrangor_id 			= :arrangor_id,
                tiltakstype_id			= :tiltakstype_id,
                start_dato				= :start_dato,
                slutt_dato				= :slutt_dato,
                antall_plasser          = :antall_plasser,
                oppstart                = :oppstart,
                apent_for_pamelding     = :apent_for_pamelding,
                oppmote_sted            = :oppmote_sted,
                pameldingstype          = :pameldingstype,
                lopenummer              = :lopenummer,
                tilgjengelig_fom        = :tilgjengelig_fom,
                modified_at             = CURRENT_TIMESTAMP
            """.trimIndent()

        val params = mapOf(
            "id" to gjennomforing.id,
            "navn" to gjennomforing.navn,
            "gjennomforingstype" to gjennomforing.gjennomforingstype.name,
            "status" to gjennomforing.status.name,
            "arrangor_id" to gjennomforing.arrangor?.id,
            "tiltakstype_id" to gjennomforing.tiltakstype.id,
            "start_dato" to gjennomforing.startDato,
            "slutt_dato" to gjennomforing.sluttDato,
            "antall_plasser" to gjennomforing.antallPlasser,
            "oppstart" to gjennomforing.oppstart.name,
            "apent_for_pamelding" to gjennomforing.apentForPamelding,
            "oppmote_sted" to gjennomforing.oppmoteSted,
            "pameldingstype" to gjennomforing.pameldingstype.name,
            "lopenummer" to gjennomforing.lopenummer,
            "tilgjengelig_fom" to gjennomforing.tilgjengeligForArrangorFraOgMedDato,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
        log.info("Upsertet deltakerliste med id ${gjennomforing.id}")
    }

    // blir brukt av EnkeltplassService
    fun upsert(gjennomforing: GjennomforingInsertDbo) {
        val sql =
            """
            INSERT INTO gjennomforing(
                id, 
                navn, 
                gjennomforingstype,
                status, 
                tiltakstype_id, 
                oppstart,
                apent_for_pamelding,
                oppmote_sted,
                pameldingstype
            )
            VALUES (
                :id,
                :navn,
                :gjennomforingstype,
                :status,
                :tiltakstype_id,
                :oppstart,
                :apent_for_pamelding,
                :oppmote_sted,
                :pameldingstype
            )
            ON CONFLICT (id) DO UPDATE SET
                navn     				= :navn,
                gjennomforingstype      = :gjennomforingstype,
                status					= :status,
                tiltakstype_id			= :tiltakstype_id,
                oppstart                = :oppstart,
                apent_for_pamelding     = :apent_for_pamelding,
                pameldingstype          = :pameldingstype,
                modified_at             = CURRENT_TIMESTAMP
            """.trimIndent()

        val params = mapOf(
            "id" to gjennomforing.id,
            "navn" to gjennomforing.navn,
            "gjennomforingstype" to gjennomforing.type.name,
            "status" to gjennomforing.status?.name,
            "tiltakstype_id" to gjennomforing.tiltakId,
            "oppstart" to gjennomforing.oppstart?.name,
            "apent_for_pamelding" to gjennomforing.apentForPamelding,
            "pameldingstype" to gjennomforing.pameldingstype?.name,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
        log.info("Upsertet gjennomføring med id ${gjennomforing.id}")
    }

    fun update(gjennomforingUpdateDbo: EnkeltplassGjennomforingUpdateDbo) {
        val sql =
            """
            UPDATE gjennomforing
            SET 
                arrangor_id 			= :arrangor_id,
                modified_at             = CURRENT_TIMESTAMP
            WHERE id = :id
            """.trimIndent()

        val params = mapOf(
            "id" to gjennomforingUpdateDbo.id,
            "arrangor_id" to gjennomforingUpdateDbo.arrangorId,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
        log.info("Oppdaterte gjennomføring kladd med id ${gjennomforingUpdateDbo.id}")
    }

    fun delete(id: UUID) = Database.query {
        it.update(
            queryOf(
                statement = "DELETE FROM gjennomforing WHERE id = :id",
                paramMap = mapOf("id" to id),
            ),
        )
        log.info("Slettet deltakerliste med id $id")
    }

    fun get(id: UUID): Gjennomforing? {
        val sql = "$BASE_SELECT WHERE dl.id = :id"

        return Database.query { session ->
            session.run(
                queryOf(
                    sql,
                    mapOf("id" to id),
                ).map(::rowMapper).asSingle,
            )
        }
    }

    fun getManyForTiltakstype(tiltakstypeId: UUID): List<Gjennomforing> {
        val sql = "$BASE_SELECT WHERE dl.tiltakstype_id = :tiltakstype_id"

        return Database.query { session ->
            session.run(
                queryOf(
                    sql,
                    mapOf("tiltakstype_id" to tiltakstypeId),
                ).map(::rowMapperUtenOpplaringKategorisering).asList,
            )
        }
    }

    companion object {
        private val col = prefixColumn("dl")

        private val BASE_SELECT =
            """
            SELECT 
               dl.id AS "dl.id",
               dl.navn AS "dl.navn",
               dl.gjennomforingstype AS "dl.gjennomforingstype",
               dl.status AS "dl.status",
               dl.start_dato AS "dl.start_dato",
               dl.slutt_dato AS "dl.slutt_dato",
               dl.antall_plasser AS "dl.antall_plasser",
               dl.oppstart AS "dl.oppstart",
               dl.apent_for_pamelding AS "dl.apent_for_pamelding",
               dl.oppmote_sted AS "dl.oppmote_sted",
               dl.pameldingstype AS "dl.pameldingstype",
               dl.lopenummer AS "dl.lopenummer",
               dl.tilgjengelig_fom AS "dl.tilgjengelig_fom",
               a.id AS "a.id",
               a.navn AS "a.navn",
               a.organisasjonsnummer AS "a.organisasjonsnummer",
               a.overordnet_arrangor_id AS "a.overordnet_arrangor_id",
               t.id AS "t.id",
               t.navn AS "t.navn",
               t.tiltakskode AS "t.tiltakskode",
               t.innsatsgrupper AS "t.innsatsgrupper",
               t.innhold AS "t.innhold"
            FROM 
                gjennomforing dl
                LEFT JOIN arrangor a ON a.id = dl.arrangor_id
                JOIN tiltakstype t ON t.id = dl.tiltakstype_id
            """.trimIndent()

        fun rowMapper(row: Row): Gjennomforing {
            val id = row.uuid(col("id"))
            val gjennomforingstype = GjennomforingType.valueOf(row.string(col("gjennomforingstype")))

            // Arena enkeltplasser har i praksis ikke kategoriseringer men skal ha det etter hvert
            val opplaringKategorisering = if (gjennomforingstype == GjennomforingType.Enkeltplass) {
                // TODO: fikse dette med join isteden
                OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(id)
            } else {
                null
            }

            return mapDeltakerliste(row, opplaringKategorisering)
        }

        /**
         * Lettvekts-mapper for reproduksjons-stien (amt.gjennomforing-intern): utelater
         * opplæringskategorisering, som ellers utløser to ekstra spørringer per Enkeltplass og gir
         * N+1 ved reproduksjon av mange gjennomføringer. [toAmtGjennomforingPayload] bruker ikke
         * feltet, så det er trygt å hoppe over her.
         */
        fun rowMapperUtenOpplaringKategorisering(row: Row): Gjennomforing = mapDeltakerliste(row, opplaringKategorisering = null)

        private fun mapDeltakerliste(
            row: Row,
            opplaringKategorisering: OpplaringKategoriseringValg?,
        ): Gjennomforing {
            val id = row.uuid(col("id"))
            val gjennomforingstype = GjennomforingType.valueOf(row.string(col("gjennomforingstype")))

            return Gjennomforing(
                id = id,
                tiltakstype = TiltakRepository.rowMapper(row, "t"),
                navn = row.string(col("navn")),
                gjennomforingstype = gjennomforingstype,
                status = row.string(col("status")).let { GjennomforingStatusType.valueOf(it) },
                startDato = row.localDateOrNull(col("start_dato")),
                sluttDato = row.localDateOrNull(col("slutt_dato")),
                oppstart = row.string(col("oppstart")).let { Oppstartstype.valueOf(it) },
                apentForPamelding = row.boolean(col("apent_for_pamelding")),
                oppmoteSted = row.stringOrNull(col("oppmote_sted")),
                pameldingstype = row.string(col("pameldingstype")).let { GjennomforingPameldingType.valueOf(it) },
                lopenummer = row.stringOrNull(col("lopenummer")),
                tilgjengeligForArrangorFraOgMedDato = row.localDateOrNull(col("tilgjengelig_fom")),
                antallPlasser = row.intOrNull(col("antall_plasser")),
                arrangor = row.uuidOrNull("a.id")?.let { arrangorId ->
                    Arrangor(
                        id = arrangorId,
                        navn = row.string("a.navn"),
                        organisasjonsnummer = row.string("a.organisasjonsnummer"),
                        overordnetArrangorId = row.uuidOrNull("a.overordnet_arrangor_id"),
                    )
                },
                opplaringKategorisering = opplaringKategorisering,
            )
        }
    }
}
