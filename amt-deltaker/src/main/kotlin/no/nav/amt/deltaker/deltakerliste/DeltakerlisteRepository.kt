package no.nav.amt.deltaker.deltakerliste

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.utils.prefixColumn
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.util.UUID

class DeltakerlisteRepository {
    private val log = LoggerFactory.getLogger(javaClass)

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
                :arrangor_id,
                :tiltakstype_id,
                :start_dato,
                :slutt_dato,
                :oppstart,
                :apent_for_pamelding,
                :oppmote_sted,
                :pameldingstype
            )
            ON CONFLICT (id) DO UPDATE SET
                navn     				= :navn,
                gjennomforingstype      = :gjennomforingstype,
                status					= :status,
                arrangor_id 			= :arrangor_id,
                tiltakstype_id			= :tiltakstype_id,
                start_dato				= :start_dato,
                slutt_dato				= :slutt_dato,
                oppstart                = :oppstart,
                apent_for_pamelding     = :apent_for_pamelding,
                oppmote_sted            = :oppmote_sted,
                pameldingstype          = :pameldingstype,
                modified_at             = CURRENT_TIMESTAMP
            """.trimIndent()

        val params = mapOf(
            "id" to deltakerliste.id,
            "navn" to deltakerliste.navn,
            "gjennomforingstype" to deltakerliste.gjennomforingstype.name,
            "status" to deltakerliste.status?.name,
            "arrangor_id" to deltakerliste.arrangor.id,
            "tiltakstype_id" to deltakerliste.tiltakstype.id,
            "start_dato" to deltakerliste.startDato,
            "slutt_dato" to deltakerliste.sluttDato,
            "oppstart" to deltakerliste.oppstart?.name,
            "apent_for_pamelding" to deltakerliste.apentForPamelding,
            "oppmote_sted" to deltakerliste.oppmoteSted,
            "pameldingstype" to deltakerliste.pameldingstype?.name,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
        log.info("Upsertet deltakerliste med id ${deltakerliste.id}")
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
               dl.oppstart AS "dl.oppstart",
               dl.apent_for_pamelding AS "dl.apent_for_pamelding",
               dl.oppmote_sted AS "dl.oppmote_sted",
               dl.pameldingstype AS "dl.pameldingstype",
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
                JOIN arrangor a ON a.id = dl.arrangor_id
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

    companion object {
        private val col = prefixColumn("dl")

        fun rowMapper(row: Row): Deltakerliste = Deltakerliste(
            id = row.uuid(col("id")),
            tiltakstype = TiltakstypeRepository.rowMapper(row, "t"),
            navn = row.string(col("navn")),
            gjennomforingstype = GjennomforingType.valueOf(row.string(col("gjennomforingstype"))),
            status = row.stringOrNull(col("status"))?.let { GjennomforingStatusType.valueOf(it) },
            startDato = row.localDateOrNull(col("start_dato")),
            sluttDato = row.localDateOrNull(col("slutt_dato")),
            oppstart = row.stringOrNull(col("oppstart"))?.let { Oppstartstype.valueOf(it) },
            apentForPamelding = row.boolean(col("apent_for_pamelding")),
            oppmoteSted = row.stringOrNull(col("oppmote_sted")),
            pameldingstype = row.stringOrNull(col("pameldingstype"))?.let { GjennomforingPameldingType.valueOf(it) },
            arrangor = Arrangor(
                id = row.uuid("a.id"),
                navn = row.string("a.navn"),
                organisasjonsnummer = row.string("a.organisasjonsnummer"),
                overordnetArrangorId = row.uuidOrNull("a.overordnet_arrangor_id"),
            ),
        )
    }
}
