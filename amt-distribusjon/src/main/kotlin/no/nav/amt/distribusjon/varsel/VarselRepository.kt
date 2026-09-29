package no.nav.amt.distribusjon.varsel

import no.nav.amt.distribusjon.varsel.model.Varsel
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.SqlObject
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.sql.ResultSet
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@RegisterRowMapper(VarselMapper::class)
interface VarselRepository : SqlObject {
    @SqlUpdate(
        """
        INSERT INTO varsel (
            id,
            type,
            hendelser,
            status,
            tekst,
            aktiv_fra,
            aktiv_til,
            deltaker_id,
            personident,
            er_eksternt_varsel,
            revarsel_for_varsel,
            revarsles
        )
        VALUES (
            :id,
            :type,
            :hendelser,
            :status,
            :tekst,
            :aktiv_fra,
            :aktiv_til,
            :deltaker_id,
            :personident,
            :er_eksternt_varsel,
            :revarsel_for_varsel,
            :revarsles
        )
        ON CONFLICT (id) DO UPDATE SET
            type = EXCLUDED.type,
            hendelser = EXCLUDED.hendelser,
            status = EXCLUDED.status,
            tekst = EXCLUDED.tekst,
            aktiv_fra = EXCLUDED.aktiv_fra,
            aktiv_til = EXCLUDED.aktiv_til,
            er_eksternt_varsel = EXCLUDED.er_eksternt_varsel,
            revarsel_for_varsel = EXCLUDED.revarsel_for_varsel,
            revarsles = EXCLUDED.revarsles,
            modified_at = CURRENT_TIMESTAMP
        """,
    )
    fun upsert(
        @Bind("id") id: UUID,
        @Bind("type") type: String,
        @Bind("hendelser") hendelser: Array<UUID>,
        @Bind("status") status: String,
        @Bind("tekst") tekst: String,
        @Bind("aktiv_fra") aktivFra: ZonedDateTime,
        @Bind("aktiv_til") aktivTil: ZonedDateTime?,
        @Bind("deltaker_id") deltakerId: UUID,
        @Bind("personident") personident: String,
        @Bind("er_eksternt_varsel") erEksterntVarsel: Boolean,
        @Bind("revarsel_for_varsel") revarselForVarsel: UUID?,
        @Bind("revarsles") revarsles: ZonedDateTime?,
    )

    fun upsert(varsel: Varsel) = upsert(
        id = varsel.id,
        type = varsel.type.name,
        hendelser = varsel.hendelser.toTypedArray(),
        status = varsel.status.name,
        tekst = varsel.tekst,
        aktivFra = varsel.aktivFra,
        aktivTil = varsel.aktivTil,
        deltakerId = varsel.deltakerId,
        personident = varsel.personident,
        erEksterntVarsel = varsel.erEksterntVarsel,
        revarselForVarsel = varsel.revarselForVarsel,
        revarsles = varsel.revarsles,
    )

    @SqlQuery(
        """
        SELECT *
        FROM varsel
        WHERE deltaker_id = :deltaker_id
          AND type = :type
        ORDER BY aktiv_fra DESC
        LIMIT 1
        """,
    )
    fun getSisteVarselSql(
        @Bind("deltaker_id") deltakerId: UUID,
        @Bind("type") type: String,
    ): Varsel?

    fun getSisteVarsel(
        deltakerId: UUID,
        type: Varsel.Type,
    ): Result<Varsel> = runCatching {
        getSisteVarselSql(deltakerId, type.name)
            ?: throw NoSuchElementException("Fant ingen varsel av type $type for deltaker $deltakerId")
    }

    @SqlQuery(
        """
        SELECT *
        FROM varsel
        WHERE deltaker_id = :deltaker_id
          AND type = 'BESKJED'
          AND (
              status = 'VENTER_PA_UTSENDELSE'
              OR status = 'AKTIV'
          )
        """,
    )
    fun getAktiveEllerVentendeBeskjeder(
        @Bind("deltaker_id") deltakerId: UUID,
    ): List<Varsel>

