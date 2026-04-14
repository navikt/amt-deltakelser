package no.nav.amt.deltaker.bff.navtiltakskoordinator

import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteService
import no.nav.amt.deltaker.bff.sporbarhet.SporbarhetsloggService
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import java.util.UUID

class SporbarhetOgTilgangskontrollSvc(
    private val sporbarhetsloggService: SporbarhetsloggService,
    private val tilgangskontrollService: TilgangskontrollService,
    private val deltakerlisteService: DeltakerlisteService,
) {
    suspend fun kontrollerTilgangTilBruker(
        navIdent: String,
        navAnsattAzureId: UUID,
        personident: String,
        erInnbyggerSkjermet: Boolean,
        adressebeskyttelse: Adressebeskyttelse?,
        deltakerlisteId: UUID,
    ): Boolean = kontrollerTilgangTilBruker(
        navIdent = navIdent,
        navAnsattAzureId = navAnsattAzureId,
        personident = navBruker.personident,
        erSkjermet = navBruker.erSkjermet,
        adressebeskyttelse = navBruker.adressebeskyttelse,
        deltakerlisteId = deltakerlisteId,
    )

    suspend fun kontrollerTilgangTilBruker(
        navIdent: String,
        navAnsattAzureId: UUID,
        personident: String,
        erSkjermet: Boolean,
        adressebeskyttelse: Adressebeskyttelse?,
        deltakerlisteId: UUID,
    ): Boolean {
        sporbarhetsloggService.sendAuditLog(
            navIdent = navIdent,
            deltakerPersonIdent = personident,
        )

        deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteId)

        tilgangskontrollService.verifiserTiltakskoordinatorTilgang(
            navIdent = navIdent,
            deltakerlisteId = deltakerlisteId,
        )

        return tilgangskontrollService
            .harKoordinatorTilgangTilPerson(
                navAnsattAzureId = navAnsattAzureId,
                erSkjermet = erSkjermet,
                adressebeskyttelse = adressebeskyttelse,
            )
    }
}
