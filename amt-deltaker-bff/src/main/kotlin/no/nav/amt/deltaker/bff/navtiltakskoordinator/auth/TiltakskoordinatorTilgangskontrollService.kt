package no.nav.amt.deltaker.bff.navtiltakskoordinator.auth

import no.nav.amt.deltaker.bff.auth.SporbarhetsloggService
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorService
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import org.slf4j.LoggerFactory
import java.util.UUID

class TiltakskoordinatorTilgangskontrollService(
    private val sporbarhetsloggService: SporbarhetsloggService,
    private val tilgangskontrollService: TilgangskontrollService,
    private val selfServiceTilgangService: SelfServiceTilgangService,
    private val deltakerlisteService: DeltakerlisteService,
    private val tiltakskoordinatorService: TiltakskoordinatorService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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

    suspend fun tilgangTilDeltakereGuard(
        deltakerIder: List<UUID>,
        deltakerlisteId: UUID,
        navIdent: String,
    ) {
        val deltakere = tiltakskoordinatorService
            .getMany(deltakerIder)
            .filter { it.deltakerliste.id == deltakerlisteId }
        val noenKanIkkeEndres = deltakere.any { !it.kanEndres }

        selfServiceTilgangService.verifiserTiltakskoordinatorTilgang(navIdent, deltakerlisteId)
        deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerlisteId)

        if (noenKanIkkeEndres) {
            throw AuthorizationException(
                "En eller flere deltakere kan ikke endres" +
                    "deltakere: ${deltakere.filter { !it.kanEndres }.map { it.id }}, " +
                    "deltakerliste: $deltakerlisteId",
            )
        }
        if (deltakerIder.size != deltakere.size) {
            log.error(
                "Alle deltakere i bulk operasjon må være på samme deltakerliste. " +
                    "deltakere: $deltakerIder, " +
                    "deltakerliste: $deltakerlisteId",
            )
            throw AuthorizationException("Alle deltakere i bulk operasjon må være på samme deltakerliste")
        }
    }
}