    @SqlQuery(
        "SELECT * FROM varsel WHERE deltaker_id = :deltaker_id AND status = 'AKTIV'",
    )
    fun getAktivtSql(
        @Bind("deltaker_id") deltakerId: UUID,
    ): Varsel?

    fun getAktivt(deltakerId: UUID): Result<Varsel> = runCatching {
        getAktivtSql(deltakerId)
            ?: throw NoSuchElementException("Fant ikke varsel for deltakerId: $deltakerId")
    }

    @SqlQuery("SELECT * FROM varsel WHERE id = :id")
    fun getSql(
        @Bind("id") id: UUID,
    ): Varsel?

    fun get(id: UUID): Result<Varsel> = runCatching {
        getSql(id) ?: throw NoSuchElementException("Fant ikke varsel $id")
    }

    @SqlQuery(
        "SELECT * FROM varsel WHERE hendelser @> ARRAY[:hendelse_id]::uuid[]",
    )
    fun getByHendelseIdSql(
        @Bind("hendelse_id") hendelseId: UUID,
    ): Varsel?

    fun getByHendelseId(hendelseId: UUID): Result<Varsel> = runCatching {
        getByHendelseIdSql(hendelseId)
            ?: throw NoSuchElementException("Fant ikke varsel for hendelse $hendelseId")
    }

    @SqlQuery(
        "SELECT * FROM varsel WHERE deltaker_id = :deltaker_id AND status = 'VENTER_PA_UTSENDELSE'",
    )
    fun getVentendeVarselSql(
        @Bind("deltaker_id") deltakerId: UUID,
    ): Varsel?

    fun getVentendeVarsel(deltakerId: UUID): Result<Varsel> = runCatching {
        getVentendeVarselSql(deltakerId)
            ?: throw NoSuchElementException("Fant ikke ventende varsel for deltaker $deltakerId")
    }

    @SqlQuery(
        "SELECT * FROM varsel WHERE status = 'VENTER_PA_UTSENDELSE' AND aktiv_fra < CURRENT_TIMESTAMP",
    )
    fun getVarslerSomSkalSendes(): List<Varsel>

    @SqlQuery("SELECT * FROM varsel WHERE revarsles < CURRENT_TIMESTAMP")
    fun getVarslerSomSkalRevarsles(): List<Varsel>

    @SqlUpdate(
        "UPDATE varsel SET revarsles = null WHERE deltaker_id = :deltaker_id AND revarsles IS NOT NULL",
    )
    fun stoppRevarsler(
        @Bind("deltaker_id") deltakerId: UUID,
    )
}

class VarselMapper : RowMapper<Varsel> {
    override fun map(
        rs: ResultSet,
        ctx: StatementContext,
    ): Varsel = Varsel(
        id = rs.getObject("id", UUID::class.java),
        type = Varsel.Type.valueOf(rs.getString("type")),
        hendelser = (rs.getArray("hendelser").array as Array<*>).map { it as UUID },
        status = Varsel.Status.valueOf(rs.getString("status")),
        aktivFra = rs.getTimestamp("aktiv_fra").toInstant().atZone(ZoneId.of("Z")),
        aktivTil = rs.getTimestamp("aktiv_til")?.toInstant()?.atZone(ZoneId.of("Z")),
        deltakerId = rs.getObject("deltaker_id", UUID::class.java),
        personident = rs.getString("personident"),
        tekst = rs.getString("tekst"),
        erEksterntVarsel = rs.getBoolean("er_eksternt_varsel"),
        revarselForVarsel = rs.getObject("revarsel_for_varsel", UUID::class.java),
        revarsles = rs.getTimestamp("revarsles")?.toInstant()?.atZone(ZoneId.of("Z")),
    )
}
