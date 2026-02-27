package no.nav.amt.deltaker.deltakerliste.tiltakstype

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.utils.prefixColumn
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory

class TiltakstypeRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    fun upsert(tiltakstype: Tiltakstype) {
        val sql =
            """
            INSERT INTO tiltakstype (
                id, 
                navn, 
                tiltakskode,
                innsatsgrupper,
                innhold
            )
            VALUES (
                :id,
                :navn,
                :tiltakskode,
                :innsatsgrupper,
                :innhold
            )
            ON CONFLICT (id) DO UPDATE SET
                navn     		    = :navn,
                tiltakskode         = :tiltakskode,
                innsatsgrupper		= :innsatsgrupper,
                innhold 			= :innhold,
                modified_at         = CURRENT_TIMESTAMP
            """.trimIndent()

        val params = mapOf(
            "id" to tiltakstype.id,
            "navn" to tiltakstype.navn,
            "tiltakskode" to tiltakstype.tiltakskode.name,
            "innsatsgrupper" to toPGObject(tiltakstype.innsatsgrupper),
            "innhold" to toPGObject(tiltakstype.innhold),
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
        log.info("Upsertet tiltakstype med id ${tiltakstype.id}")
    }

    fun get(tiltakskode: Tiltakskode): Result<Tiltakstype> = runCatching {
        val sql =
            """
            SELECT 
                id,
                navn,
                tiltakskode,
                innsatsgrupper,
                innhold
            FROM tiltakstype
            WHERE tiltakskode = :tiltakskode
            """.trimIndent()

        Database.query { session ->
            session.run(
                queryOf(
                    sql,
                    mapOf("tiltakskode" to tiltakskode.name),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke tiltakstype ${tiltakskode.name}")
        }
    }

    companion object {
        fun rowMapper(row: Row, alias: String? = null): Tiltakstype {
            val col = prefixColumn(alias)

            return Tiltakstype(
                id = row.uuid(col("id")),
                navn = row.string(col("navn")),
                tiltakskode = Tiltakskode.valueOf(row.string(col("tiltakskode"))),
                innsatsgrupper = row.string(col("innsatsgrupper")).let { objectMapper.readValue(it) },
                innhold = row.stringOrNull(col("innhold"))?.let { objectMapper.readValue<DeltakerRegistreringInnhold?>(it) },
            )
        }
    }
}
