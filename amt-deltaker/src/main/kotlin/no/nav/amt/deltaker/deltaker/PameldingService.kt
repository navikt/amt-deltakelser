package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.AvbrytUtkastRequest
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.UtkastRequest
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class PameldingService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val deltakerListeRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val vedtakService: VedtakService,
    private val hendelseService: HendelseService,
    private val innsokPaaFellesOppstartService: InnsokPaaFellesOppstartService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun opprettDeltaker(deltakerListeId: UUID, personIdent: String): Deltaker {
        val eksisterendeDeltaker = deltakerRepository
            .getFlereForPerson(personIdent, deltakerListeId)
            .firstOrNull { !it.harSluttet() }

        if (eksisterendeDeltaker != null) {
            log.warn("Deltakeren ${eksisterendeDeltaker.id} er allerede opprettet og deltar fortsatt")
            return eksisterendeDeltaker
        }

        return deltakerService
            .upsertAndProduceDeltaker(
                deltaker = lagDeltaker(
                    navBrukerService.get(personIdent).getOrThrow(),
                    deltakerListeRepository.get(deltakerListeId).getOrThrow(),
                ),
                erDeltakerSluttdatoEndret = false,
            ).also { deltaker ->
                log.info("Lagret kladd for deltaker med id ${deltaker.id}")
            }
    }

    suspend fun slettKladd(deltakerId: UUID) {
        deltakerRepository.get(deltakerId).onSuccess { opprinneligDeltaker ->
            if (opprinneligDeltaker.status.type != DeltakerStatus.Type.KLADD) {
                log.warn("Kan ikke slette deltaker med id $deltakerId som har status ${opprinneligDeltaker.status.type}")
                throw IllegalArgumentException(
                    "Kan ikke slette deltaker med id ${opprinneligDeltaker.id} som har status ${opprinneligDeltaker.status.type}",
                )
            }
            Database.transaction {
                deltakerService.deleteDeltaker(deltakerId)
            }
        }
    }

    suspend fun upsertUtkast(deltakerId: UUID, utkast: UtkastRequest): Deltaker {
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

                hendelseService.produceHendelseForUtkast(deltaker, endretAv, endretAvNavEnhet) { utkastDto ->
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

    suspend fun innbyggerGodkjennUtkast(deltakerId: UUID): Deltaker = deltakerService.upsertAndProduceDeltaker(
        deltaker = deltakerRepository.get(deltakerId).getOrThrow(),
        erDeltakerSluttdatoEndret = false,
        beforeUpsert = { deltaker ->
            if (deltaker.deltakerliste.deltakelserMaaGodkjennes) {
                innbyggerGodkjennInnsok(deltaker)
            } else {
                vedtakService.innbyggerFattVedtak(deltaker.id)

                val deltakerStatus = if (deltaker.status.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
                    nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
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
            hendelseService.hendelseForUtkastGodkjentAvInnbygger(deltaker)
        },
    )

    private fun innbyggerGodkjennInnsok(opprinneligDeltaker: Deltaker): Deltaker {
        val oppdatertDeltaker = opprinneligDeltaker.copy(
            status = nyDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            sistEndret = LocalDateTime.now(),
        )

        innsokPaaFellesOppstartService.nyttInnsokUtkastGodkjentAvDeltaker(
            deltaker = oppdatertDeltaker,
            forrigeStatus = opprinneligDeltaker.status,
        )

        return oppdatertDeltaker
    }

    suspend fun avbrytUtkast(deltakerId: UUID, avbrytUtkastRequest: AvbrytUtkastRequest) {
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
            status = nyDeltakerStatus(DeltakerStatus.Type.AVBRUTT_UTKAST),
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
                hendelseService.produceHendelseForUtkast(deltaker, endretAv, endretAvNavEnhet) { utkastDto ->
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

        private fun lagDeltaker(navBruker: NavBruker, deltakerListe: Deltakerliste) = Deltaker(
            id = UUID.randomUUID(),
            navBruker = navBruker,
            deltakerliste = deltakerListe,
            startdato = null,
            sluttdato = null,
            dagerPerUke = null,
            deltakelsesprosent = null,
            bakgrunnsinformasjon = null,
            deltakelsesinnhold = Deltakelsesinnhold(deltakerListe.tiltakstype.innhold?.ledetekst, emptyList()),
            status = nyDeltakerStatus(DeltakerStatus.Type.KLADD),
            vedtaksinformasjon = null,
            sistEndret = LocalDateTime.now(),
            kilde = Kilde.KOMET,
            erManueltDeltMedArrangor = false,
            opprettet = LocalDateTime.now(),
        )

        internal fun getOppdatertStatus(opprinneligDeltaker: Deltaker, godkjentAvNav: Boolean): DeltakerStatus = if (godkjentAvNav) {
            if (opprinneligDeltaker.deltakerliste.pameldingstype == GjennomforingPameldingType.TRENGER_GODKJENNING) {
                nyDeltakerStatus(DeltakerStatus.Type.SOKT_INN)
            } else {
                nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
            }
        } else {
            when (opprinneligDeltaker.status.type) {
                DeltakerStatus.Type.KLADD -> nyDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING)

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
