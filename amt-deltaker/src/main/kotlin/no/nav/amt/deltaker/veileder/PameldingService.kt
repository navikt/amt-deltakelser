package no.nav.amt.deltaker.veileder

import no.nav.amt.deltaker.enkeltplass.EnkeltplassService
import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.innbygger.DistribuerEndringService
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.utils.DeltakerUtils
import no.nav.amt.internapi.paamelding.request.AvbrytUtkastRequest
import no.nav.amt.internapi.paamelding.request.UtkastRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.hendelse.HendelseType
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class PameldingService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val vedtakService: VedtakService,
    private val distribuerEndringService: DistribuerEndringService,
    private val innsokPaaFellesOppstartService: InnsokPaaFellesOppstartService,
    private val enkeltplassService: EnkeltplassService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun upsertUtkast(
        deltakerId: UUID,
        utkast: UtkastRequest,
    ): Deltaker {
        val opprinneligDeltaker = deltakerRepository.get(deltakerId).getOrThrow()

        require(kanUpserteUtkast(opprinneligDeltaker.status)) {
            "Kan ikke upserte utkast for deltaker $deltakerId " +
                "med status ${opprinneligDeltaker.status.type}," +
                "status må være ${DeltakerStatus.Type.KLADD} eller ${DeltakerStatus.Type.UTKAST_TIL_PAMELDING}."
        }

        val status = getOppdatertStatus(opprinneligDeltaker, utkast.godkjentAvNav)

        val endretAv = navAnsattService.hentEllerOpprettNavAnsatt(utkast.endretAv)
        val endretAvNavEnhet = navEnhetService.hentEllerOpprettNavEnhet(utkast.endretAvEnhet)

        val oppdatertDeltaker = opprinneligDeltaker.copy(
            deltakelsesinnhold = utkast.deltakelsesinnhold,
            bakgrunnsinformasjon = utkast.bakgrunnsinformasjon,
            deltakelsesprosent = utkast.deltakelsesprosent,
            dagerPerUke = utkast.dagerPerUke,
            status = status,
            sistEndret = LocalDateTime.now(),
        )

        val skalNavFatteVedtak = utkast.godkjentAvNav &&
            oppdatertDeltaker.deltakerliste.pameldingstype == GjennomforingPameldingType.DIREKTE_VEDTAK

        val deltaker = deltakerService.upsertAndProduceDeltaker(
            deltaker = oppdatertDeltaker,
            erDeltakerSluttdatoEndret = opprinneligDeltaker.sluttdato != oppdatertDeltaker.sluttdato,
            beforeUpsert = { deltaker ->
                val oppdatertVedtak = vedtakService
                    .opprettEllerOppdaterVedtak(
                        fattetAvNav = skalNavFatteVedtak,
                        endretAv = endretAv,
                        endretAvEnhet = endretAvNavEnhet,
                        deltaker = deltaker.toDeltakerVedVedtak(),
                        fattetDato = if (skalNavFatteVedtak) LocalDateTime.now() else null,
                    )

                val deltakerMedNyttVedtak = oppdatertDeltaker.copy(vedtaksinformasjon = oppdatertVedtak.tilVedtaksInformasjon())
                if (utkast.godkjentAvNav &&
                    oppdatertDeltaker.deltakerliste.pameldingstype == GjennomforingPameldingType.TRENGER_GODKJENNING
                ) {
                    innsokPaaFellesOppstartService.nyttInnsokUtkastGodkjentAvNav(deltakerMedNyttVedtak, opprinneligDeltaker.status)
                }
                deltakerMedNyttVedtak
            },
            afterUpsert = { deltaker ->

                distribuerEndringService.produceHendelseForUtkast(deltaker, endretAv, endretAvNavEnhet) { utkastDto ->
                    when {
                        utkast.godkjentAvNav -> HendelseType.NavGodkjennUtkast(utkastDto)
                        opprinneligDeltaker.status.type == DeltakerStatus.Type.KLADD -> HendelseType.OpprettUtkast(utkastDto)
                        else -> HendelseType.EndreUtkast(utkastDto)
                    }
                }
            },
        )

        log.info("Upsertet utkast for deltaker med id $deltakerId, meldt på direkte: ${utkast.godkjentAvNav}")
        return deltaker
    }

    fun innbyggerGodkjennUtkast(deltakerId: UUID): Deltaker = deltakerService.upsertAndProduceDeltaker(
        deltaker = deltakerRepository.get(deltakerId).getOrThrow(),
        erDeltakerSluttdatoEndret = false,
        beforeUpsert = { deltaker ->
            if (deltaker.deltakerliste.deltakelserMaaGodkjennes) {
                innbyggerGodkjennInnsok(deltaker)
            } else {
                vedtakService.innbyggerFattVedtak(deltaker.id)

                val deltakerStatus = if (deltaker.status.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
                    DeltakerUtils.nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
                } else {
                    deltaker.status
                }

                deltaker.copy(
                    status = deltakerStatus,
                    sistEndret = LocalDateTime.now(),
                )
            }
        },
        afterUpsert = { deltaker ->
            distribuerEndringService.hendelseForUtkastGodkjentAvInnbygger(deltaker)
            if (deltaker.erEnkeltplass) {
                enkeltplassService.publiserGjennomforing(deltaker)
            }
        },
    )

    private fun innbyggerGodkjennInnsok(opprinneligDeltaker: Deltaker): Deltaker {
        val oppdatertDeltaker = opprinneligDeltaker.copy(
            status = DeltakerUtils.nyDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            sistEndret = LocalDateTime.now(),
        )

        innsokPaaFellesOppstartService.nyttInnsokUtkastGodkjentAvDeltaker(
            deltaker = oppdatertDeltaker,
            forrigeStatus = opprinneligDeltaker.status,
        )

        return oppdatertDeltaker
    }

    suspend fun avbrytUtkast(
        deltakerId: UUID,
        avbrytUtkastRequest: AvbrytUtkastRequest,
    ) {
        val opprinneligDeltaker = deltakerRepository.get(deltakerId).getOrThrow()

        if (opprinneligDeltaker.status.type != DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
            log.warn(
                "Kan ikke avbryte utkast for deltaker med id ${opprinneligDeltaker.id} som har status ${opprinneligDeltaker.status.type}",
            )
            throw IllegalArgumentException(
                "Kan ikke avbryte utkast for deltaker med id ${opprinneligDeltaker.id} som har status ${opprinneligDeltaker.status.type}",
            )
        }

        val endretAv = navAnsattService.hentEllerOpprettNavAnsatt(avbrytUtkastRequest.avbruttAv)
        val endretAvNavEnhet = navEnhetService.hentEllerOpprettNavEnhet(avbrytUtkastRequest.avbruttAvEnhet)

        val oppdatertDeltaker = opprinneligDeltaker.copy(
            status = DeltakerUtils.nyDeltakerStatus(DeltakerStatus.Type.AVBRUTT_UTKAST),
            sistEndret = LocalDateTime.now(),
        )

        deltakerService.upsertAndProduceDeltaker(
            deltaker = oppdatertDeltaker,
            erDeltakerSluttdatoEndret = opprinneligDeltaker.sluttdato != oppdatertDeltaker.sluttdato,
            beforeUpsert = { deltaker ->
                val vedtak = vedtakService.avbrytVedtak(
                    deltakerId = deltaker.id,
                    avbruttAv = endretAv,
                    avbruttAvNavEnhet = endretAvNavEnhet,
                )

                deltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksInformasjon())
            },
            afterUpsert = { deltaker ->
                distribuerEndringService.produceHendelseForUtkast(deltaker, endretAv, endretAvNavEnhet) { utkastDto ->
                    HendelseType.AvbrytUtkast(utkastDto)
                }
            },
        )

        log.info("Avbrutt utkast for deltaker med id $deltakerId")
    }

    companion object {
        private fun kanUpserteUtkast(opprinneligDeltakerStatus: DeltakerStatus) = opprinneligDeltakerStatus.type in listOf(
            DeltakerStatus.Type.KLADD,
            DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
        )

        // Brukes for "del utkast med innbygger", "oppdater utkast" og "godkjenn utkast"
        // godkjentAvNav=true betyr "meld på uten å dele utkast"
        internal fun getOppdatertStatus(
            opprinneligDeltaker: Deltaker,
            godkjentAvNav: Boolean,
        ): DeltakerStatus = if (godkjentAvNav) {
            if (opprinneligDeltaker.deltakerliste.pameldingstype == GjennomforingPameldingType.TRENGER_GODKJENNING) {
                DeltakerUtils.nyDeltakerStatus(DeltakerStatus.Type.SOKT_INN)
            } else {
                DeltakerUtils.nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
            }
        } else {
            // Virker som denne er sånn for å tilrettelegge for at man kan bruke det samme api endepunktet for både å dele utkast(som medfører statusendring) og å oppdatere eksisterende utkast,
            when (opprinneligDeltaker.status.type) {
                DeltakerStatus.Type.KLADD -> DeltakerUtils.nyDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING)

                DeltakerStatus.Type.UTKAST_TIL_PAMELDING -> opprinneligDeltaker.status

                else -> throw IllegalArgumentException(
                    "Kan ikke upserte utkast for deltaker " +
                        "med status ${opprinneligDeltaker.status.type}," +
                        "status må være ${DeltakerStatus.Type.KLADD} eller ${DeltakerStatus.Type.UTKAST_TIL_PAMELDING}.",
                )
            }
        }
    }
}
