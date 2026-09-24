package no.nav.amt.deltaker.bff.utils

import kotliquery.queryOf
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteRepository
import no.nav.amt.deltaker.bff.model.Deltakerliste
import no.nav.amt.deltaker.bff.tiltak.TiltakRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.ArrangorRepository
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.database.Database
import java.time.LocalDateTime

object TestRepository {
    fun insert(
        deltakerliste: Deltakerliste,
        overordnetArrangor: Arrangor? = null,
    ) {
        TiltakRepository().upsert(deltakerliste.tiltak)
        overordnetArrangor?.let { ArrangorRepository().upsert(it) }
        ArrangorRepository().upsert(deltakerliste.arrangor.arrangor)
        DeltakerlisteRepository().upsert(deltakerliste)
    }

    fun insert(
        navEnhet: NavEnhet,
        sistEndret: LocalDateTime,
    ) {
        Database.query { session ->
            session.update(
                queryOf(
                    "UPDATE nav_enhet SET modified_at = :modified_at WHERE id = :id",
                    mapOf(
                        "id" to navEnhet.id,
                        "modified_at" to sistEndret,
                    ),
                ),
            )
        }
    }
}
