package no.nav.amt.deltaker.bff.tiltakskoordinator

import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteService
import no.nav.amt.deltaker.bff.sporbarhet.SporbarhetsloggService
import no.nav.amt.lib.models.person.NavBruker
import java.util.UUID

class SporbarhetOgTilgangskontrollSvc(
    private val sporbarhetsloggService: SporbarhetsloggService,
    private val tilgangskontrollService: TilgangskontrollService,
    private val deltakerlisteService: DeltakerlisteService,
) {
    suspend fun kontrollerTilgangTilBruker(
        navIdent: String,
        navAnsattAzureId: UUID,
        navBruker: NavBruker,
        deltakerlisteId: UUID,
    ): Boolean {
        sporbarhetsloggService.sendAuditLog(
            navIdent = navIdent,
            deltakerPersonIdent = navBruker.personident,
        )

        deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteId)

        tilgangskontrollService.verifiserTiltakskoordinatorTilgang(
            navIdent = navIdent,
            deltakerlisteId = deltakerlisteId,
        )

        return tilgangskontrollService
            .harKoordinatorTilgangTilPerson(
                navAnsattAzureId,
                navBruker,
            )
    }
}
