package no.nav.amt.deltaker.bff.deltaker

import no.nav.amt.deltaker.bff.apiclients.DtoMappers.toDeltakeroppdatering
import no.nav.amt.deltaker.bff.apiclients.deltaker.AmtDeltakerClient
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerStatusRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.deltaker.model.AKTIVE_STATUSER
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltaker.model.Deltakeroppdatering
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndringForslagRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndringRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.util.UUID

class DeltakerService(
    private val deltakerRepository: DeltakerRepository,
    private val amtDeltakerClient: AmtDeltakerClient,
    private val navEnhetService: NavEnhetService,
    private val forslagRepository: ForslagRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun oppdaterDeltaker(
        deltaker: Deltaker,
        endringRequest: EndringRequest,
    ): Deltaker {
        navEnhetService.hentOpprettEllerOppdaterNavEnhet(endringRequest.endretAvEnhet)

        val deltakeroppdatering = amtDeltakerClient
            .postEndreDeltaker(
                deltakerId = deltaker.id,
                requestBody = endringRequest,
            ).toDeltakeroppdatering()

        oppdaterDeltaker(
            deltakeroppdatering = deltakeroppdatering,
            beforeUpsert = {
                if (endringRequest is ReaktiverDeltakelseRequest) {
                    slettKladdIfExists(
                        deltakerlisteId = deltaker.deltakerliste.id,
                        personident = deltaker.navBruker.personident,
                    )
                }
            },
            afterUpsert = {
                if (endringRequest is EndringForslagRequest) {
                    endringRequest.forslagId?.let { forslagId -> forslagRepository.delete(forslagId) }
                }
            },
        )

        return deltaker.oppdater(deltakeroppdatering)
    }

    /**
     * Benyttes av [oppdaterDeltaker] og kaller ikke amt-deltaker
     */
    private fun slettKladdIfExists(
        deltakerlisteId: UUID,
        personident: String,
    ) {
        deltakerRepository.getKladdForDeltakerliste(deltakerlisteId, personident).onSuccess { deltaker ->
            deleteDeltaker(deltaker.id)
        }
    }

    // benyttes av DeltakerV2Consumer
    fun oppdaterDeltakerLaas(
        deltakerId: UUID,
        personident: String,
        deltakerlisteId: UUID,
    ) {
        fun laasOppDeltakelse(deltakerId: UUID) {
            log.info("Nyeste deltakelse $deltakerId var låst for endringer. Låser opp")
            deltakerRepository.settKanEndres(deltakerId, true)
        }

        /*
            Denne funksjonen er ment til alle scenarioer hvor det er relevant med låsing av deltakere.
            Skal kalles etter at databasen er oppdatert med ny/oppdatering av deltaker som gjør at den er vanskelig å gjenbruke noen steder
            (flere steder så oppdateres ikke databasen med deltakerendringer før resultatet er mottatt på kafka).
            OBS: Flere deltakelser kan ha samme påmeldt dato(i tilfelle den ene er historisert).
            Scenario 1: oppdatering av eksisterende deltakelser
            Scenario 2: Import av data fra arena
            Scenario 3: Avbryt utkast som i praksis vil ha en deltakelse uten påmeldtdato
         */
        val deltakelserPaaPerson = deltakerRepository
            .getMany(personident, deltakerlisteId)

        if (deltakelserPaaPerson.none { it.id == deltakerId }) {
            throw IllegalStateException("Den nye deltakelsen $deltakerId må være upsertet for å bruke denne funksjonen")
        }

        // early-return hvis kun en deltakelse
        deltakelserPaaPerson.singleOrNull()?.let { deltakelse ->
            if (!deltakelse.kanEndres) {
                laasOppDeltakelse(deltakelse.id)
            }
            return
        }

        val deltakelserPaaPersonSorted = deltakelserPaaPerson
            .sortedWith(
                compareByDescending<Deltaker> { it.paameldtDato }
                    .thenByDescending { it.status.gyldigFra },
            )

        val nyesteDeltakelse = deltakelserPaaPersonSorted.firstOrNull { it.status.type in AKTIVE_STATUSER }
            ?: deltakelserPaaPersonSorted.first()

        if (deltakerId != nyesteDeltakelse.id) {
            log.info("Fikk oppdatering på $deltakerId som skal låses fordi det er nyere deltakelse ${nyesteDeltakelse.id} på personen")
        }

        val deltakelserSomSkalLaases = deltakelserPaaPersonSorted
            .filter {
                it.id != nyesteDeltakelse.id ||
                    nyesteDeltakelse.status.type == DeltakerStatus.Type.FEILREGISTRERT ||
                    nyesteDeltakelse.status.aarsak?.type == DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
            }.filter { it.kanEndres }

        val laasesMedAktivStatus = deltakelserSomSkalLaases
            .filter { it.status.type in AKTIVE_STATUSER }

        if (laasesMedAktivStatus.isNotEmpty()) {
            throw IllegalStateException(
                "ugyldig state. Fant eldre deltakelser med aktiv status: " +
                    "Nyeste deltaker ${nyesteDeltakelse.id} " +
                    "påmeldt ${nyesteDeltakelse.paameldtDato} " +
                    "har status ${nyesteDeltakelse.status.type}. " +
                    "Eldre deltakelse(r) ${laasesMedAktivStatus.map { it.id }} " +
                    "påmeldt ${laasesMedAktivStatus.map { it.paameldtDato }} " +
                    "har status ${laasesMedAktivStatus.map { it.status.type }}. ",
            )
        }

        if (!nyesteDeltakelse.kanEndres) {
            // Dette skal ikke skje i en ventet funksjonell flyt men mange feil med
            // låsing opp igjennom tidene har ført til at nyeste deltakelse er låst
            laasOppDeltakelse(nyesteDeltakelse.id)
        }

        if (deltakelserSomSkalLaases.any()) {
            laasSingleOrMultipleDeltakelser(
                iderSomSkalLaases = deltakelserSomSkalLaases.map { it.id },
                nyDeltakerId = nyesteDeltakelse.id,
            )
        }
    }

    private fun laasTidligereDeltakelser(deltakeroppdatering: Deltakeroppdatering) {
        if (deltakeroppdatering.status.type in AKTIVE_STATUSER && harEndretStatus(deltakeroppdatering)) {
            val tidligereDeltakelser = deltakerRepository.getTidligereAvsluttedeDeltakelser(deltakeroppdatering.id)

            if (tidligereDeltakelser.any()) {
                laasSingleOrMultipleDeltakelser(
                    iderSomSkalLaases = tidligereDeltakelser,
                    nyDeltakerId = deltakeroppdatering.id,
                )
            }
        }
    }

    private fun laasSingleOrMultipleDeltakelser(
        iderSomSkalLaases: List<UUID>,
        nyDeltakerId: UUID,
    ) {
        if (iderSomSkalLaases.isEmpty()) return

        log.info(
            "Låser ${iderSomSkalLaases.size} deltakere for endringer grunnet nyere aktiv deltaker med id $nyDeltakerId",
        )

        if (iderSomSkalLaases.size > 1) {
            deltakerRepository.disableKanEndresMany(iderSomSkalLaases)
        } else {
            deltakerRepository.settKanEndres(iderSomSkalLaases.first(), false)
        }
    }

    fun lagreDeltakerStatus(
        deltakerId: UUID,
        deltakerStatus: DeltakerStatus,
    ) {
        DeltakerStatusRepository.slettTidligereStatuser(deltakerId, deltakerStatus)
        DeltakerStatusRepository.insertIfNotExists(deltakerId, deltakerStatus)
    }

    suspend fun oppdaterDeltaker(
        deltakeroppdatering: Deltakeroppdatering,
        beforeUpsert: () -> Unit = {},
        afterUpsert: () -> Unit = {},
    ) {
        val disableKanEndres = deltakeroppdatering.status.type == DeltakerStatus.Type.FEILREGISTRERT ||
            deltakeroppdatering.status.aarsak?.type == DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT

        Database.transaction {
            beforeUpsert()

            laasTidligereDeltakelser(deltakeroppdatering)

            deltakerRepository.update(deltakeroppdatering)
            lagreDeltakerStatus(deltakeroppdatering.id, deltakeroppdatering.status)

            // deltakerRepository.settKanEndres kalles også i laasTidligereDeltakelser, undersøk
            if (disableKanEndres) {
                deltakerRepository.settKanEndres(deltakeroppdatering.id, false)
            }

            afterUpsert()
        }
    }

    fun deleteDeltaker(deltakerId: UUID) {
        forslagRepository.deleteForDeltaker(deltakerId)
        DeltakerStatusRepository.slettStatus(deltakerId)
        deltakerRepository.slettDeltaker(deltakerId)
    }

    // benyttes av Routing.registerInnbyggerApi
    suspend fun oppdaterSistBesokt(deltaker: Deltaker) {
        val sistBesokt = ZonedDateTime.now()
        amtDeltakerClient.sistBesokt(deltaker.id, sistBesokt)
        deltakerRepository.oppdaterSistBesokt(deltaker.id, sistBesokt)
    }

    // benyttes av TiltakskoordinatorService
    suspend fun oppdaterDeltakere(oppdaterteDeltakere: List<Deltakeroppdatering>) {
        Database.transaction {
            deltakerRepository.updateBatch(oppdaterteDeltakere)
            DeltakerStatusRepository.batchSlettTidligereStatuser(oppdaterteDeltakere)
            DeltakerStatusRepository.batchInsert(oppdaterteDeltakere)
        }
    }

    private fun harEndretStatus(deltakeroppdatering: Deltakeroppdatering): Boolean {
        val currentStatus = DeltakerStatusRepository.getAktivDeltakerStatus(deltakeroppdatering.id) ?: return true

        return currentStatus.type != deltakeroppdatering.status.type
    }
}
