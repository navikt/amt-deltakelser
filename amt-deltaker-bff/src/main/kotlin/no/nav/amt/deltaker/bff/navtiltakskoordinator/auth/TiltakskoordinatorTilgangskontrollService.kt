package no.nav.amt.deltaker.bff.navtiltakskoordinator.auth

import no.nav.amt.deltaker.bff.auth.SporbarhetsloggService
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteService
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import java.util.UUID

class TiltakskoordinatorTilgangskontrollService(
    private val sporbarhetsloggService: SporbarhetsloggService,
    private val tilgangskontrollService: TilgangskontrollService,
    private val selfServiceTilgangService: SelfServiceTilgangService,
    private val deltakerlisteService: DeltakerlisteService,
) {
    /*
      Vurderer ansatt tilgang for innbygger basert på adressebeskyttelse og skjerming.
      OBS: Gir automatisk permit om innbygger ikke har spesifikke restriksjoner
     */
    fun harTilgangTilPersonMedRestriksjoner(
        navAnsattAzureId: UUID,
        erSkjermet: Boolean,
        adressebeskyttelse: Adressebeskyttelse?,
    ): Boolean {
        val tilgangTilAdressebeskyttelse = tilgangskontrollService.vurderAdressebeskyttelseTilgang(adressebeskyttelse, navAnsattAzureId)
        val tilgangTilSkjerming = tilgangskontrollService.vurderSkjermingTilgang(erSkjermet, navAnsattAzureId)

        return tilgangTilAdressebeskyttelse.isPermit && tilgangTilSkjerming.isPermit
    }

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

        selfServiceTilgangService.verifiserTiltakskoordinatorTilgang(
            navIdent = navIdent,
            deltakerlisteId = deltakerlisteId,
        )

        return harTilgangTilPersonMedRestriksjoner(
            navAnsattAzureId = navAnsattAzureId,
            erSkjermet = erSkjermet,
            adressebeskyttelse = adressebeskyttelse,
        )
    }

    suspend fun tilgangTilGjennomforingGuard(
        gjennomforingId: UUID,
        navIdent: String,
    ) {
        selfServiceTilgangService.verifiserTiltakskoordinatorTilgang(navIdent, gjennomforingId)
        deltakerlisteService.verifiserTilgjengeligDeltakerliste(gjennomforingId)
    }
}
