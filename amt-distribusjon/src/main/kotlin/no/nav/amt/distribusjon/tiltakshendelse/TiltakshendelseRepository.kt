package no.nav.amt.distribusjon.tiltakshendelse

import no.nav.amt.distribusjon.tiltakshendelse.model.Tiltakshendelse
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.database.jdbi.Repository
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import java.sql.ResultSet
import java.util.UUID

@RegisterRowMapper(TiltakshendelseMapper::class)
interface TiltakshendelseRepository : Repository {
    @SqlQuery(
        """
        SELECT *
        FROM tiltakshendelse
        WHERE id = :id
        """,
    )
    fun getById(
        @Bind("id") id: UUID,
    ): Tiltakshendelse?

    @SqlQuery(
        """
        SELECT *
        FROM tiltakshendelse
        WHERE deltaker_id = :deltaker_id
          AND type = :type
          AND aktiv = true
        ORDER BY modified_at DESC
        LIMIT 1
        """,
    )
    fun getAktivHendelse(
        @Bind("deltaker_id") deltakerId: UUID,
        @Bind("type") type: String,
    ): Tiltakshendelse?

    @SqlQuery(
        """
        SELECT *
        FROM tiltakshendelse
        WHERE forslag_id = :forslag_id
        """,
    )
    fun getForslagHendelseSql(
        @Bind("forslag_id") forslagId: UUID,
    ): Tiltakshendelse?

    @SqlQuery(
        """
        SELECT *
        FROM tiltakshendelse
        WHERE :hendelse_id = ANY(hendelser)
        """,
    )
    fun getByHendelseIdSql(
        @Bind("hendelse_id") hendelseId: UUID,
    ): Tiltakshendelse?

    fun get(id: UUID): Result<Tiltakshendelse> = runCatching {
        getById(id) ?: throw NoSuchElementException("Fant ikke tiltakshendelse $id")
    }

    fun getAktivHendelse(
        deltakerId: UUID,
        hendelseType: Tiltakshendelse.Type,
    ): Result<Tiltakshendelse> = runCatching {
        getAktivHendelse(deltakerId, hendelseType.name)
            ?: throw NoSuchElementException(
                "Fant ikke aktiv tiltakshendelse for deltaker $deltakerId og type $hendelseType",
            )
    }

    fun getForslagHendelse(forslagId: UUID): Result<Tiltakshendelse> = runCatching {
        getForslagHendelseSql(forslagId)
            ?: throw NoSuchElementException("Fant ikke tiltakshendelse for med forslagId $forslagId")
    }

    fun getByHendelseId(hendelseId: UUID): Result<Tiltakshendelse> = runCatching {
        getByHendelseIdSql(hendelseId)
            ?: throw NoSuchElementException("Fant ikke tiltakshendelse for hendelse $hendelseId")
    }

    fun upsert(tiltakshendelse: Tiltakshendelse): Tiltakshendelse {
        val sql = if (tiltakshendelse.forslagId == null) {
            UPSERT_BY_ID_SQL
        } else {
            UPSERT_BY_FORSLAG_ID_SQL
        }

        return handle
            .createQuery(sql)
            .bind("id", tiltakshendelse.id)
            .bind("type", tiltakshendelse.type.name)
            .bind("deltaker_id", tiltakshendelse.deltakerId)
            .bind("forslag_id", tiltakshendelse.forslagId)
            .bind("hendelser", tiltakshendelse.hendelser.toTypedArray())
            .bind("personident", tiltakshendelse.personident)
            .bind("aktiv", tiltakshendelse.aktiv)
            .bind("tekst", tiltakshendelse.tekst)
            .bind("tiltakskode", tiltakshendelse.tiltakskode.name)
            .map(TiltakshendelseMapper())
            .singleOrNull() ?: error("Klarte ikke å upserte tiltakshendelse ${tiltakshendelse.id}")
    }

    companion object {
        private const val ID_COLUMN = "id"
        private const val FORSLAG_ID_COLUMN = "forslag_id"

        private val UPSERT_BY_ID_SQL = createUpsertSql(ID_COLUMN)
        private val UPSERT_BY_FORSLAG_ID_SQL = createUpsertSql(FORSLAG_ID_COLUMN)

        private fun createUpsertSql(conflictColumn: String) =
            """
            INSERT INTO tiltakshendelse (
                id,
                type,
                deltaker_id,
                forslag_id,
                hendelser,
                personident,
                aktiv,
                tekst,
                tiltakskode
            )
            VALUES (
                :id,
                :type,
                :deltaker_id,
                :forslag_id,
                :hendelser,
                :personident,
                :aktiv,
                :tekst,
                :tiltakskode
            )
            ON CONFLICT ($conflictColumn) DO UPDATE SET
                hendelser = EXCLUDED.hendelser,
                personident = EXCLUDED.personident,
                aktiv = EXCLUDED.aktiv,
                tekst = EXCLUDED.tekst,
                modified_at = CURRENT_TIMESTAMP
            RETURNING *
            """.trimIndent()
    }
}

class TiltakshendelseMapper : RowMapper<Tiltakshendelse> {
    override fun map(
        rs: ResultSet,
        ctx: StatementContext,
    ): Tiltakshendelse = Tiltakshendelse(
        id = rs.getObject("id", UUID::class.java),
        type = Tiltakshendelse.Type.valueOf(rs.getString("type")),
        deltakerId = rs.getObject("deltaker_id", UUID::class.java),
        forslagId = rs.getObject("forslag_id", UUID::class.java),
        hendelser = (rs.getArray("hendelser").array as Array<*>).map { it as UUID },
        personident = rs.getString("personident"),
        aktiv = rs.getBoolean("aktiv"),
        tekst = rs.getString("tekst"),
        tiltakskode = Tiltakskode.valueOf(rs.getString("tiltakskode")),
        opprettet = rs.getTimestamp("created_at").toLocalDateTime(),
    )
}
