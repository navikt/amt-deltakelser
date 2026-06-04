package no.nav.amt.deltaker.enkeltplass

import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestProducer
import no.nav.amt.deltaker.innbygger.NavBrukerService
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.DeltakerStatusRepository
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.repository.KodeverkValgRepository
import no.nav.amt.deltaker.repository.SertifiseringValgRepository
import no.nav.amt.deltaker.repository.dbo.DeltakerKladdUpsertDbo
import no.nav.amt.deltaker.repository.dbo.GjennomforingInsertDbo
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.utils.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.lib.ktor.clients.kodeverk.KodeverkClient
import no.nav.amt.lib.ktor.clients.kodeverk.OpplaringKategoriseringResponse
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
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
    private val kodeverkClient: KodeverkClient,
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
    // kan ikke være suspending fordi den kalles i en transaksjon, derfor må kodeverk sendes inn som parameter
    fun publiserGjennomforing(
        deltaker: Deltaker,
        kodeverk: OpplaringKategoriseringResponse?,
    ) {
        val vedtak = vedtakService.hentIkkeFattetVedtakOrThrow(deltaker.id)
        val ansvarligEnhet = navEnhetRepository.getOrThrow(vedtak.opprettetAvEnhet)
        val ansvarligNavAnsatt = navAnsattRepository.getOrThrow(vedtak.opprettetAv)
        val gjennomforing = deltaker.deltakerliste

        val payload = GjennomforingRequestPayload.OpprettEnkeltplass(
            gjennomforingId = deltaker.deltakerliste.id,
            tiltakskode = deltaker.deltakerliste.tiltakstype.tiltakskode,
            prisinformasjon = checkNotNull(gjennomforing.prisinformasjon) {
                "Kan ikke publisere gjennomføring ${gjennomforing.id}: prisinformasjon mangler"
            },
            organisasjonsnummer = checkNotNull(gjennomforing.arrangor) {
                "Kan ikke publisere gjennomføring ${gjennomforing.id}: arrangør mangler"
            }.organisasjonsnummer,
            ansvarligEnhet = ansvarligEnhet.enhetsnummer,
            opprettetAv = ansvarligNavAnsatt.navIdent,
            kategorisering = kodeverk?.toOpplaringKategorisering(
                kodeverkValg = KodeverkValgRepository.hentKodeverkValg(gjennomforing.id),
                sertifiseringValg = SertifiseringValgRepository.hentSertifiseringValg(gjennomforing.id),
            ),
        )

        gjennomforingRequestProducer.produce(payload)
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

        Database.transaction {
            deltakerlisteRepository.update(
                EnkeltplassGjennomforingUpdateDbo(
                    id = deltaker.deltakerliste.id,
                    prisinformasjon = oppdaterKladdRequest.prisinformasjon,
                    arrangorId = arrangor?.id,
                ),
            )

            deltakerRepository.updateEnkeltplassKladd(
                lagDeltakerUpdateDbo(
                    deltaker = deltaker,
                    startdato = oppdaterKladdRequest.startdato,
                    sluttdato = oppdaterKladdRequest.sluttdato,
                    beskrivelse = oppdaterKladdRequest.beskrivelse,
                ),
            )

            lagreKodeverkValg(
                deltakerlisteId = deltaker.deltakerliste.id,
                kodeverkValg = oppdaterKladdRequest.kodeverkValg,
                sertifiseringValg = oppdaterKladdRequest.sertifiseringValg,
            )
        }
    }

    /**
     * Lagrer oppdateringer til deltaker og gjennomføring i én transaksjon,
     * oppretter/oppdaterer vedtak og publiserer gjennomføringsrequest til Kafka.
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
/*
    TODO: Undersøk om det er OK å kalle denne metoden etter at request om opprettelse av
    gjennomføring allerede er kalt
        require(gjennomforing.status == GjennomforingStatusType.KLADD) {
            "Kan ikke opprette gjennomforing hos Mulighetsrommet fordi gjennomforing med id ${gjennomforing.id} ikke er i kladd"
        }
*/

        val arrangor = arrangorService.hentArrangor(request.arrangorUnderenhet)
        val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(decoratedRequest.endretAvEnhet)
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(decoratedRequest.endretAv)
        val kodeverk = kodeverkClient.hentKodeverk(gjennomforing.tiltakstype.tiltakskode)

        return Database.transaction {
            deltakerService.lagreDeltakerStatus(
                deltakerId = deltaker.id,
                nyDeltakerStatus = nyDeltakerStatus(type = nyStatus),
                erDeltakerSluttdatoEndret = deltaker.sluttdato != request.sluttdato,
            )

            deltakerlisteRepository.update(
                EnkeltplassGjennomforingUpdateDbo(
                    id = gjennomforing.id,
                    prisinformasjon = request.prisinformasjon,
                    arrangorId = arrangor.id,
                ),
            )

            deltakerRepository.updateEnkeltplassKladd(
                lagDeltakerUpdateDbo(
                    deltaker = deltaker,
                    startdato = request.startdato,
                    sluttdato = request.sluttdato,
                    beskrivelse = request.beskrivelse,
                ),
            )

            lagreKodeverkValg(
                deltakerlisteId = gjennomforing.id,
                kodeverkValg = request.kodeverkValg,
                sertifiseringValg = request.sertifiseringValg,
            )

            val oppdatertDeltaker = deltakerRepository.get(deltakerId).getOrThrow()

            vedtakService.opprettEllerOppdaterVedtak(
                fattetAvNav = false,
                endretAv = navAnsatt,
                endretAvEnhet = navEnhet,
                deltaker = oppdatertDeltaker.toDeltakerVedVedtak(),
                fattetDato = null,
            )

            val deltakerMedVedtak = deltakerRepository.get(deltakerId).getOrThrow()

            gjennomforingRequestProducer.produce(
                GjennomforingRequestPayload.OpprettEnkeltplass(
                    gjennomforingId = deltakerMedVedtak.deltakerliste.id,
                    tiltakskode = deltakerMedVedtak.deltakerliste.tiltakstype.tiltakskode,
                    prisinformasjon = request.prisinformasjon,
                    organisasjonsnummer = request.arrangorUnderenhet,
                    ansvarligEnhet = decoratedRequest.endretAvEnhet,
                    opprettetAv = decoratedRequest.endretAv,
                    kategorisering = kodeverk.toOpplaringKategorisering(
                        kodeverkValg = request.kodeverkValg,
                        sertifiseringValg = request.sertifiseringValg,
                    ),
                ),
            )

            deltakerMedVedtak
        }
    }

    private suspend fun hentArrangorHvisEndret(
        organisasjonsnummer: String,
        eksisterendeArrangor: Arrangor?,
    ): Arrangor? = if (eksisterendeArrangor?.organisasjonsnummer == organisasjonsnummer) {
        eksisterendeArrangor
    } else {
        arrangorService.hentArrangor(organisasjonsnummer)
    }

    private fun lagreKodeverkValg(
        deltakerlisteId: UUID,
        kodeverkValg: Set<UUID>?,
        sertifiseringValg: Set<SertifiseringValg>?,
    ) {
        kodeverkValg?.let { internalKodeverkValg ->
            if (internalKodeverkValg.isNotEmpty()) {
                KodeverkValgRepository.lagreKodeverkValg(
                    deltakerlisteId = deltakerlisteId,
                    valg = internalKodeverkValg,
                )
            } else {
                KodeverkValgRepository.deleteForGjennomforing(deltakerlisteId)
            }
        }

        sertifiseringValg?.let { internalSertifiseringValg ->
            // insert-only, sletter eksisterende valg før insert
            SertifiseringValgRepository.deleteForGjennomforing(deltakerlisteId)

            if (internalSertifiseringValg.isNotEmpty()) {
                SertifiseringValgRepository.lagreSertifiseringValg(
                    deltakerlisteId = deltakerlisteId,
                    sertifiseringValg = internalSertifiseringValg,
                )
            }
        }
    }

    companion object {
        private fun lagDeltakerUpdateDbo(
            deltaker: Deltaker,
            startdato: LocalDate?,
            sluttdato: LocalDate?,
            beskrivelse: String?,
        ) = EnkeltplassDeltakerUpdateDbo(
            id = deltaker.id,
            startdato = startdato,
            sluttdato = sluttdato,
            deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = deltaker.deltakerliste.tiltakstype.innhold
                    ?.ledetekst,
                innhold = beskrivelse?.let { listOf(Innhold.createFritekstInnhold(it)) } ?: emptyList(),
            ),
        )

        private fun OpplaringKategoriseringResponse.toOpplaringKategorisering(
            kodeverkValg: Set<UUID>?,
            sertifiseringValg: Set<SertifiseringValg>?,
        ): GjennomforingRequestPayload.OpprettEnkeltplass.OpplaringKategorisering =
            GjennomforingRequestPayload.OpprettEnkeltplass.OpplaringKategorisering(
                verdier = kodeverkValg
                    ?.let { grupperKodeverkvalgPerRepresenterer(it) }
                    ?: emptyMap(),
                sertifiseringer = sertifiseringValg ?: emptySet(),
            )
    }
}
