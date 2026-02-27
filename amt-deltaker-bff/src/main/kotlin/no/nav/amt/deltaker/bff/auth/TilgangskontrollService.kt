package no.nav.amt.deltaker.bff.auth

import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteService
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.tiltakskoordinator.TiltakskoordinatorService
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.utils.database.Database
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.EksternBrukerTilgangTilEksternBrukerPolicyInput
import no.nav.poao_tilgang.client.NavAnsattBehandleFortroligBrukerePolicyInput
import no.nav.poao_tilgang.client.NavAnsattBehandleSkjermedePersonerPolicyInput
import no.nav.poao_tilgang.client.NavAnsattBehandleStrengtFortroligBrukerePolicyInput
import no.nav.poao_tilgang.client.NavAnsattTilgangTilEksternBrukerPolicyInput
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.TilgangType
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class TilgangskontrollService(
    private val poaoTilgangCachedClient: PoaoTilgangCachedClient,
    private val navAnsattService: NavAnsattService,
    private val tiltakskoordinatorTilgangRepository: TiltakskoordinatorTilgangRepository,
    private val tiltakskoordinatorsDeltakerlisteProducer: TiltakskoordinatorsDeltakerlisteProducer,
    private val tiltakskoordinatorService: TiltakskoordinatorService,
    private val deltakerlisteService: DeltakerlisteService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun verifiserSkrivetilgang(navAnsattAzureId: UUID, norskIdent: String) {
        val tilgang = poaoTilgangCachedClient
            .evaluatePolicy(
                NavAnsattTilgangTilEksternBrukerPolicyInput(
                    navAnsattAzureId,
                    TilgangType.SKRIVE,
                    norskIdent,
                ),
            ).getOrDefault(Decision.Deny("Ansatt har ikke skrivetilgang til bruker", ""))

        if (tilgang.isDeny) {
            throw AuthorizationException("Ansatt har ikke skrivetilgang til bruker")
        }
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

        verifiserTiltakskoordinatorTilgang(navIdent, deltakerlisteId)
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

    fun verifiserLesetilgang(navAnsattAzureId: UUID, norskIdent: String) {
        val tilgang = poaoTilgangCachedClient
            .evaluatePolicy(
                NavAnsattTilgangTilEksternBrukerPolicyInput(
                    navAnsattAzureId,
                    TilgangType.LESE,
                    norskIdent,
                ),
            ).getOrDefault(Decision.Deny("Ansatt har ikke lesetilgang til bruker", ""))

        if (tilgang.isDeny) {
            throw AuthorizationException("Ansatt har ikke lesetilgang til bruker")
        }
    }

    fun verifiserInnbyggersTilgangTilDeltaker(rekvirentPersonident: String, ressursPersonident: String) {
        val tilgang = poaoTilgangCachedClient
            .evaluatePolicy(
                EksternBrukerTilgangTilEksternBrukerPolicyInput(rekvirentPersonident, ressursPersonident),
            ).getOrDefault(Decision.Deny("Innbygger har ikke tilgang til deltaker", ""))

        if (tilgang.isDeny) {
            throw AuthorizationException("Innbygger har ikke tilgang til deltaker")
        }
    }

    fun harKoordinatorTilgangTilDeltaker(navAnsattAzureId: UUID, deltaker: Deltaker): Boolean {
        val tilgangTilAdressebeskyttelse = vurderAdressebeskyttelseTilgang(deltaker.navBruker.adressebeskyttelse, navAnsattAzureId)
        val tilgangTilSkjerming = vurderSkjermingTilgang(deltaker.navBruker, navAnsattAzureId)

        return tilgangTilAdressebeskyttelse.isPermit && tilgangTilSkjerming.isPermit
    }

    fun harKoordinatorTilgangTilPerson(navAnsattAzureId: UUID, navBruker: NavBruker): Boolean {
        val tilgangTilAdressebeskyttelse = vurderAdressebeskyttelseTilgang(navBruker.adressebeskyttelse, navAnsattAzureId)
        val tilgangTilSkjerming = vurderSkjermingTilgang(navBruker, navAnsattAzureId)

        return tilgangTilAdressebeskyttelse.isPermit && tilgangTilSkjerming.isPermit
    }

    private fun vurderSkjermingTilgang(navBruker: NavBruker, navAnsattAzureId: UUID): Decision = if (navBruker.erSkjermet) {
        poaoTilgangCachedClient.evaluatePolicy(NavAnsattBehandleSkjermedePersonerPolicyInput(navAnsattAzureId)).getOrThrow()
    } else {
        Decision.Permit
    }

    private fun vurderAdressebeskyttelseTilgang(adressebeskyttelse: Adressebeskyttelse?, navAnsattAzureId: UUID): Decision =
        when (adressebeskyttelse) {
            Adressebeskyttelse.FORTROLIG ->
                poaoTilgangCachedClient.evaluatePolicy(NavAnsattBehandleFortroligBrukerePolicyInput(navAnsattAzureId)).getOrThrow()

            Adressebeskyttelse.STRENGT_FORTROLIG, Adressebeskyttelse.STRENGT_FORTROLIG_UTLAND ->
                poaoTilgangCachedClient.evaluatePolicy(NavAnsattBehandleStrengtFortroligBrukerePolicyInput(navAnsattAzureId)).getOrThrow()

            else -> Decision.Permit
        }

    suspend fun leggTilTiltakskoordinatorTilgang(navIdent: String, deltakerlisteId: UUID): Result<TiltakskoordinatorDeltakerlisteTilgang> {
        val koordinator = navAnsattService.hentEllerOpprettNavAnsatt(navIdent)
        val aktivTilgang = tiltakskoordinatorTilgangRepository.hentAktivTilgang(koordinator.id, deltakerlisteId)

        return if (aktivTilgang.isSuccess) {
            log.error(
                "Kan ikke legge til tilgang til deltakerliste $deltakerlisteId " +
                    "fordi nav-ansatt ${koordinator.id} har allerede tilgang fra før.",
            )
            Result.failure(IllegalArgumentException("Nav-ansatt ${koordinator.id} har allerede tilgang til $deltakerlisteId"))
        } else {
            Database.transaction {
                upsertTilgang(
                    navIdent = navIdent,
                    TiltakskoordinatorDeltakerlisteTilgang(
                        id = UUID.randomUUID(),
                        navAnsattId = koordinator.id,
                        deltakerlisteId = deltakerlisteId,
                        gyldigFra = LocalDateTime.now(),
                        gyldigTil = null,
                    ),
                )
            }
        }
    }

    suspend fun fjernTiltakskoordinatorTilgang(navIdent: String, deltakerlisteId: UUID): Result<TiltakskoordinatorDeltakerlisteTilgang> {
        val koordinatorAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(navIdent)

        val tilgang = tiltakskoordinatorTilgangRepository
            .hentAktivTilgang(koordinatorAnsatt.id, deltakerlisteId)
            .getOrElse {
                log.error("Ingen aktiv tilgang funnet for ${koordinatorAnsatt.id} / $deltakerlisteId", it)
                return Result.failure(
                    IllegalArgumentException("Nav-ansatt ${koordinatorAnsatt.id} har ikke tilgang til $deltakerlisteId"),
                )
            }

        return stengTiltakskoordinatorTilgang(tilgang)
    }

    private fun upsertTilgang(
        navIdent: String,
        tilgang: TiltakskoordinatorDeltakerlisteTilgang,
    ): Result<TiltakskoordinatorDeltakerlisteTilgang> = tiltakskoordinatorTilgangRepository
        .upsert(tilgang)
        .onSuccess { tilgang ->
            tiltakskoordinatorsDeltakerlisteProducer.produce(
                TiltakskoordinatorsDeltakerlisteDto.fromModel(
                    model = tilgang,
                    navIdent = navIdent,
                ),
            )
        }

    fun stengTiltakskoordinatorTilgang(id: UUID): Result<TiltakskoordinatorDeltakerlisteTilgang> {
        val tilgang = tiltakskoordinatorTilgangRepository.get(id).getOrThrow()

        return stengTiltakskoordinatorTilgang(tilgang)
    }

    private fun stengTiltakskoordinatorTilgang(
        tilgang: TiltakskoordinatorDeltakerlisteTilgang,
    ): Result<TiltakskoordinatorDeltakerlisteTilgang> = if (tilgang.gyldigTil == null) {
        tiltakskoordinatorTilgangRepository
            .upsert(tilgang.copy(gyldigTil = LocalDateTime.now()))
            .onSuccess { tilgang ->
                log.info("Stengte tiltakskoordinators tilgang ${tilgang.id}")
                tiltakskoordinatorsDeltakerlisteProducer.produceTombstone(tilgang.id)
            }
    } else {
        log.warn("Kan ikke stenge tiltakskoordinatortilgang som allerede er stengt ${tilgang.id}")
        Result.failure(
            IllegalArgumentException("Kan ikke stenge tiltakskoordinatortilgang som allerede er stengt ${tilgang.id}"),
        )
    }

    suspend fun verifiserTiltakskoordinatorTilgang(navIdent: String, deltakerlisteId: UUID) {
        val koordinator = navAnsattService.hentEllerOpprettNavAnsatt(navIdent)
        val aktivTilgang = tiltakskoordinatorTilgangRepository.hentAktivTilgang(koordinator.id, deltakerlisteId)

        if (aktivTilgang.isFailure) {
            throw AuthorizationException("Ansatt ${koordinator.id} har ikke tilgang til deltakerliste $deltakerlisteId")
        }
    }

    fun getUtdaterteTiltakskoordinatorTilganger(): List<TiltakskoordinatorDeltakerlisteTilgang> =
        tiltakskoordinatorTilgangRepository.hentUtdaterteTilganger()

    fun stengTilgangerTilDeltakerliste(deltakerlisteId: UUID) {
        val tilganger = tiltakskoordinatorTilgangRepository.hentAktiveForDeltakerliste(deltakerlisteId)

        log.info("Stenger ${tilganger.size} aktive tiltakskoordinatortilganger til deltakerliste $deltakerlisteId")
        tilganger.forEach { stengTiltakskoordinatorTilgang(it) }
    }
}
