package no.nav.amt.deltaker.repository

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.enkeltplass.EnkeltplassGjennomforingUpdateDbo
import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.repository.dbo.GjennomforingInsertDbo
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.utils.prefixColumn
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.Period
import java.util.UUID

class DeltakerlisteRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    // blir brukt av GjennomforingConsumer
    fun upsert(deltakerliste: Deltakerliste) {
        val sql =
            """
            INSERT INTO deltakerliste(
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
                prisinformasjon
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
                :prisinformasjon
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
                prisinformasjon         = :prisinformasjon,
                modified_at             = CURRENT_TIMESTAMP
            """.trimIndent()

        val params = mapOf(
            "id" to deltakerliste.id,
            "navn" to deltakerliste.navn,
            "gjennomforingstype" to deltakerliste.gjennomforingstype.name,
            "status" to deltakerliste.status.name,
            "arrangor_id" to deltakerliste.arrangor?.id,
            "tiltakstype_id" to deltakerliste.tiltakstype.id,
            "start_dato" to deltakerliste.startDato,
            "slutt_dato" to deltakerliste.sluttDato,
            "antall_plasser" to deltakerliste.antallPlasser,
            "oppstart" to deltakerliste.oppstart.name,
            "apent_for_pamelding" to deltakerliste.apentForPamelding,
            "oppmote_sted" to deltakerliste.oppmoteSted,
            "prisinformasjon" to deltakerliste.prisinformasjon,
            "pameldingstype" to deltakerliste.pameldingstype.name,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
        log.info("Upsertet deltakerliste med id ${deltakerliste.id}")
    }

    // blir brukt av EnkeltplassService
    fun upsert(gjennomforing: GjennomforingInsertDbo) {
        val sql =
            """
            INSERT INTO deltakerliste(
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
            UPDATE deltakerliste
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
                statement = "DELETE FROM deltakerliste WHERE id = :id",
                paramMap = mapOf("id" to id),
            ),
        )
        log.info("Slettet deltakerliste med id $id")
    }

    fun get(id: UUID): Result<Deltakerliste> = runCatching {
        val sql =
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
               dl.prisinformasjon as "dl.prisinformasjon",
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
                deltakerliste dl
                LEFT JOIN arrangor a ON a.id = dl.arrangor_id
                JOIN tiltakstype t ON t.id = dl.tiltakstype_id
            WHERE dl.id = :id
            """.trimIndent()

        Database.query { session ->
            session.run(
                queryOf(
                    sql,
                    mapOf("id" to id),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke deltakerliste med id $id")
        }
    }

    fun verifiserTilgjengeligDeltakerliste(id: UUID): Deltakerliste {
        val deltakerliste = get(id).getOrThrow()

        deltakerliste.sluttDato?.let { sluttdato ->
            if (LocalDate.now().isAfter(sluttdato.plus(tiltakskoordinatorGraceperiode))) {
                throw DeltakerlisteStengtException("Deltakerlisten $id er stengt for tiltakskoordinator")
            }
        }

        return deltakerliste
    }

    companion object {
        val tiltakskoordinatorGraceperiode: Period = Period.ofDays(14)
        private val col = prefixColumn("dl")

        fun rowMapper(row: Row): Deltakerliste {
            val id = row.uuid(col("id"))
            val gjennomforingstype = GjennomforingType.valueOf(row.string(col("gjennomforingstype")))

            // Arena enkeltplasser har i praksis ikke kategoriseringer men skal ha det etter hvert
            val opplaringKategorisering = if (gjennomforingstype == GjennomforingType.Enkeltplass) {
                // TODO: fikse dette med join isteden
                OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(id)
            } else {
                null
            }

            return Deltakerliste(
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
                prisinformasjon = row.stringOrNull(col("prisinformasjon")),
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
