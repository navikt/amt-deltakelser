package no.nav.amt.deltaker.repository

import kotliquery.queryOf
import no.nav.amt.deltaker.repository.dbo.OpplaeringKategoriseringValgDbo
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

object OpplaeringKategoriseringValgRepository {
    fun deleteForGjennomforing(gjennomforingId: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM opplaering_kategorisering_valg WHERE deltakerliste_id = ?",
                    gjennomforingId,
                ),
            )
        }
    }

    fun insertKategoriseringValg(
        gjennomforingId: UUID,
        valg: List<OpplaeringKategoriseringValgDbo>,
    ) {
        if (valg.isEmpty()) return

        val sql =
            """
            INSERT INTO opplaering_kategorisering_valg (
                deltakerliste_id,
                representerer,
                kodeverk_id,
                tekst
            ) VALUES (
                :deltakerliste_id,
                :representerer,
                :kodeverk_id,
                :tekst
            )
            """.trimIndent()

        val params = valg.map { valgDbo ->
            mapOf(
                "deltakerliste_id" to gjennomforingId,
                "representerer" to valgDbo.representerer.name,
                "kodeverk_id" to valgDbo.kodeverkId,
                "tekst" to valgDbo.tekst,
            )
        }

        Database.query { session ->
            session.batchPreparedNamedStatement(sql, params)
        }
    }

    fun hentKategoriseringValg(gjennomforingId: UUID): List<OpplaeringKategoriseringValgDbo> {
        val sql =
            """
            SELECT 
                representerer,
                kodeverk_id,
                tekst
            FROM opplaering_kategorisering_valg
            WHERE deltakerliste_id = ?
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(sql, gjennomforingId)
                    .map { row ->
                        OpplaeringKategoriseringValgDbo(
                            representerer = OpplaringKategoriseringType.valueOf(row.string("representerer")),
                            kodeverkId = row.uuid("kodeverk_id"),
                            tekst = row.string("tekst"),
                        )
                    }.asList,
            )
        }
    }
}
