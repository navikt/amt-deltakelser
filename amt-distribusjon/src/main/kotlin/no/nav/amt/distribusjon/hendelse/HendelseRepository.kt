package no.nav.amt.distribusjon.hendelse

import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.distribusjon.hendelse.model.Hendelse
import no.nav.amt.distribusjon.journalforing.model.HendelseMedJournalforingstatus
import no.nav.amt.distribusjon.journalforing.model.Journalforingstatus
import no.nav.amt.lib.utils.database.jdbi.Repository
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.toPGObject
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindList
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import tools.jackson.module.kotlin.readValue
import java.sql.ResultSet
import java.util.UUID

@RegisterRowMapper(HendelseMapper::class)
@RegisterRowMapper(HendelseMedJournalforingstatusMapper::class)
interface HendelseRepository : Repository {
    @SqlUpdate(
        """
        INSERT INTO hendelse (
            id,
            deltaker_id,
            deltaker,
            ansvarlig,
            payload,
            distribusjonskanal,
            manuelloppfolging
        )
        VALUES (
            :id,
            :deltaker_id,
            :deltaker,
            :ansvarlig,
            :payload,
            :distribusjonskanal,
            :manuelloppfolging
        )
        ON CONFLICT (id) DO NOTHING
        """,
    )
    fun insert(
        @Bind("id") id: UUID,
        @Bind("deltaker_id") deltakerId: UUID,
        @Bind("deltaker") deltaker: org.postgresql.util.PGobject,
        @Bind("ansvarlig") ansvarlig: org.postgresql.util.PGobject,
        @Bind("payload") payload: org.postgresql.util.PGobject,
        @Bind("distribusjonskanal") distribusjonskanal: String,
        @Bind("manuelloppfolging") manuellOppfolging: Boolean,
    )

    fun insert(hendelse: Hendelse) {
        insert(
            id = hendelse.id,
            deltakerId = hendelse.deltaker.id,
            deltaker = objectMapper.toPGObject(hendelse.deltaker),
            ansvarlig = objectMapper.toPGObject(hendelse.ansvarlig),
            payload = objectMapper.toPGObject(hendelse.payload),
            distribusjonskanal = hendelse.distribusjonskanal.name,
            manuellOppfolging = hendelse.manuellOppfolging,
        )
    }

    @SqlQuery(
        """
        SELECT
            h.id,
            h.deltaker,
            h.ansvarlig,
            h.payload,
            h.created_at,
            h.distribusjonskanal,
            h.manuelloppfolging,
            js.journalpost_id,
            js.bestillingsid,
            js.kan_ikke_distribueres,
            js.kan_ikke_journalfores
        FROM hendelse h
        JOIN journalforingstatus js ON h.id = js.hendelse_id
        WHERE
            js.journalpost_id IS NULL
            AND js.kan_ikke_journalfores IS NOT TRUE
        """,
    )
    fun hentIkkeJournalforteHendelser(): List<HendelseMedJournalforingstatus>

    /**
     * Hendelser som er journalført, men som ikke er distribuert (bestillingsid mangler).
     *
     * Vi ekskluderer "digitale" distribusjonskanaler (DITT_NAV/SDP), og ekskluderer samtidig
     * rader som allerede blir plukket opp av [hentIkkeJournalforteHendelser].
     */
    @SqlQuery(
        """
        SELECT
            h.id,
            h.deltaker,
            h.ansvarlig,
            h.payload,
            h.created_at,
            h.distribusjonskanal,
            h.manuelloppfolging,
            js.journalpost_id,
            js.bestillingsid,
            js.kan_ikke_distribueres,
            js.kan_ikke_journalfores
        FROM hendelse h
        JOIN journalforingstatus js ON h.id = js.hendelse_id
        WHERE
            js.bestillingsid IS NULL
            AND js.kan_ikke_distribueres IS NOT TRUE
            AND h.distribusjonskanal NOT IN ('DITT_NAV','SDP')
            AND NOT (
                js.journalpost_id IS NULL
                AND js.kan_ikke_journalfores IS NOT TRUE
            )
        """,
    )
    fun hentHendelserSomSkalDistribueresSomBrev(): List<HendelseMedJournalforingstatus>

    @SqlQuery(
        """
        SELECT *
        FROM hendelse
        WHERE id IN (<hendelseIder>)
        """,
    )
    fun findHendelser(
        @BindList("hendelseIder") hendelseIder: List<UUID>,
    ): List<Hendelse>

    fun getHendelser(hendelseIder: List<UUID>): List<Hendelse> = if (hendelseIder.isEmpty()) emptyList() else findHendelser(hendelseIder)
}

class HendelseMapper : RowMapper<Hendelse> {
    override fun map(
        rs: ResultSet,
        ctx: StatementContext,
    ): Hendelse = Hendelse(
        id = rs.getObject("id", UUID::class.java),
        deltaker = objectMapper.readValue(rs.getString("deltaker")),
        ansvarlig = objectMapper.readValue(rs.getString("ansvarlig")),
        payload = objectMapper.readValue(rs.getString("payload")),
        opprettet = rs.getTimestamp("created_at").toLocalDateTime(),
        distribusjonskanal = Distribusjonskanal.valueOf(rs.getString("distribusjonskanal")),
        manuellOppfolging = rs.getBoolean("manuelloppfolging"),
    )
}

class HendelseMedJournalforingstatusMapper : RowMapper<HendelseMedJournalforingstatus> {
    override fun map(
        rs: ResultSet,
        ctx: StatementContext,
    ): HendelseMedJournalforingstatus {
        val hendelse = Hendelse(
            id = rs.getObject("id", UUID::class.java),
            deltaker = objectMapper.readValue(rs.getString("deltaker")),
            ansvarlig = objectMapper.readValue(rs.getString("ansvarlig")),
            payload = objectMapper.readValue(rs.getString("payload")),
            opprettet = rs.getTimestamp("created_at").toLocalDateTime(),
            distribusjonskanal = Distribusjonskanal.valueOf(rs.getString("distribusjonskanal")),
            manuellOppfolging = rs.getBoolean("manuelloppfolging"),
        )

        return HendelseMedJournalforingstatus(
            hendelse = hendelse,
            journalforingstatus = Journalforingstatus(
                hendelseId = rs.getObject("id", UUID::class.java),
                journalpostId = rs.getString("journalpost_id")?.takeUnless { it.isBlank() },
                bestillingsId = rs.getObject("bestillingsid", UUID::class.java),
                kanIkkeDistribueres = rs.getBoolean("kan_ikke_distribueres"),
                kanIkkeJournalfores = rs.getBoolean("kan_ikke_journalfores"),
            ),
        )
    }
}
