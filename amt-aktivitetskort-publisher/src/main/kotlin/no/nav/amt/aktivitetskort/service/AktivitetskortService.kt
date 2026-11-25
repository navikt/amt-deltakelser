package no.nav.amt.aktivitetskort.service

import no.nav.amt.aktivitetskort.client.AktivitetArenaAclClient
import no.nav.amt.aktivitetskort.client.AmtArenaAclClient
import no.nav.amt.aktivitetskort.client.AmtDeltakerClient
import no.nav.amt.aktivitetskort.client.VeilarboppfolgingClient
import no.nav.amt.aktivitetskort.domain.AktivitetStatus
import no.nav.amt.aktivitetskort.domain.Aktivitetskort
import no.nav.amt.aktivitetskort.domain.Arrangor
import no.nav.amt.aktivitetskort.domain.Deltaker
import no.nav.amt.aktivitetskort.domain.DeltakerDbo
import no.nav.amt.aktivitetskort.domain.DeltakerStatusModel
import no.nav.amt.aktivitetskort.domain.EndretAv
import no.nav.amt.aktivitetskort.domain.Handling
import no.nav.amt.aktivitetskort.domain.IKKE_AVTALT_MED_NAV_STATUSER
import no.nav.amt.aktivitetskort.domain.IdentType
import no.nav.amt.aktivitetskort.domain.LenkeType
import no.nav.amt.aktivitetskort.domain.Melding
import no.nav.amt.aktivitetskort.domain.Oppfolgingsperiode
import no.nav.amt.aktivitetskort.domain.Oppgave
import no.nav.amt.aktivitetskort.domain.OppgaveWrapper
import no.nav.amt.aktivitetskort.domain.displayText
import no.nav.amt.aktivitetskort.domain.toAktivitetskortTiltakstype
import no.nav.amt.aktivitetskort.exceptions.FeilOppfolgingsperiodeException
import no.nav.amt.aktivitetskort.exceptions.HistoriskArenaDeltakerException
import no.nav.amt.aktivitetskort.exceptions.IngenOppfolgingsperiodeException
import no.nav.amt.aktivitetskort.kafka.producer.AktivitetskortProducer
import no.nav.amt.aktivitetskort.repositories.ArrangorRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerRepository
import no.nav.amt.aktivitetskort.repositories.MeldingRepository
import no.nav.amt.aktivitetskort.repositories.OppfolgingsperiodeRepository
import no.nav.amt.aktivitetskort.service.StatusMapping.deltakerStatusTilAktivitetStatus
import no.nav.amt.aktivitetskort.service.StatusMapping.deltakerStatusTilEtikett
import no.nav.amt.aktivitetskort.unleash.UnleashConfig.Companion.AKTIVITETSKORT_APP_NAME
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerStatus.Companion.avsluttendeStatuser
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.ConcurrencyFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

