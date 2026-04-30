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
import no.nav.amt.deltaker.repository.dbo.DeltakerKladdUpsertDbo
import no.nav.amt.deltaker.repository.dbo.GjennomforingInsertDbo
import no.nav.amt.deltaker.service.DeltakerService
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.utils.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
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
            DeltakerStatusRepository.lagreStatus(kladdDbo.id, nyDeltakerStatus(DeltakerStatus.Type.KLADD))
        }

        return deltakerRepository.get(kladdDbo.id).getOrThrow()
    }

    suspend fun oppdaterKladd(
        deltakerId: UUID,
        oppdaterKladdRequest: OppdaterEnkeltplassKladdRequest,
    ) {
        // Deltakeren hentes for å få tak i ledeteksten fra tiltakstypen
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()

        require(deltaker.deltakerliste.gjennomforingstype == GjennomforingType.Enkeltplass) {
            "oppdaterKladd kan kun brukes på enkeltplass-deltakere. Deltaker med id $deltakerId har gjennomforingstype ${deltaker.deltakerliste.gjennomforingstype}"
        }
        require(deltaker.status.type == DeltakerStatus.Type.KLADD) {
            "Kladd oppdatering kan kun brukes på deltaker med status ${DeltakerStatus.Type.KLADD}. Deltaker med id $deltakerId har status ${deltaker.status.type}"
        }

        // Arrangøren oppdateres bare hvis den er endret i requesten
        val arrangor = oppdaterKladdRequest.arrangorUnderenhet?.let {
            hentArrangorHvisEndret(organisasjonsnummer = it, eksisterendeArrangor = deltaker.deltakerliste.arrangor)
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
                byggDeltakerUpdateDbo(
                    deltakerId = deltakerId,
                    deltaker = deltaker,
                    startdato = oppdaterKladdRequest.startdato,
                    sluttdato = oppdaterKladdRequest.sluttdato,
                    beskrivelse = oppdaterKladdRequest.beskrivelse,
                ),
            )
        }
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

    /** Oppdaterer innholdet i utkastet uten å endre status. */
    suspend fun oppdaterUtkast(
        deltakerId: UUID,
        decoratedRequest: EnkeltplassPameldingDecoratedRequest,
    ): Deltaker = lagreOgPubliser(
        deltakerId = deltakerId,
        decoratedRequest = decoratedRequest,
        nyStatus = null,
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

    /**
     * Lagrer oppdateringer til deltaker og gjennomføring i én transaksjon,
     * oppretter/oppdaterer vedtak og publiserer gjennomføringsrequest til Kafka.
     */
    private suspend fun lagreOgPubliser(
        deltakerId: UUID,
        decoratedRequest: EnkeltplassPameldingDecoratedRequest,
        nyStatus: DeltakerStatus.Type?,
    ): Deltaker {
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()
        val gjennomforing = deltaker.deltakerliste

        require(gjennomforing.gjennomforingstype == GjennomforingType.Enkeltplass) {
            "Kan ikke opprette gjennomforing hos Mulighetsrommet for " +
                "gjennomforingstype ${gjennomforing.gjennomforingstype} for deltaker $deltakerId"
        }
        require(gjennomforing.status == GjennomforingStatusType.KLADD) {
            "Kan ikke opprette gjennomforing hos Mulighetsrommet fordi gjennomforing med id ${gjennomforing.id} ikke er i kladd"
        }

        val arrangor = arrangorService.hentArrangor(decoratedRequest.wrappedRequest.arrangorUnderenhet)
        val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(decoratedRequest.endretAvEnhet)
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(decoratedRequest.endretAv)

        return Database.transaction {
            if (nyStatus != null) {
                deltakerService.lagreDeltakerStatus(
                    deltakerId = deltaker.id,
                    nyDeltakerStatus = nyDeltakerStatus(type = nyStatus),
                    erDeltakerSluttdatoEndret = deltaker.sluttdato != decoratedRequest.wrappedRequest.sluttdato,
                )
            }

            deltakerlisteRepository.update(
                EnkeltplassGjennomforingUpdateDbo(
                    id = gjennomforing.id,
                    prisinformasjon = decoratedRequest.wrappedRequest.prisinformasjon,
                    arrangorId = arrangor.id,
                ),
            )
            deltakerRepository.updateEnkeltplassKladd(
                byggDeltakerUpdateDbo(
                    deltakerId = deltakerId,
                    deltaker = deltaker,
                    startdato = decoratedRequest.wrappedRequest.startdato,
                    sluttdato = decoratedRequest.wrappedRequest.sluttdato,
                    beskrivelse = decoratedRequest.wrappedRequest.beskrivelse,
                ),
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
                byggGjennomforingRequest(deltakerMedVedtak, decoratedRequest),
            )

            deltakerMedVedtak
        }
    }

    fun publiserGjennomforing(deltaker: Deltaker) {
        val vedtak = vedtakService.hentIkkeFattetVedtakOrThrow(deltaker.id)
        val ansvarligEnhet = navEnhetRepository.getOrThrow(vedtak.opprettetAvEnhet)
        val ansvarligNavAnsatt = navAnsattRepository.getOrThrow(vedtak.opprettetAv)
        val gjennomforing = deltaker.deltakerliste
        val payload = GjennomforingRequestPayload.OpprettEnkeltplass(
            gjennomforingId = deltaker.deltakerliste.id,
            tiltakskode = deltaker.deltakerliste.tiltakstype.tiltakskode,
            prisinformasjon = requireNotNull(gjennomforing.prisinformasjon) {
                "Kan ikke publisere gjennomføring ${gjennomforing.id}: prisinformasjon mangler"
            },
            organisasjonsnummer = requireNotNull(gjennomforing.arrangor) {
                "Kan ikke publisere gjennomføring ${gjennomforing.id}: arrangør mangler"
            }.organisasjonsnummer,
            ansvarligEnhet = ansvarligEnhet.enhetsnummer,
            opprettetAv = ansvarligNavAnsatt.navIdent,
        )
        gjennomforingRequestProducer.produce(payload)
    }

    fun byggGjennomforingRequest(
        deltaker: Deltaker,
        decoratedRequest: EnkeltplassPameldingDecoratedRequest,
    ) = GjennomforingRequestPayload.OpprettEnkeltplass(
        gjennomforingId = deltaker.deltakerliste.id,
        tiltakskode = deltaker.deltakerliste.tiltakstype.tiltakskode,
        prisinformasjon = decoratedRequest.wrappedRequest.prisinformasjon,
        organisasjonsnummer = decoratedRequest.wrappedRequest.arrangorUnderenhet,
        ansvarligEnhet = decoratedRequest.endretAvEnhet,
        opprettetAv = decoratedRequest.endretAv,
    )

    private fun byggDeltakerUpdateDbo(
        deltakerId: UUID,
        deltaker: Deltaker,
        startdato: LocalDate?,
        sluttdato: LocalDate?,
        beskrivelse: String?,
    ) = EnkeltplassDeltakerUpdateDbo(
        id = deltakerId,
        startdato = startdato,
        sluttdato = sluttdato,
        deltakelsesinnhold = Deltakelsesinnhold(
            ledetekst = deltaker.deltakerliste.tiltakstype.innhold
                ?.ledetekst,
            innhold = beskrivelse?.let { listOf(Innhold.createFritekstInnhold(it)) } ?: emptyList(),
        ),
    )

    private suspend fun hentArrangorHvisEndret(
        organisasjonsnummer: String,
        eksisterendeArrangor: Arrangor?,
    ): Arrangor? = if (eksisterendeArrangor?.organisasjonsnummer == organisasjonsnummer) {
        eksisterendeArrangor
    } else {
        arrangorService.hentArrangor(organisasjonsnummer)
    }
}
