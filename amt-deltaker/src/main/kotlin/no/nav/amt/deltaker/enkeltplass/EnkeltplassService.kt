package no.nav.amt.deltaker.enkeltplass

import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.KladdService.Companion.lagEnkeltplassKladdInsertDbo
import no.nav.amt.deltaker.deltaker.KladdService.Companion.lagEnkeltplassUpdateDbo
import no.nav.amt.deltaker.deltaker.VedtakService
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
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
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
    private val tiltakRepository: TiltakstypeRepository,
    private val navEnhetService: NavEnhetService,
    private val navAnsattService: NavAnsattService,
    private val vedtakService: VedtakService,
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
        val tiltak = Tiltakskode.valueOf(tiltakskode.name).let {
            tiltakRepository.get(tiltakskode).getOrThrow()
        }
        val gjennomforing = GjennomforingInsertDbo(
            id = UUID.randomUUID(),
            type = GjennomforingType.Enkeltplass,
            tiltakId = tiltak.id,
            navn = tiltak.navn,
            status = GjennomforingStatusType.KLADD,
            apentForPamelding = false,
            oppstart = null,
            pameldingstype = null,
        )

        val kladd = lagEnkeltplassKladdInsertDbo(
            navBruker.personId,
            gjennomforing.id,
            tiltak,
        )

        Database.transaction {
            deltakerlisteRepository.upsert(gjennomforing)
            deltakerRepository.upsertKladd(kladd)
            DeltakerStatusRepository.lagreStatus(kladd.id, nyDeltakerStatus(DeltakerStatus.Type.KLADD))
        }

        return deltakerRepository.get(kladd.id).getOrThrow()
    }

    suspend fun oppdaterKladd(
        deltakerId: UUID,
        startdato: LocalDate?,
        sluttdato: LocalDate?,
        beskrivelse: String?,
        prisinformasjon: String?,
    ): Deltaker {
        // Trenger egentlig bare deltakeren for tiltakstypen sånn at ledeteksten
        // kan puttes i jsonobjektet i innhold
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()

        require(deltaker.status.type == DeltakerStatus.Type.KLADD) {
            "Kladd oppdatering kan kun brukes på deltaker med status ${DeltakerStatus.Type.KLADD}. Deltaker med id $deltakerId har status ${deltaker.status.type}"
        }

        val gjennomforingUpdateDbo = EnkeltplassGjennomforingUpdateDbo(
            id = deltaker.deltakerliste.id,
            prisinformasjon = prisinformasjon,
        )
        val kladdUpdateDbo = lagEnkeltplassUpdateDbo(
            deltakerId = deltakerId,
            tiltakstype = deltaker.deltakerliste.tiltakstype,
            startdato = startdato,
            sluttdato = sluttdato,
            beskrivelse = beskrivelse,
        )
        Database.transaction {
            deltakerlisteRepository.update(gjennomforingUpdateDbo)
            deltakerRepository.updateEnkeltplassKladd(kladdUpdateDbo)
        }
        return deltakerRepository.get(deltakerId).getOrThrow()
    }

    suspend fun meldPaaDirekte(
        deltakerId: UUID,
        decoratedRequest: EnkeltplassPameldingDecoratedRequest,
    ) {
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()
        val gjennomforing = deltaker.deltakerliste

        require(gjennomforing.gjennomforingstype == GjennomforingType.Enkeltplass) {
            "Kan ikke opprette gjennomforing hos Mulighetsrommet for " +
                "gjennomforingstype ${gjennomforing.gjennomforingstype} for deltaker $deltakerId"
        }

        require(gjennomforing.status == GjennomforingStatusType.KLADD) {
            "Kan ikke opprette gjennomforing hos Mulighetsrommet fordi gjennomforing med id ${gjennomforing.id} ikke er i kladd"
        }

        val gjennomforingUpdateDbo = EnkeltplassGjennomforingUpdateDbo(
            id = deltaker.deltakerliste.id,
            prisinformasjon = decoratedRequest.wrappedRequest.prisinformasjon,
        )

        val utkastUpdateDbo = lagEnkeltplassUpdateDbo(
            deltakerId = deltakerId,
            tiltakstype = deltaker.deltakerliste.tiltakstype,
            startdato = decoratedRequest.wrappedRequest.startdato,
            sluttdato = decoratedRequest.wrappedRequest.sluttdato,
            beskrivelse = decoratedRequest.wrappedRequest.beskrivelse,
        )

        val navEnhetForKostnadssted = navEnhetService.hentEllerOpprettNavEnhet(decoratedRequest.endretAvEnhet)
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(decoratedRequest.endretAv)

        Database.transaction {
            deltakerService.lagreDeltakerStatus(
                deltakerId = deltaker.id,
                nyDeltakerStatus = nyDeltakerStatus(type = DeltakerStatus.Type.SOKT_INN),
                erDeltakerSluttdatoEndret = deltaker.sluttdato !== decoratedRequest.wrappedRequest.sluttdato,
            )

            deltakerlisteRepository.update(gjennomforingUpdateDbo)
            deltakerRepository.updateEnkeltplassKladd(utkastUpdateDbo)

            vedtakService.opprettEllerOppdaterVedtak(
                fattetAvNav = true,
                endretAv = navAnsatt,
                endretAvEnhet = navEnhetForKostnadssted,
                // TODO: Skal deltaker med ny status benyttes her?
                deltaker = deltaker.toDeltakerVedVedtak(),
                fattetDato = null, // fattes når økonomi er godkjent
            )

            gjennomforingRequestProducer.produce(
                GjennomforingRequestPayload.OpprettEnkeltplass(
                    gjennomforingId = gjennomforing.id,
                    tiltakskode = gjennomforing.tiltakstype.tiltakskode,
                    prisinformasjon = decoratedRequest.wrappedRequest.prisinformasjon,
                    organisasjonsnummer = decoratedRequest.wrappedRequest.arrangorOrgnummer,
                    kostnadssted = navEnhetForKostnadssted.enhetsnummer,
                ),
            )
        }
    }
}
