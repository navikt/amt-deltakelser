package no.nav.amt.deltaker.bff.navtiltakskoordinator.auth

import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorsDeltakerlistePayload
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorsDeltakerlisteProducer
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class SelfServiceTilgangService(
    private val navAnsattService: NavAnsattService,
    private val tiltakskoordinatorTilgangRepository: TiltakskoordinatorTilgangRepository,
    private val tiltakskoordinatorsDeltakerlisteProducer: TiltakskoordinatorsDeltakerlisteProducer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getUtdaterteTiltakskoordinatorTilganger(): List<TiltakskoordinatorDeltakerlisteTilgang> =
        tiltakskoordinatorTilgangRepository.hentUtdaterteTilganger()

    suspend fun leggTilTiltakskoordinatorTilgang(
        navIdent: String,
        deltakerlisteId: UUID,
    ): Result<TiltakskoordinatorDeltakerlisteTilgang> {
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

    suspend fun fjernTiltakskoordinatorTilgang(
        navIdent: String,
        deltakerlisteId: UUID,
    ): Result<TiltakskoordinatorDeltakerlisteTilgang> {
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

    fun stengTilgangerTilDeltakerliste(deltakerlisteId: UUID) {
        val tilganger = tiltakskoordinatorTilgangRepository.hentAktiveForDeltakerliste(deltakerlisteId)

        log.info("Stenger ${tilganger.size} aktive tiltakskoordinatortilganger til deltakerliste $deltakerlisteId")
        tilganger.forEach { stengTiltakskoordinatorTilgang(it) }
    }

    suspend fun verifiserTiltakskoordinatorTilgang(
        navIdent: String,
        deltakerlisteId: UUID,
    ) {
        val koordinator = navAnsattService.hentEllerOpprettNavAnsatt(navIdent)
        val aktivTilgang = tiltakskoordinatorTilgangRepository.hentAktivTilgang(koordinator.id, deltakerlisteId)

        if (aktivTilgang.isFailure) {
            throw AuthorizationException("Ansatt ${koordinator.id} har ikke tilgang til deltakerliste $deltakerlisteId")
        }
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

    fun stengTiltakskoordinatorTilgang(id: UUID): Result<TiltakskoordinatorDeltakerlisteTilgang> {
        val tilgang = tiltakskoordinatorTilgangRepository.get(id).getOrThrow()

        return stengTiltakskoordinatorTilgang(tilgang)
    }

    private fun upsertTilgang(
        navIdent: String,
        tilgang: TiltakskoordinatorDeltakerlisteTilgang,
    ): Result<TiltakskoordinatorDeltakerlisteTilgang> = tiltakskoordinatorTilgangRepository
        .upsert(tilgang)
        .onSuccess { tilgang ->
            tiltakskoordinatorsDeltakerlisteProducer.produce(
                TiltakskoordinatorsDeltakerlistePayload.fromModel(
                    model = tilgang,
                    navIdent = navIdent,
                ),
            )
        }
}
