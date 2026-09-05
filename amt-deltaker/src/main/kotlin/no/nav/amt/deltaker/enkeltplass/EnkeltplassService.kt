package no.nav.amt.deltaker.enkeltplass

import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.innbygger.NavBrukerService
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.DeltakerStatusRepository
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.repository.OpplaringKategoriseringRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.dbo.DeltakerKladdUpsertDbo
import no.nav.amt.deltaker.repository.dbo.GjennomforingInsertDbo
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.DistribuerEndringService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.utils.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.veileder.InnsokService
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.lib.ktor.clients.kodeverk.OpplaringKategoriseringClient
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.database.Database
import java.time.LocalDate
import java.util.UUID

class EnkeltplassService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
    private val tiltakRepository: TiltakRepository,
    private val navEnhetService: NavEnhetService,
    private val navAnsattService: NavAnsattService,
    private val vedtakService: VedtakService,
    private val arrangorService: ArrangorService,
    private val opplaringKategoriseringClient: OpplaringKategoriseringClient,
    private val deltakerProducerService: DeltakerProducerService,
    private val innsokService: InnsokService,
    private val distribuerEndringService: DistribuerEndringService,
    private val gjennomforingUpserter: GjennomforingUpserter,
) {
    suspend fun opprettKladd(
        tiltakskode: Tiltakskode,
        personident: String,
    ): Deltaker {
        deltakerRepository
            .getKladd(personident, tiltakskode)
            .getOrNull()
            ?.takeIf { it.erEnkeltplass }
            ?.let { return it }

        val navBruker = navBrukerService.get(personident).getOrThrow()
        val tiltakstype = tiltakRepository.get(tiltakskode).getOrThrow()

        val gjennomforing = GjennomforingInsertDbo(
            id = UUID.randomUUID(),
            type = GjennomforingType.Enkeltplass,
            tiltakId = tiltakstype.id,
            navn = tiltakstype.navn,
            status = GjennomforingStatusType.KLADD,
            apentForPamelding = false,
            oppstart = Oppstartstype.ENKELTPLASS,
            pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
        )

        val kladdDbo = DeltakerKladdUpsertDbo(
            id = UUID.randomUUID(),
            navBrukerId = navBruker.personId,
            deltakerlisteId = gjennomforing.id,
            bakgrunnsinformasjon = null,
            deltakelsesinnhold = Deltakelsesinnhold(tiltakstype.innhold?.ledetekst, emptyList()),
            kilde = Kilde.KOMET,
            erManueltDeltMedArrangor = false,
        )

        Database.transaction {
            deltakerlisteRepository.upsert(gjennomforing)
            deltakerRepository.upsertKladd(kladdDbo)
            DeltakerStatusRepository.lagreStatus(
                deltakerId = kladdDbo.id,
                deltakerStatus = nyDeltakerStatus(DeltakerStatus.Type.KLADD),
            )
        }

        return deltakerRepository.get(kladdDbo.id).getOrThrow()
    }

    suspend fun oppdaterKladd(
        deltakerId: UUID,
        oppdaterKladdRequest: OppdaterEnkeltplassKladdRequest,
    ) {
        // Deltakeren hentes for å få tak i ledeteksten fra tiltakstypen
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()

        require(deltaker.status.type == DeltakerStatus.Type.KLADD) {
            "Kladd oppdatering kan kun brukes på deltaker med status ${DeltakerStatus.Type.KLADD}. Deltaker med id $deltakerId har status ${deltaker.status.type}"
        }

        oppdaterKladdEllerUtkast(
            deltaker = deltaker,
            oppdaterKladdRequest = oppdaterKladdRequest,
        )
    }

    /** Oppdaterer innholdet i utkastet uten å endre status. */
    suspend fun oppdaterUtkast(
        deltakerId: UUID,
        decoratedRequest: EnkeltplassPameldingDecoratedRequest,
    ): Deltaker {
        // Deltakeren hentes for å få tak i ledeteksten fra tiltakstypen
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()

        require(deltaker.status.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
            "Oppdatering av utkast kan kun benyttes for deltaker med status ${DeltakerStatus.Type.UTKAST_TIL_PAMELDING}. " +
                "Deltaker $deltakerId har status ${deltaker.status.type}"
        }

        val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(decoratedRequest.endretAvEnhet)
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(decoratedRequest.endretAv)

        return oppdaterKladdEllerUtkast(
            deltaker = deltaker,
            // TODO: Vurder å benytte kun OppdaterEnkeltplassKladdRequest
            // EnkeltplassPameldingRequest og OppdaterEnkeltplassKladdRequest er veldig like
            oppdaterKladdRequest = with(decoratedRequest.wrappedRequest) {
                OppdaterEnkeltplassKladdRequest(
                    beskrivelse = beskrivelse,
                    prisinformasjon = prisinformasjon,
                    arrangorUnderenhet = arrangorUnderenhet,
                    startdato = startdato,
                    sluttdato = sluttdato,
                    kodeverkValg = kodeverkValg,
                    sertifiseringValg = sertifiseringValg,
                    dagerPerUke = dagerPerUke,
                )
            },
            afterUpdate = { oppdatertDeltaker ->
                // Gjør det mulig for Utkast-siden å vise riktig endringstidspunkt
                val oppdatertDeltakerMedVedtak = lagreVedtakIkkeFattet(
                    deltaker = oppdatertDeltaker,
                    endretAv = navAnsatt,
                    endretAvEnhet = navEnhet,
                )
                distribuerEndringService.produceHendelseForUtkast(oppdatertDeltakerMedVedtak, navAnsatt, navEnhet) {
                    HendelseType.EndreUtkast(it)
                }
                oppdatertDeltakerMedVedtak
            },
        )
    }

    /** Oppdaterer utkastet og setter status til [DeltakerStatus.Type.UTKAST_TIL_PAMELDING] for deling med innbygger. */
    suspend fun delUtkastMedInnbygger(
        deltakerId: UUID,
        decoratedRequest: EnkeltplassPameldingDecoratedRequest,
    ): Deltaker = lagreOgPubliser(
        deltakerId = deltakerId,
        decoratedRequest = decoratedRequest,
        nyStatus = DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
    )

    suspend fun meldPaaDirekte(
        deltakerId: UUID,
        decoratedRequest: EnkeltplassPameldingDecoratedRequest,
    ) {
        lagreOgPubliser(
            deltakerId = deltakerId,
            decoratedRequest = decoratedRequest,
            nyStatus = DeltakerStatus.Type.SOKT_INN,
        )
    }

    private suspend fun oppdaterKladdEllerUtkast(
        deltaker: Deltaker,
        oppdaterKladdRequest: OppdaterEnkeltplassKladdRequest,
        afterUpdate: ((Deltaker) -> Deltaker)? = null,
    ): Deltaker {
        require(deltaker.deltakerliste.erNyForskriftOpplaring) {
            "oppdaterKladd kan kun brukes på enkeltplass-deltakere. Deltaker med id ${deltaker.id} har gjennomforingstype ${deltaker.deltakerliste.gjennomforingstype}"
        }

        // Arrangøren oppdateres bare hvis den er endret i requesten
        val arrangor = oppdaterKladdRequest.arrangorUnderenhet?.let {
            hentArrangorHvisEndret(
                organisasjonsnummer = it,
                eksisterendeArrangor = deltaker.deltakerliste.arrangor,
            )
        }

        val kategoriseringResponse = opplaringKategoriseringClient.hentOpplaringKategorisering(
            deltaker.deltakerliste.tiltakstype.tiltakskode,
        )

        return Database.transaction {
            deltakerlisteRepository.update(
                EnkeltplassGjennomforingUpdateDbo(
                    id = deltaker.deltakerliste.id,
                    arrangorId = arrangor?.id,
                ),
            )

            deltakerRepository.updateEnkeltplass(
                lagDeltakerUpdateDbo(
                    deltaker = deltaker,
                    startdato = oppdaterKladdRequest.startdato,
                    sluttdato = oppdaterKladdRequest.sluttdato,
                    beskrivelse = oppdaterKladdRequest.beskrivelse,
                    dagerPerUke = oppdaterKladdRequest.dagerPerUke,
                ),
            )

            lagreKategoriseringOgPrisinfo(
                gjennomforingId = deltaker.deltakerliste.id,
                kodeverkValg = oppdaterKladdRequest.kodeverkValg,
                sertifiseringValg = oppdaterKladdRequest.sertifiseringValg,
                prisinformasjon = oppdaterKladdRequest.prisinformasjon,
                kategoriseringForTiltak = kategoriseringResponse,
            )

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()
            afterUpdate?.invoke(oppdatertDeltaker) ?: oppdatertDeltaker
        }
    }

    /**
     * Lagrer oppdateringer til deltaker og gjennomføring i én transaksjon,
     * oppretter/oppdaterer vedtak og publiserer gjennomføringsrequest til Kafka.
     * OBS: Denne kan ikke brukes for andre endringer på deltakelse i dette formatet
     * Fordi det må ikke publiseres flere meldinger av
     */
    private suspend fun lagreOgPubliser(
        deltakerId: UUID,
        decoratedRequest: EnkeltplassPameldingDecoratedRequest,
        nyStatus: DeltakerStatus.Type,
    ): Deltaker {
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()
        val gjennomforing = deltaker.deltakerliste
        val request = decoratedRequest.wrappedRequest

        require(gjennomforing.gjennomforingstype == GjennomforingType.Enkeltplass) {
            "Kan ikke opprette gjennomforing hos Mulighetsrommet for " +
                "gjennomforingstype ${gjennomforing.gjennomforingstype} for deltaker $deltakerId"
        }
        require(deltaker.status.type == DeltakerStatus.Type.KLADD || deltaker.status.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
            "Kan ikke opprette gjennomforing hos Mulighetsrommet fordi deltaker $deltakerId i ${deltaker.status.type}"
        }

        val arrangor = arrangorService.hentArrangor(request.arrangorUnderenhet)
        val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(decoratedRequest.endretAvEnhet)
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(decoratedRequest.endretAv)
        val kategoriseringForTiltak = opplaringKategoriseringClient.hentOpplaringKategorisering(gjennomforing.tiltakstype.tiltakskode)

        return Database.transaction {
            deltakerlisteRepository.update(
                EnkeltplassGjennomforingUpdateDbo(
                    id = gjennomforing.id,
                    arrangorId = arrangor.id,
                ),
            )

            deltakerService.lagreDeltakerStatus(
                deltakerId = deltaker.id,
                nyDeltakerStatus = nyDeltakerStatus(type = nyStatus),
                erDeltakerSluttdatoEndret = deltaker.sluttdato != request.sluttdato,
            )

            deltakerRepository.updateEnkeltplass(
                lagDeltakerUpdateDbo(
                    deltaker = deltaker,
                    startdato = request.startdato,
                    sluttdato = request.sluttdato,
                    beskrivelse = request.beskrivelse,
                    dagerPerUke = request.dagerPerUke,
                ),
            )

            val oppdatertDeltaker = deltakerRepository.get(deltakerId).getOrThrow()
            val deltakerMedVedtak = lagreVedtakIkkeFattet(
                deltaker = oppdatertDeltaker,
                endretAv = navAnsatt,
                endretAvEnhet = navEnhet,
            )
            if (nyStatus == DeltakerStatus.Type.SOKT_INN) {
                innsokService.nyttInnsokUtkastGodkjentAvNav(deltakerMedVedtak, deltaker.status)
            }

            lagreKategoriseringOgPrisinfo(
                gjennomforingId = gjennomforing.id,
                kodeverkValg = request.kodeverkValg,
                sertifiseringValg = request.sertifiseringValg,
                prisinformasjon = request.prisinformasjon,
                kategoriseringForTiltak = kategoriseringForTiltak,
            )

            gjennomforingUpserter.produserGjennomforingUpsert(
                deltaker = deltakerMedVedtak,
                endretAvNavIdent = decoratedRequest.endretAv,
                endretAvEnhet = decoratedRequest.endretAvEnhet,
            )

            distribuerEndringService.produceHendelseForUtkast(
                deltaker = deltakerMedVedtak,
                navAnsatt = navAnsatt,
                enhet = navEnhet,
            ) { utkastDto ->
                when {
                    nyStatus == DeltakerStatus.Type.SOKT_INN -> HendelseType.NavGodkjennUtkast(utkastDto)
                    deltaker.status.type == DeltakerStatus.Type.KLADD -> HendelseType.OpprettUtkast(utkastDto)
                    else -> HendelseType.EndreUtkast(utkastDto)
                }
            }

            // hvis gjennomføring er opprettet, publiser deltaker
            if (gjennomforing.status != GjennomforingStatusType.KLADD) {
                deltakerProducerService.produce(deltakerMedVedtak)
            }

            deltakerMedVedtak
        }
    }

    private fun lagreVedtakIkkeFattet(
        deltaker: Deltaker,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
    ): Deltaker {
        val vedtak = vedtakService.opprettEllerOppdaterVedtak(
            fattetAvNav = false,
            endretAv = endretAv,
            endretAvEnhet = endretAvEnhet,
            deltaker = deltaker.toDeltakerVedVedtak(),
            fattetDato = null,
        )

        return deltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksInformasjon())
    }

    private suspend fun hentArrangorHvisEndret(
        organisasjonsnummer: String,
        eksisterendeArrangor: Arrangor?,
    ): Arrangor? = if (eksisterendeArrangor?.organisasjonsnummer == organisasjonsnummer) {
        eksisterendeArrangor
    } else {
        arrangorService.hentArrangor(organisasjonsnummer)
    }

    internal fun lagreKategoriseringOgPrisinfo(
        gjennomforingId: UUID,
        kodeverkValg: Set<UUID>?,
        sertifiseringValg: Set<SertifiseringValg>?,
        prisinformasjon: PrisinformasjonDto?,
        kategoriseringForTiltak: OpplaringKategoriseringResponse,
    ) {
        prisinformasjon?.let {
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingId,
                prisinformasjon = it,
            )
        }

        val opplaringKategoriseringValg = kategoriseringForTiltak.toOpplaringKategoriseringValg(
            kategoriseringValg = kodeverkValg ?: emptySet(),
            sertifiseringValg = sertifiseringValg ?: emptySet(),
        )

        OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
            gjennomforingId = gjennomforingId,
            valgteVerdier = kodeverkValg?.let { opplaringKategoriseringValg.valgteKategoriseringer },
            valgteSertifiseringer = sertifiseringValg?.let { opplaringKategoriseringValg.valgteSertifiseringer },
        )
    }

    companion object {
        private fun lagDeltakerUpdateDbo(
            deltaker: Deltaker,
            startdato: LocalDate?,
            sluttdato: LocalDate?,
            beskrivelse: String?,
            dagerPerUke: Int?,
        ) = EnkeltplassDeltakerUpdateDbo(
            id = deltaker.id,
            startdato = startdato,
            sluttdato = sluttdato,
            dagerPerUke = dagerPerUke?.toFloat(),
            deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = deltaker.deltakerliste.tiltakstype.innhold
                    ?.ledetekst,
                innhold = beskrivelse?.let { listOf(Innhold.createFritekstInnhold(it)) } ?: emptyList(),
            ),
        )
    }
}
