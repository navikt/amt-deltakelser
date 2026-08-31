package no.nav.amt.deltaker.service

import no.nav.amt.deltaker.enkeltplass.GjennomforingUpserter
import no.nav.amt.deltaker.extensions.getForslagId
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.OpplaringKategoriseringRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.service.DeltakerService.Companion.validerIkkeFeilregistrert
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringService
import no.nav.amt.deltaker.veileder.endring.VellykketEndring
import no.nav.amt.deltaker.veileder.endring.extensions.anvendPaaDeltaker
import no.nav.amt.deltaker.veileder.endring.extensions.validerGyldigFra
import no.nav.amt.internapi.deltaker.request.EndretOpplaringKategoriseringRequest
import no.nav.amt.internapi.deltaker.request.EndretPrisinfoRequest
import no.nav.amt.internapi.deltaker.request.EndringRequest
import no.nav.amt.internapi.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.lib.ktor.clients.kodeverk.OpplaringKategoriseringClient
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.slf4j.LoggerFactory
import java.util.UUID

class VeilederEndringService(
    private val deltakerService: DeltakerService,
    private val deltakerRepository: DeltakerRepository,
    private val deltakerEndringService: DeltakerEndringService,
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val navAnsattService: NavAnsattService,
    private val unleashToggle: CommonUnleashToggle,
    private val gjennomforingUpserter: GjennomforingUpserter,
    private val opplaringKategoriseringClient: OpplaringKategoriseringClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Behandler en endrings-request fra veileder og persisterer resultatet.
     *
     * Flyten er:
     * 1. Validerer at deltakeren kan endres ([validerIkkeFeilregistrert], unleash-toggle, aktiv oppfølging)
     * 2. Beregner ny deltakerstate via [beregnUpdateResult] — returnerer tidlig hvis deltakeren er uendret
     * 3. Henter Nav-ansatt og eventuell opplæringskategorisering utenfor db-transaksjonen (suspend-kall)
     * 4. Persisterer endringen og produserer Kafka-melding via [DeltakerService.upsertAndProduceDeltaker]
     *
     * @param deltakerId id på deltakeren som skal endres
     * @param endringRequest requesten som beskriver ønsket endring
     * @return oppdatert [Deltaker], eller uendret deltaker hvis endringen ikke førte til noen diff
     */
    suspend fun upsertEndretDeltaker(
        deltakerId: UUID,
        endringRequest: EndringRequest,
    ): Deltaker {
        val eksisterendeDeltaker = deltakerRepository.get(deltakerId).getOrThrow()
        validerIkkeFeilregistrert(eksisterendeDeltaker)

        require(unleashToggle.erKometMasterForTiltakstype(eksisterendeDeltaker.deltakerliste.tiltakstype.tiltakskode)) {
            "Kan ikke utføre endring på deltaker $deltakerId på tiltakstype ${eksisterendeDeltaker.deltakerliste.tiltakstype.tiltakskode} som komet ikke eier"
        }

        require(eksisterendeDeltaker.navBruker.harAktivOppfolgingsperiode || endringRequest.kanIverksettesUtenAktivOppfolging()) {
            "Kan ikke utføre endring ${endringRequest.javaClass.simpleName} på deltaker $deltakerId uten aktiv oppfølgingsperiode"
        }

        val updateResult = beregnUpdateResult(
            endringRequest = endringRequest,
            eksisterendeDeltaker = eksisterendeDeltaker,
        ) ?: return eksisterendeDeltaker

        // hent eller opprett Nav-ansatt før transaksjonen starter
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(endringRequest.endretAv)

        // hentOpplaringKategorisering er suspend og må kjøres før db-transaksjon
        val kategoriseringForTiltak = if (endringRequest is EndretOpplaringKategoriseringRequest) {
            opplaringKategoriseringClient.hentOpplaringKategorisering(
                eksisterendeDeltaker.deltakerliste.tiltakstype.tiltakskode,
            )
        } else {
            null
        }

        return deltakerService.upsertAndProduceDeltaker(
            deltaker = updateResult.deltaker,
            erDeltakerSluttdatoEndret = eksisterendeDeltaker.sluttdato != updateResult.deltaker.sluttdato,
            nesteStatus = updateResult.nesteStatus,
            beforeUpsert = { deltaker ->
                val endringsRequestForUpsert = when (endringRequest) {
                    is EndretOpplaringKategoriseringRequest -> {
                        gjennomforingUpserter.lagreOgProduserEnkeltplassEndreInnhold(
                            gjennomforingId = deltaker.deltakerliste.id,
                            kodeverkValg = endringRequest.kodeverkValg(),
                            sertifiseringValg = endringRequest.sertifiseringValg,
                            kategoriseringForTiltak = kategoriseringForTiltak ?: error("Kodeverk mangler"),
                        )

                        endringRequest
                    }

                    is EndretPrisinfoRequest -> {
                        val prisinformasjonId = gjennomforingUpserter.lagreOgProduserPrisinfoEndring(
                            gjennomforingId = deltaker.deltakerliste.id,
                            prisinfo = endringRequest.prisinfo,
                            endretAvNavIdent = endringRequest.endretAv,
                        )

                        endringRequest.copy(prisinformasjonId = prisinformasjonId)
                    }

                    else -> endringRequest
                }

                deltakerEndringService.upsertEndring(
                    endringRequest = endringsRequestForUpsert,
                    endringResultat = updateResult,
                    endretAvNavAnsatt = navAnsatt,
                )

                deltaker
            },
            afterUpsert = {
                when (endringRequest) {
                    is ReaktiverDeltakelseRequest -> slettKladdIfExists(updateResult.deltaker)
                    else -> Unit
                }
            },
        )
    }

    /**
     * Beregner resultatet av en endringsrequest mot eksisterende deltaker.
     *
     * For requests som endrer gjennomføringen direkte ([EndretOpplaringKategoriseringRequest],
     * [EndretPrisinfoRequest]) returneres `null` hvis requesten ikke endrer noe.
     * For kategorisering der kun kodeverk-valg er endret (men ikke beskrivelse), fortsetter
     * flyten med uendret deltaker slik at beforeUpsert lagrer de nye valgene.
     *
     * For øvrige endringstyper anvendes endringen på deltakeren. Hvis deltakeren er uendret
     * og det finnes et godkjent forslag, godkjennes forslaget uten å oppdatere deltaker.
     *
     * @param endringRequest requesten som beskriver ønsket endring
     * @param eksisterendeDeltaker deltakerens nåværende tilstand
     * @return [VellykketEndring] med oppdatert deltaker, eller null hvis deltakeren er uendret
     */
    private fun beregnUpdateResult(
        endringRequest: EndringRequest,
        eksisterendeDeltaker: Deltaker,
    ): VellykketEndring? {
        when (endringRequest) {
            is EndretOpplaringKategoriseringRequest -> {
                val valgErUendret = OpplaringKategoriseringRepoAdapter.erUendretValg(
                    gjennomforingId = eksisterendeDeltaker.deltakerliste.id,
                    endringRequest = endringRequest,
                )
                val beskrivelseErUendret =
                    eksisterendeDeltaker.deltakelsesinnhold?.getAnnetFritekstBeskrivelse() == endringRequest.beskrivelse

                if (valgErUendret && beskrivelseErUendret) {
                    return null
                }

                if (!valgErUendret && beskrivelseErUendret) {
                    // Valg er endret og skal lagres i beforeUpsert, selv om deltakerobjektet ellers er uendret.
                    return VellykketEndring(eksisterendeDeltaker)
                }
            }

            is EndretPrisinfoRequest -> if (PrisinfoRepoAdapter.erUendretPrisinfo(
                    gjennomforingId = eksisterendeDeltaker.deltakerliste.id,
                    endringRequest = endringRequest,
                )
            ) {
                return null
            }

            else -> Unit
        }

        val endring = deltakerEndringService.hentEndringFraRequest(
            endringRequest = endringRequest,
            deltaker = eksisterendeDeltaker,
        )

        // Valider gyldigFra utenfor runCatching slik at ugyldige datoer gir 400 Bad Request
        if (endring is DeltakerEndring.Endring.EndreDeltakelsesmengde) {
            endring.validerGyldigFra(eksisterendeDeltaker)
        }

        return endring
            .anvendPaaDeltaker(
                deltaker = eksisterendeDeltaker,
                getDeltakelsemengder = { deltakerId -> deltakerHistorikkService.getForDeltaker(deltakerId).toDeltakelsesmengder() },
            ).getOrElse {
                log.warn(
                    "Deltaker ${eksisterendeDeltaker.id} med ${endring.javaClass.simpleName} ikke endret, request skulle ikke blitt sendt",
                )

                // hvis forslag er godkjent og deltaker er uendret
                endringRequest.getForslagId()?.let {
                    deltakerEndringService.godkjennForslagForUendretDeltaker(endringRequest)
                }

                null
            }.also {
                if (it != null) {
                    log.info("Endret deltaker ${eksisterendeDeltaker.id} med ${endring.javaClass.simpleName}")
                }
            }
    }

    private fun slettKladdIfExists(deltaker: Deltaker) {
        deltakerRepository
            .getKladdForDeltakerliste(
                deltakerlisteId = deltaker.deltakerliste.id,
                personident = deltaker.navBruker.personident,
            ).onSuccess { deltaker -> deltakerService.deleteDeltaker(deltaker.id) }
    }
}
