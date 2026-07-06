package no.nav.amt.deltaker.enkeltplass

import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestProducer
import no.nav.amt.deltaker.innbygger.NavBrukerService
import no.nav.amt.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.DeltakerStatusRepository
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.repository.OpplaringKategoriseringRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.dbo.DeltakerKladdUpsertDbo
import no.nav.amt.deltaker.repository.dbo.GjennomforingInsertDbo
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.utils.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.veileder.InnsokService
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse
import no.nav.amt.lib.ktor.clients.kodeverk.OpplaringKategoriseringClient
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
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
    private val gjennomforingRequestProducer: GjennomforingRequestProducer,
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
    private val tiltakRepository: TiltakRepository,
    private val navEnhetService: NavEnhetService,
    private val navEnhetRepository: NavEnhetRepository,
    private val navAnsattService: NavAnsattService,
    private val navAnsattRepository: NavAnsattRepository,
    private val vedtakService: VedtakService,
    private val arrangorService: ArrangorService,
    private val opplaringKategoriseringClient: OpplaringKategoriseringClient,
    private val deltakerProducerService: DeltakerProducerService,
    private val innsokService: InnsokService,
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

        oppdaterKladdEllerUtkast(
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
        )

        return deltakerRepository.get(deltakerId).getOrThrow()
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

    // benyttes av PameldingService#innbyggerGodkjennUtkast
    fun publiserGjennomforing(deltaker: Deltaker) {
        val vedtak = vedtakService.hentIkkeFattetVedtakOrThrow(deltaker.id)
        val ansvarligEnhet = navEnhetRepository.getOrThrow(vedtak.opprettetAvEnhet)
        val ansvarligNavAnsatt = navAnsattRepository.getOrThrow(vedtak.opprettetAv)
        val gjennomforing = deltaker.deltakerliste

        produceUpsertGjennomforing(
            deltaker = deltaker,
            orgnr = checkNotNull(gjennomforing.arrangor) {
                "Kan ikke publisere gjennomføring ${gjennomforing.id}: arrangør mangler"
            }.organisasjonsnummer,
            endretAvNavIdent = ansvarligNavAnsatt.navIdent,
            endretAvEnhet = ansvarligEnhet.enhetsnummer,
        )
    }

    private suspend fun oppdaterKladdEllerUtkast(
        deltaker: Deltaker,
        oppdaterKladdRequest: OppdaterEnkeltplassKladdRequest,
    ) {
        require(deltaker.deltakerliste.gjennomforingstype == GjennomforingType.Enkeltplass) {
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

        Database.transaction {
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

            oppdaterKladdRequest.prisinformasjon?.let { prisinfo ->
                PrisinfoRepoAdapter.lagrePrisinfo(
                    gjennomforingId = deltaker.deltakerliste.id,
                    prisinformasjon = prisinfo,
                )
            }

            val opplaringKategoriseringValg = kategoriseringResponse.toOpplaringKategoriseringValg(
                kategoriseringValg = oppdaterKladdRequest.kodeverkValg ?: emptySet(),
                sertifiseringValg = oppdaterKladdRequest.sertifiseringValg ?: emptySet(),
            )

            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltaker.deltakerliste.id,
                valgteVerdier = oppdaterKladdRequest.kodeverkValg?.let {
                    opplaringKategoriseringValg.valgteKategoriseringer
                },
                valgteSertifiseringer = oppdaterKladdRequest.sertifiseringValg?.let {
                    opplaringKategoriseringValg.valgteSertifiseringer
                },
            )
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

            PrisinfoRepoAdapter.lagrePrisinfo(
                gjennomforingId = gjennomforing.id,
                prisinformasjon = request.prisinformasjon,
            )

            lagreKategorisering(
                gjennomforingId = gjennomforing.id,
                kategoriseringForTiltak = kategoriseringForTiltak,
                valgteKodeverk = request.kodeverkValg,
                valgteSertifiseringer = request.sertifiseringValg,
            )

            lagreVedtak(
                deltakerId = deltakerId,
                endretAv = navAnsatt,
                endretAvEnhet = navEnhet,
            )

            val deltakerMedVedtak = deltakerRepository.get(deltakerId).getOrThrow()
            if (nyStatus == DeltakerStatus.Type.SOKT_INN) {
                innsokService.nyttInnsokUtkastGodkjentAvNav(deltakerMedVedtak, deltaker.status)
            }

            produceUpsertGjennomforing(
                deltaker = deltakerMedVedtak,
                orgnr = request.arrangorUnderenhet,
                endretAvNavIdent = decoratedRequest.endretAv,
                endretAvEnhet = decoratedRequest.endretAvEnhet,
            )

            // hvis gjennomføring er opprettet, publiser deltaker
            if (gjennomforing.status != GjennomforingStatusType.KLADD) {
                deltakerProducerService.produce(deltakerMedVedtak)
            }

            deltakerMedVedtak
        }
    }

    private fun lagreKategorisering(
        gjennomforingId: UUID,
        kategoriseringForTiltak: OpplaringKategoriseringResponse,
        valgteKodeverk: Set<UUID>?,
        valgteSertifiseringer: Set<SertifiseringValg>?,
    ) {
        val opplaringKategoriseringValg = kategoriseringForTiltak.toOpplaringKategoriseringValg(
            kategoriseringValg = valgteKodeverk ?: emptySet(),
            sertifiseringValg = valgteSertifiseringer ?: emptySet(),
        )

        OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
            gjennomforingId = gjennomforingId,
            valgteVerdier = valgteKodeverk?.let { opplaringKategoriseringValg.valgteKategoriseringer },
            valgteSertifiseringer = valgteSertifiseringer?.let { opplaringKategoriseringValg.valgteSertifiseringer },
        )
    }

    private fun lagreVedtak(
        deltakerId: UUID,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
    ) {
        val oppdatertDeltaker = deltakerRepository.get(deltakerId).getOrThrow()

        vedtakService.opprettEllerOppdaterVedtak(
            fattetAvNav = false,
            endretAv = endretAv,
            endretAvEnhet = endretAvEnhet,
            deltaker = oppdatertDeltaker.toDeltakerVedVedtak(),
            fattetDato = null,
        )
    }

    internal fun produceUpsertGjennomforing(
        deltaker: Deltaker,
        orgnr: String,
        endretAvNavIdent: String,
        endretAvEnhet: String,
    ) {
        val upsertPayload = GjennomforingRequestPayload.UpsertEnkeltplass(
            tiltakskode = deltaker.deltakerliste.tiltakstype.tiltakskode,
            prisinformasjon = GjennomforingRequestPayload.Prisinformasjon.fromAmtPrisinfo(
                PrisinfoRepoAdapter.hentPrisinfo(deltaker.deltakerliste.id)
                    ?: throw IllegalStateException("Prisinfo mangler for gjennomføring ${deltaker.deltakerliste.id}"),
            ),
            organisasjonsnummer = orgnr,
            ansvarligEnhet = endretAvEnhet,
            opprettetAv = endretAvNavIdent,
            kategorisering = deltaker.deltakerliste.opplaringKategorisering?.toMulighetsrommetKategorisering(),
        )
        val gjennomforingPayload = when (val statusType = deltaker.status.type) {
            DeltakerStatus.Type.UTKAST_TIL_PAMELDING -> GjennomforingRequestPayload.EnkeltplassUtkast(
                gjennomforingId = deltaker.deltakerliste.id,
                payload = upsertPayload,
            )

            DeltakerStatus.Type.SOKT_INN -> GjennomforingRequestPayload.EnkeltplassSoktInn(
                gjennomforingId = deltaker.deltakerliste.id,
                payload = upsertPayload,
                totrinnkontroll = GjennomforingRequestPayload.Totrinnskontroll(
                    id = deltaker.id,
                    behandletAv = endretAvNavIdent,
                ),
            )

            else -> throw IllegalStateException("Deltaker ${deltaker.id} har status $statusType")
        }

        gjennomforingRequestProducer.produce(gjennomforingPayload)
    }

    private suspend fun hentArrangorHvisEndret(
        organisasjonsnummer: String,
        eksisterendeArrangor: Arrangor?,
    ): Arrangor? = if (eksisterendeArrangor?.organisasjonsnummer == organisasjonsnummer) {
        eksisterendeArrangor
    } else {
        arrangorService.hentArrangor(organisasjonsnummer)
    }

    companion object {
        fun OpplaringKategoriseringValg.toMulighetsrommetKategorisering() =
            GjennomforingRequestPayload.UpsertEnkeltplass.OpplaringKategorisering(
                sertifiseringer = valgteSertifiseringer,
                verdier = valgteKategoriseringer.associate { it.representerer to it.valg.keys },
            )

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