@Service
class AktivitetskortService(
    private val meldingRepository: MeldingRepository,
    private val arrangorRepository: ArrangorRepository,
    private val aktivitetArenaAclClient: AktivitetArenaAclClient,
    private val amtArenaAclClient: AmtArenaAclClient,
    private val unleashToggle: CommonUnleashToggle,
    private val veilarboppfolgingClient: VeilarboppfolgingClient,
    private val oppfolgingsperiodeRepository: OppfolgingsperiodeRepository,
    private val deltakerRepository: DeltakerRepository,
    private val aktivitetskortProducer: AktivitetskortProducer,
    private val transactionTemplate: TransactionTemplate,
    private val amtDeltakerClient: AmtDeltakerClient,
    @Value($$"${VEILEDERURL_BASEPATH}") private val veilederUrlBasePath: String,
    @Value($$"${DELTAKERURL_BASEPATH}") private val deltakerUrlBasePath: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getSisteMeldingForDeltaker(deltakerId: UUID) = meldingRepository
        .getByDeltakerId(deltakerId)
        .maxByOrNull { it.createdAt }

    fun lagAktivitetskort(deltaker: Deltaker): Aktivitetskort? = tryOpprettMelding(deltaker.id) { opprettMelding(deltaker) }?.aktivitetskort

    fun lagAktivitetskort(deltakerId: UUID): Aktivitetskort? = amtDeltakerClient
        .getDeltaker(deltakerId)
        .let { deltakerResponse ->
            lagAktivitetskort(Deltaker.fromDeltakerResponse(deltakerResponse))
        }

    fun lagreAktivitetskort(forberedtMelding: ForberedtMelding): Aktivitetskort? =
        tryOpprettMelding(forberedtMelding.deltakerId) { lagreOgPubliser(forberedtMelding) }?.aktivitetskort

    /**
     * Avbryter aktivitetskortet for en slettet deltaker, publiserer endringen og sletter den lokale deltakeren.
     *
     * Bruker den lagrede meldingen i stedet for å hente deltakeren fra amt-deltaker på nytt. Ved behandling
     * av en tombstone kan deltakeren allerede være slettet der, slik at et nytt oppslag feiler og kortet
     * ikke blir avbrutt. Oppdatering, publisering via outbox og lokal sletting skjer i samme transaksjon.
     */
    fun oppdaterAktivitetskortForSlettetDeltaker(
        deltaker: DeltakerDbo,
        melding: Melding,
    ) {
        val avbruttStatus = DeltakerStatusModel(
            type = DeltakerStatus.Type.AVBRUTT,
            aarsak = null,
        )

        val aktivitetskort = melding.aktivitetskort.copy(
            personident = deltaker.personident,
            aktivitetStatus = AktivitetStatus.AVBRUTT,
            endretTidspunkt = LocalDateTime.now(),
            oppgave = null,
            detaljer = melding.aktivitetskort.detaljer.map {
                if (it.label == Aktivitetskort.STATUS_FOR_DELTAKELSE) it.copy(verdi = displayText(avbruttStatus)) else it
            },
            etiketter = listOfNotNull(deltakerStatusTilEtikett(avbruttStatus)),
        )

        val oppdatertMelding = melding.copy(aktivitetskort = aktivitetskort)

        transactionTemplate.executeWithoutResult {
            meldingRepository.upsert(oppdatertMelding)
            aktivitetskortProducer.send(oppdatertMelding.aktivitetskort)
            deltakerRepository.delete(oppdatertMelding.deltakerId)
        }
    }

    fun forberedAktivitetskort(gjennomforingId: UUID): List<ForberedtAktivitetskort> {
        val meldinger = meldingRepository.getByDeltakerlisteId(gjennomforingId).map {
            it to getSisteMeldingForDeltaker(it.deltakerId)
        }
        return meldinger.map { (it, sisteMelding) ->
            val deltaker = Deltaker.fromDeltakerResponse(amtDeltakerClient.getDeltaker(it.deltakerId))
            ForberedtAktivitetskort(it, sisteMelding, forberedMelding(deltaker, meldingId = it.id))
        }
    }

    fun kontrollerMeldingshistorikk(forberedt: ForberedtAktivitetskort) {
        val deltakerId = forberedt.forberedtMelding.deltakerId
        meldingRepository.laasMeldingshistorikk(deltakerId)
        val meldinger = meldingRepository.getByDeltakerId(deltakerId)
        val gjeldende = meldinger.maxByOrNull { it.createdAt }
        val valgt = meldinger
            .filter { it.deltakerlisteId == forberedt.opprinneligMelding.deltakerlisteId }
            .maxByOrNull { it.createdAt }
        if (!sammeMelding(gjeldende, forberedt.sisteMelding) || !sammeMelding(valgt, forberedt.opprinneligMelding)) {
            throw ConcurrencyFailureException(
                "Meldingshistorikken for deltaker $deltakerId er endret. Aktivitetskortet må forberedes på nytt.",
            )
        }
    }

    fun laasMeldingshistorikk(deltakerId: UUID) = meldingRepository.laasMeldingshistorikk(deltakerId)

    private fun sammeMelding(
        a: Melding?,
        b: Melding?,
    ) = a?.id == b?.id &&
        a?.oppfolgingperiode == b?.oppfolgingperiode &&
        a?.modifiedAt == b?.modifiedAt &&
        a == b

    data class ForberedtAktivitetskort(
        val opprinneligMelding: Melding,
        val sisteMelding: Melding?,
        val forberedtMelding: ForberedtMelding,
    )

    /**
     * En oppdatering på en arrangør skal medføre at:
     * - Alle aktivitetskort som er koblet til arrangøren blir oppdatert
     * - Alle aktivitetskort som er koblet til en underordnet arrangør av den oppdaterte arrangøren blir oppdatert
     **/
    fun oppdaterAktivitetskort(arrangor: Arrangor) {
        val underordnedeArrangorer = arrangorRepository.getUnderordnedeArrangorer(arrangor.id)
        val alleOppdaterteArrangorer = listOf(arrangor) + underordnedeArrangorer
        alleOppdaterteArrangorer.forEach { a ->
            meldingRepository
                .getByArrangorId(a.id)
                .filter { it.aktivitetskort.erAktivDeltaker() }
                .forEach { oppdaterAktivitetskort(it.deltakerId, it.id) }

            log.info("Opprettet nye aktivitetskort for arrangør: ${a.id}")
        }
    }

    /*
    Henter aktivitetskortId for deltaker
     * Komet deltakere som ble opprettet før vi begynte å koble aktivitetskort til oppfølgingsperiode
     * Arenadeltakere må alltid hente id fra dab fordi de kan ha opprettet flere aktivitetskort for en
     deltaker uten komets innblanding
     *
     */
    private fun getAktivitetskortId(
        deltaker: Deltaker,
        oppfolgingsperiode: Oppfolgingsperiode,
    ): UUID {
        val nyesteAktivitetskortForDeltaker = getSisteMeldingForDeltaker(deltaker.id)
        val nyesteAktivitetskortId = nyesteAktivitetskortForDeltaker?.id

        if (deltaker.kilde == Kilde.KOMET) {
            return if (nyesteAktivitetskortForDeltaker?.oppfolgingperiode != null &&
                oppfolgingsperiode.id != nyesteAktivitetskortForDeltaker.oppfolgingperiode
            ) {
                log.info("Oppretter nytt aktivitetskort på deltaker ${deltaker.id} som har fått ny oppfølgingsperiode.")
                UUID.randomUUID()
            } else {
                nyesteAktivitetskortId
                    ?: UUID
                        .randomUUID()
                        .also { log.info("Definerer egen aktivitetskortId: $it for deltaker med id ${deltaker.id}") }
            }
        }

        return hentAktivitetskortIdForArenaDeltaker(deltaker.id)
    }

    fun hentAktivitetskortIdForArenaDeltaker(deltakerId: UUID): UUID =
        // Vi MÅ kalle dab for å generere id for arena deltakere for at de skal generere mappingen
        // Selv om vi har en aktivitetskort id på deltaker så kan dab ha opprettet en ny pga endringer i oppfølgingsperiode
        amtArenaAclClient
            .getArenaIdForAmtId(deltakerId)
            ?.also { log.info("deltaker $deltakerId er opprettet i arena med id $it. Henter aktivitetskort id fra AKAS..") }
            ?.let { aktivitetArenaAclClient.getAktivitetIdForArenaId(it) }
            ?.also { log.info("deltaker $deltakerId skal ha aktivitetId: $it") }
            ?: throw IllegalStateException("Arenadeltaker $deltakerId fikk ikke id fra AKAS")

    private fun oppdaterAktivitetskort(
        deltakerId: UUID,
        meldingId: UUID,
    ) {
        val deltakerResponse = amtDeltakerClient.getDeltaker(deltakerId)

        tryOpprettMelding(deltakerId) {
            opprettMelding(
                deltaker = Deltaker.fromDeltakerResponse(deltakerResponse),
                meldingId = meldingId,
            )
        }
    }

    private fun tryOpprettMelding(
        deltakerId: UUID,
        opprett: () -> Melding,
    ): Melding? {
        try {
            return opprett()
        } catch (e: IngenOppfolgingsperiodeException) {
            log.warn("Kan ikke opprette aktivitetskort for deltaker $deltakerId uten oppfølgingsperiode", e)
        } catch (e: HistoriskArenaDeltakerException) {
            log.error("Kan ikke opprette aktivitetskort for historisk arena deltaker $deltakerId", e)
        } catch (e: FeilOppfolgingsperiodeException) {
            log.info("Kan ikke opprette aktivitetskort for deltaker $deltakerId", e)
        }
        return null
    }

    fun opprettMelding(
        deltaker: Deltaker,
        meldingId: UUID? = null,
    ): Melding = lagreOgPubliser(forberedMelding(deltaker, meldingId))

    fun forberedMelding(
        deltaker: Deltaker,
        meldingId: UUID? = null,
    ): ForberedtMelding {
        val oppfolgingsperiode = veilarboppfolgingClient.hentOppfolgingperiode(deltaker.personident)
            ?: return ForberedtMelding.Avvist(
                deltakerId = deltaker.id,
                feil = IngenOppfolgingsperiodeException(
                    "Kan ikke opprette aktivitetskort på deltaker ${deltaker.id} som ikke er under oppfølging",
                ),
            )

        val nyesteAktivitetskortForDeltaker = getSisteMeldingForDeltaker(deltaker.id)

        if (deltaker.kilde == Kilde.ARENA &&
            nyesteAktivitetskortForDeltaker == null &&
            deltaker.status.type in avsluttendeStatuser &&
            deltaker.status.gyldigFra?.isBefore(oppfolgingsperiode.startDato) == true
        ) {
            return ForberedtMelding.Avvist(
                deltakerId = deltaker.id,
                feil = FeilOppfolgingsperiodeException(
                    "Lager ikke aktivitetskort for ukjent arenadeltaker i oppfølgingsperiode: ${oppfolgingsperiode.id}" +
                        "deltaker ${deltaker.id} er avsluttet ${deltaker.status.gyldigFra} " +
                        "før nåværende oppfølgingsperiode startet ${oppfolgingsperiode.startDato}",
                ),
                oppfolgingsperiode = oppfolgingsperiode,
            )
        }

        val aktivitetskortId = try {
            meldingId ?: getAktivitetskortId(
                deltaker = deltaker,
                oppfolgingsperiode = oppfolgingsperiode,
            )
        } catch (e: HistoriskArenaDeltakerException) {
            return ForberedtMelding.Avvist(deltakerId = deltaker.id, feil = e)
        }

        val aktivitetskort = lagAktivitetskort(
            id = aktivitetskortId,
            deltaker = deltaker,
        )

        val melding = Melding(
            id = aktivitetskortId,
            deltakerId = deltaker.id,
            deltakerlisteId = deltaker.gjennomforing.id,
            arrangorId = deltaker.gjennomforing.arrangor.id,
            aktivitetskort = aktivitetskort,
            oppfolgingperiode = oppfolgingsperiode.id,
        )

        return ForberedtMelding.Klar(melding, oppfolgingsperiode)
    }

    private fun lagreOgPubliser(forberedtMelding: ForberedtMelding): Melding {
        when (forberedtMelding) {
            is ForberedtMelding.Avvist -> {
                forberedtMelding.oppfolgingsperiode?.let { oppfolgingsperiodeRepository.upsert(it) }
                throw forberedtMelding.feil
            }

            is ForberedtMelding.Klar -> {
                transactionTemplate.executeWithoutResult {
                    meldingRepository.laasMeldingshistorikk(forberedtMelding.deltakerId)
                    oppfolgingsperiodeRepository.upsert(forberedtMelding.oppfolgingsperiode)
                    meldingRepository.upsert(forberedtMelding.melding)
                    aktivitetskortProducer.send(forberedtMelding.melding.aktivitetskort)
                }

                log.info(
                    "Opprettet nytt aktivitetskort: ${forberedtMelding.melding.aktivitetskort.id} for deltaker: ${forberedtMelding.deltakerId}",
                )
                return forberedtMelding.melding
            }
        }
    }

    sealed interface ForberedtMelding {
        val deltakerId: UUID

        data class Klar(
            val melding: Melding,
            val oppfolgingsperiode: Oppfolgingsperiode,
        ) : ForberedtMelding {
            override val deltakerId: UUID get() = melding.deltakerId
        }

        data class Avvist(
            override val deltakerId: UUID,
            val feil: RuntimeException,
            val oppfolgingsperiode: Oppfolgingsperiode? = null,
        ) : ForberedtMelding
    }

    fun opprettMelding(
        deltaker: DeltakerDbo,
        meldingId: UUID? = null,
    ): Melding = opprettMelding(
        deltaker = Deltaker.fromDeltakerResponse(amtDeltakerClient.getDeltaker(deltaker.id)),
        meldingId = meldingId,
    )

    private fun lagAktivitetskort(
        id: UUID,
        deltaker: Deltaker,
    ) = Aktivitetskort(
        id = id,
        personident = deltaker.personident,
        tittel = deltaker.gjennomforing.visningsnavn,
        aktivitetStatus = deltakerStatusTilAktivitetStatus(deltaker.status.type).getOrThrow(),
        startDato = deltaker.startdato,
        sluttDato = deltaker.sluttdato,
        beskrivelse = null,
        endretAv = EndretAv(AKTIVITETSKORT_APP_NAME, IdentType.SYSTEM),
        endretTidspunkt = LocalDateTime.now(),
        avtaltMedNav = deltaker.status.type !in IKKE_AVTALT_MED_NAV_STATUSER,
        oppgave = oppgaver(deltaker),
        handlinger = getHandlinger(deltaker),
        detaljer = Aktivitetskort.lagDetaljer(deltaker),
        etiketter = listOfNotNull(deltakerStatusTilEtikett(deltaker.status)),
        tiltakstype = deltaker.gjennomforing.tiltakstype.tiltakskode
            .toAktivitetskortTiltakstype(),
    )

    private fun oppgaver(deltaker: Deltaker): OppgaveWrapper? {
        if (!unleashToggle.erKometMasterForTiltakstype(deltaker.gjennomforing.tiltakstype.tiltakskode)) {
            return null
        }

        if (deltaker.status.type != DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
            return null
        }

        return OppgaveWrapper(
            ekstern = Oppgave(
                tekst = "Du har mottatt et utkast til påmelding",
                subtekst = "Les gjennom utkastet før du godkjenner det. Når du godkjenner, blir du meldt på aktiviteten.",
                url = deltaker.deltakerUrl(),
            ),
            intern = null,
        )
    }

    private fun getHandlinger(deltaker: Deltaker): List<Handling> = listOf(
        Handling(
            tekst = "Les mer om din deltakelse",
            subtekst = "",
            url = "$veilederUrlBasePath/${deltaker.id}",
            lenkeType = LenkeType.INTERN,
        ),
        Handling(
            tekst = "Les mer om din deltakelse",
            subtekst = "",
            url = deltaker.deltakerUrl(),
            lenkeType = LenkeType.EKSTERN,
        ),
    )

    private fun Deltaker.deltakerUrl() = "$deltakerUrlBasePath/${this.id}"
}
