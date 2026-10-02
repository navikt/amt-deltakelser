package no.nav.amt.distribusjon.utils

import no.nav.amt.distribusjon.hendelse.model.Hendelse
import no.nav.amt.lib.utils.database.jdbi.Repository
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.toPGObject
import org.jdbi.v3.sqlobject.CreateSqlObject
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.postgresql.util.PGobject
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.util.UUID

interface TestRepository : Repository {
    @CreateSqlObject
    fun testHendelseRepository(): TestHendelseRepository

    /**
     * @param deltakerOverride Brukes til å teste lesing av avvikende/legacy JSON-strukturer i `deltaker`-kolonnen.
     * Er ikke satt, brukes [hendelse]s vanlige deltaker-representasjon.
     */
    fun insertHendelse(
        hendelse: Hendelse,
        deltakerOverride: JsonNode? = null,
    ) = testHendelseRepository().insert(
        id = hendelse.id,
        deltakerId = hendelse.deltaker.id,
        deltaker = objectMapper.toPGObject(deltakerOverride ?: objectMapper.valueToTree(hendelse.deltaker)),
        ansvarlig = objectMapper.toPGObject(hendelse.ansvarlig),
        payload = objectMapper.toPGObject(hendelse.payload),
        distribusjonskanal = hendelse.distribusjonskanal.name,
        manuellOppfolging = hendelse.manuellOppfolging,
        createdAt = hendelse.opprettet,
    )
}

interface TestHendelseRepository : Repository {
    @SqlUpdate(
        """
        INSERT INTO hendelse (
            id, 
            deltaker_id, 
            deltaker, 
            ansvarlig, 
            payload, 
            distribusjonskanal, 
            manuelloppfolging, 
            created_at
        )
        VALUES (
            :id, 
            :deltaker_id, 
            :deltaker, 
            :ansvarlig, 
            :payload, 
            :distribusjonskanal, 
            :manuelloppfolging, 
            :created_at
        )
        ON CONFLICT (id) DO NOTHING
        """,
    )
    fun insert(
        @Bind("id") id: UUID,
        @Bind("deltaker_id") deltakerId: UUID,
        @Bind("deltaker") deltaker: PGobject,
        @Bind("ansvarlig") ansvarlig: PGobject,
        @Bind("payload") payload: PGobject,
        @Bind("distribusjonskanal") distribusjonskanal: String,
        @Bind("manuelloppfolging") manuellOppfolging: Boolean,
        @Bind("created_at") createdAt: LocalDateTime,
    )
}
