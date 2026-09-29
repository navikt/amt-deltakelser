package no.nav.amt.distribusjon.journalforing

import no.nav.amt.distribusjon.journalforing.model.Journalforingstatus
import org.jdbi.v3.sqlobject.SqlObject
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.util.UUID

interface JdbiJournalforingstatusRepository : SqlObject {
    @SqlUpdate(
        """
        INSERT INTO journalforingstatus (
            hendelse_id,
            journalpost_id,
            bestillingsid,
            kan_ikke_distribueres,
            kan_ikke_journalfores
        )
        VALUES (
            :hendelse_id,
            :journalpost_id,
            CAST(:bestillingsid AS uuid),
            :kan_ikke_distribueres,
            :kan_ikke_journalfores
        )
        ON CONFLICT (hendelse_id) DO UPDATE SET
            journalpost_id = :journalpost_id,
            bestillingsid = CAST(:bestillingsid AS uuid),
            kan_ikke_distribueres = :kan_ikke_distribueres,
            kan_ikke_journalfores = :kan_ikke_journalfores,
            modified_at = CURRENT_TIMESTAMP
        """
    )
    fun upsert(
        @Bind("hendelse_id") hendelseId: UUID,
        @Bind("journalpost_id") journalpostId: String?,
        @Bind("bestillingsid") bestillingsId: String?,
        @Bind("kan_ikke_distribueres") kanIkkeDistribueres: Boolean,
        @Bind("kan_ikke_journalfores") kanIkkeJournalfores: Boolean,
    )

    fun upsert(journalforingstatus: Journalforingstatus) {
        upsert(
            hendelseId = journalforingstatus.hendelseId,
            journalpostId = journalforingstatus.journalpostId,
            bestillingsId = journalforingstatus.bestillingsId?.toString(),
            kanIkkeDistribueres = journalforingstatus.kanIkkeDistribueres ?: false,
            kanIkkeJournalfores = journalforingstatus.kanIkkeJournalfores ?: false,
        )
    }

    @SqlQuery(
        """
        SELECT *
        FROM journalforingstatus
        WHERE hendelse_id = :hendelse_id
        """
    )
    fun get(@Bind("hendelse_id") hendelseId: UUID): Journalforingstatus?
}
