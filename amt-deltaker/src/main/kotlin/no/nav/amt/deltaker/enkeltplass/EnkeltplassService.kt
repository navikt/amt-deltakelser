package no.nav.amt.deltaker.enkeltplass

import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.VedtakService
import no.nav.amt.deltaker.deltaker.db.DeltakerKladdUpsertDbo
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerStatusRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.GjennomforingInsertDbo
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestProducer
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.OppdaterEnkeltplassKladdRequest
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.database.Database
import java.time.LocalDateTime
import java.util.UUID

class EnkeltplassService(
    private val gjennomforingRequestProducer: GjennomforingRequestProducer,
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
    private val tiltakstypeRepository: TiltakstypeRepository,
    private val navEnhetService: NavEnhetService,
    private val navAnsattService: NavAnsattService,
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
        val tiltakstype = tiltakstypeRepository.get(tiltakskode).getOrThrow()

        val gjennomforing = GjennomforingInsertDbo(
            id = UUID.randomUUID(),
            type = GjennomforingType.Enkeltplass,
            tiltakId = tiltakstype.id,
            navn = tiltakstype.navn,
            status = GjennomforingStatusType.KLADD,
            apentForPamelding = false,
            oppstart = null,
            pameldingstype = null,
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
        // Trenger egentlig bare deltakeren for tiltakstypen sånn at ledeteksten
        // kan puttes i jsonobjektet i innhold
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()

        require(deltaker.deltakerliste.gjennomforingstype == GjennomforingType.Enkeltplass) {
            "oppdaterKladd kan kun brukes på enkeltplass-deltakere. Deltaker med id $deltakerId har gjennomforingstype ${deltaker.deltakerliste.gjennomforingstype}"
        }

        require(deltaker.status.type == DeltakerStatus.Type.KLADD) {
            "Kladd oppdatering kan kun brukes på deltaker med status ${DeltakerStatus.Type.KLADD}. Deltaker med id $deltakerId har status ${deltaker.status.type}"
        }

        // hvis arrangor er endret
        val arrangor = oppdaterKladdRequest.arrangorUnderenhet?.let {
            hentArrangor(
                organisasjonsnummer = it,
                eksisterendeArrangor = deltaker.deltakerliste.arrangor,
            )
        }

        val gjennomforingUpdateDbo = EnkeltplassGjennomforingUpdateDbo(
            id = deltaker.deltakerliste.id,
            prisinformasjon = oppdaterKladdRequest.prisinformasjon,
            arrangorId = arrangor?.id,
        )

        val kladdUpdateDbo = EnkeltplassDeltakerUpdateDbo(
            id = deltakerId,
            startdato = oppdaterKladdRequest.startdato,
            sluttdato = oppdaterKladdRequest.sluttdato,
            deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = deltaker.deltakerliste.tiltakstype.innhold
                    ?.ledetekst,
                innhold = oppdaterKladdRequest.beskrivelse?.let { innerBeskrivelse ->
                    listOf(Innhold.createFritekstInnhold(innerBeskrivelse))
                } ?: emptyList(),
            ),
        )

        Database.transaction {
            deltakerlisteRepository.update(gjennomforingUpdateDbo)
            deltakerRepository.updateEnkeltplassKladd(kladdUpdateDbo)
        }
    }

    suspend fun oppdaterUtkast(
        deltakerId: UUID,
        decoratedRequest: EnkeltplassPameldingDecoratedRequest,
    ): Deltaker = oppdaterDeltaker(
        deltakerId = deltakerId,
        decoratedRequest = decoratedRequest,
    )

    suspend fun delUtkastMedInnbygger(
        deltakerId: UUID,
        decoratedRequest: EnkeltplassPameldingDecoratedRequest,
    ): Deltaker {
        val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(decoratedRequest.endretAvEnhet)
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(decoratedRequest.endretAv)

        return oppdaterDeltaker(deltakerId, decoratedRequest, DeltakerStatus.Type.UTKAST_TIL_PAMELDING) { deltaker ->
            vedtakService.opprettEllerOppdaterVedtak(
                fattetAvNav = false,
                endretAv = navAnsatt,
                endretAvEnhet = navEnhet,
                deltaker = deltaker.toDeltakerVedVedtak(),
                fattetDato = null, // fattes når økonomi er godkjent
            )
        }
    }

    suspend fun meldPaaDirekte(
        deltakerId: UUID,
        decoratedRequest: EnkeltplassPameldingDecoratedRequest,
    ) {
        val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(decoratedRequest.endretAvEnhet)
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(decoratedRequest.endretAv)

        oppdaterDeltaker(deltakerId, decoratedRequest, DeltakerStatus.Type.SOKT_INN) { deltaker ->
            vedtakService.opprettEllerOppdaterVedtak(
                // Er det riktig at dette fattes? Tror det siden det er sånn vi gjør det med de andre som har søkt inn status
                // (gjelder også fattetDato)
                fattetAvNav = true,
                endretAv = navAnsatt,
                endretAvEnhet = navEnhet,
                deltaker = deltaker.toDeltakerVedVedtak(),
                fattetDato = LocalDateTime.now(),
            )
            gjennomforingRequestProducer.produce(
                GjennomforingRequestPayload.OpprettEnkeltplass(
                    gjennomforingId = deltaker.deltakerliste.id,
                    tiltakskode = deltaker.deltakerliste.tiltakstype.tiltakskode,
                    prisinformasjon = decoratedRequest.wrappedRequest.prisinformasjon,
                    organisasjonsnummer = decoratedRequest.wrappedRequest.arrangorUnderenhet,
                    ansvarligEnhet = decoratedRequest.endretAvEnhet,
                    opprettetAv = decoratedRequest.endretAv,
                ),
            )
        }
    }

    private suspend fun oppdaterDeltaker(
        deltakerId: UUID,
        decoratedRequest: EnkeltplassPameldingDecoratedRequest,
        nyDeltakerStatus: DeltakerStatus.Type? = null,
        doInTxBlock: (Deltaker) -> Unit = { _ -> },
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

        val gjennomforingUpdateDbo = EnkeltplassGjennomforingUpdateDbo(
            id = deltaker.deltakerliste.id,
            prisinformasjon = decoratedRequest.wrappedRequest.prisinformasjon,
            arrangorId = arrangor.id,
        )

        val utkastUpdateDbo = EnkeltplassDeltakerUpdateDbo(
            id = deltakerId,
            startdato = decoratedRequest.wrappedRequest.startdato,
            sluttdato = decoratedRequest.wrappedRequest.sluttdato,
            deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = deltaker.deltakerliste.tiltakstype.innhold
                    ?.ledetekst,
                innhold = listOf(
                    Innhold.createFritekstInnhold(decoratedRequest.wrappedRequest.beskrivelse),
                ),
            ),
        )

        lateinit var oppdatertDeltaker: Deltaker

        Database.transaction {
            nyDeltakerStatus?.let {
                deltakerService.lagreDeltakerStatus(
                    deltakerId = deltaker.id,
                    nyDeltakerStatus = nyDeltakerStatus(type = nyDeltakerStatus),
                    erDeltakerSluttdatoEndret = deltaker.sluttdato != decoratedRequest.wrappedRequest.sluttdato,
                )
            }

            deltakerlisteRepository.update(gjennomforingUpdateDbo)
            deltakerRepository.updateEnkeltplassKladd(utkastUpdateDbo)

            oppdatertDeltaker = deltakerRepository.get(deltakerId).getOrThrow()

            doInTxBlock(oppdatertDeltaker)
        }

        return oppdatertDeltaker
    }

    private suspend fun hentArrangor(
        organisasjonsnummer: String,
        eksisterendeArrangor: Arrangor? = null,
    ): Arrangor? = if (eksisterendeArrangor?.organisasjonsnummer == organisasjonsnummer) {
        eksisterendeArrangor
    } else {
        arrangorService.hentArrangor(organisasjonsnummer)
    }
}
