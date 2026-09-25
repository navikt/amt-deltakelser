package no.nav.amt.deltaker.bff.tiltak

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.bff.model.Tiltak
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory

class TiltakRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    fun upsert(tiltak: Tiltak) {
        val sql =
            """
            INSERT INTO tiltakstype (
                id, 
                navn, 
                tiltakskode
            )
            VALUES (
                :id,
                :navn,
                :tiltakskode
            )
            ON CONFLICT (id) DO UPDATE SET
                navn     		    = :navn,
                tiltakskode         = :tiltakskode,
                modified_at         = CURRENT_TIMESTAMP
            """.trimIndent()

        Database.query { session ->
            session.update(
                queryOf(
                    sql,
                    mapOf(
                        "id" to tiltak.id,
                        "navn" to tiltak.navn,
                        "tiltakskode" to tiltak.tiltakskode.name,
                    ),
                ),
            )
        }

        log.info("Upsertet tiltakstype med id ${tiltak.id}")
    }

    fun get(tiltakskode: Tiltakskode): Result<Tiltak> = runCatching {
        val query = queryOf(
            """
            SELECT 
                id,
                navn,
                tiltakskode
            FROM tiltakstype
            WHERE tiltakskode = :tiltakskode
            """.trimIndent(),
            mapOf("tiltakskode" to tiltakskode.name),
        ).map(::rowMapper).asSingle

        Database.query { session ->
            session.run(query)
                ?: throw NoSuchElementException("Fant ikke tiltakstype ${tiltakskode.name}")
        }
    }

    companion object {
        fun rowMapper(
            row: Row,
            alias: String? = null,
        ): Tiltak {
            val prefix = alias?.let { "$alias." } ?: ""
            val col = { label: String -> prefix + label }

            return Tiltak(
                id = row.uuid(col("id")),
                navn = row.string(col("navn")),
                tiltakskode = Tiltakskode.valueOf(row.string(col("tiltakskode"))),
            )
        }
    }
}
