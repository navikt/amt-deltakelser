package no.nav.amt.aktivitetskort.service

import no.nav.amt.aktivitetskort.client.AktivitetArenaAclClient
import no.nav.amt.aktivitetskort.client.AmtArenaAclClient
import no.nav.amt.aktivitetskort.client.AmtDeltakerClient
import no.nav.amt.aktivitetskort.client.VeilarboppfolgingClient
import no.nav.amt.aktivitetskort.domain.Aktivitetskort
import no.nav.amt.aktivitetskort.domain.Arrangor
import no.nav.amt.aktivitetskort.domain.Deltaker
import no.nav.amt.aktivitetskort.domain.DeltakerDbo
import no.nav.amt.aktivitetskort.domain.Deltakerliste
import no.nav.amt.aktivitetskort.domain.EndretAv
import no.nav.amt.aktivitetskort.domain.Handling
import no.nav.amt.aktivitetskort.domain.IKKE_AVTALT_MED_NAV_STATUSER
import no.nav.amt.aktivitetskort.domain.IdentType
import no.nav.amt.aktivitetskort.domain.LenkeType
import no.nav.amt.aktivitetskort.domain.Melding
import no.nav.amt.aktivitetskort.domain.Oppfolgingsperiode
import no.nav.amt.aktivitetskort.domain.Oppgave
import no.nav.amt.aktivitetskort.domain.OppgaveWrapper
import no.nav.amt.aktivitetskort.domain.toAktivitetskortTiltakstype
import no.nav.amt.aktivitetskort.exceptions.FeilOppfolgingsperiodeException
import no.nav.amt.aktivitetskort.exceptions.HistoriskArenaDeltakerException
import no.nav.amt.aktivitetskort.exceptions.IngenOppfolgingsperiodeException
import no.nav.amt.aktivitetskort.repositories.ArrangorRepository
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
    private val transactionTemplate: TransactionTemplate,
    private val amtDeltakerClient: AmtDeltakerClient,
    @Value($$"${veilederurl.basepath}") private val veilederUrlBasePath: String,
    @Value($$"${deltakerurl.basepath}") private val deltakerUrlBasePath: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getSisteMeldingForDeltaker(deltakerId: UUID) = meldingRepository
        .getByDeltakerId(deltakerId)
        .maxByOrNull { it.createdAt }

    fun lagAktivitetskort(deltakerId: UUID): Aktivitetskort? = amtDeltakerClient
        .getDeltaker(deltakerId)
        .let { tryOpprettMelding(deltaker = Deltaker.fromDeltakerResponse(it))?.aktivitetskort }

    fun oppdaterAktivitetskortForSlettetdeltaker(
        deltaker: DeltakerDbo,
        meldingId: UUID,
    ): Aktivitetskort {
        val melding = opprettMelding(deltaker, meldingId = meldingId)

        return melding.aktivitetskort
    }

    fun oppdaterAktivitetskort(deltakerliste: Deltakerliste) = meldingRepository
        .getByDeltakerlisteId(deltakerliste.id)
        .mapNotNull { oppdaterAktivitetskort(it.deltakerId, it.id)?.aktivitetskort }
        .also { log.info("Opprettet nye aktivitetskort for deltakerliste: ${deltakerliste.id}") }

    /*
    En oppdatering på en arrangør skal medføre at:
        - Alle aktivitetskort som er koblet til arrangøren blir oppdatert
        - Alle aktivitetskort som er koblet til en underordnet arrangør av den oppdaterte arrangøren blir oppdatert
     */
    fun oppdaterAktivitetskort(arrangor: Arrangor): List<Aktivitetskort> {
        val underordnedeArrangorer = arrangorRepository.getUnderordnedeArrangorer(arrangor.id)
        val alleOppdaterteArrangorer = listOf(arrangor) + underordnedeArrangorer
        return alleOppdaterteArrangorer.flatMap { a ->
            meldingRepository
                .getByArrangorId(a.id)
                .filter { it.aktivitetskort.erAktivDeltaker() }
                .mapNotNull { oppdaterAktivitetskort(it.deltakerId, it.id)?.aktivitetskort }
                .also { log.info("Opprettet nye aktivitetskort for arrangør: ${a.id}") }
        }
    }

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

    fun oppdaterAktivitetskort(
        deltakerId: UUID,
        meldingId: UUID,
    ): Melding? = amtDeltakerClient
        .getDeltaker(deltakerId)
        .let { deltaker -> tryOpprettMelding(Deltaker.fromDeltakerResponse(deltaker), meldingId) }
        ?: run {
            log.warn("Deltaker med id $deltakerId finnes ikke lenger")
            null
        }

    private fun tryOpprettMelding(
        deltaker: Deltaker,
        meldingId: UUID? = null,
    ): Melding? {
        try {
            return opprettMelding(deltaker, meldingId)
        } catch (e: IngenOppfolgingsperiodeException) {
            log.warn("Kan ikke opprette aktivitetskort for deltaker ${deltaker.id} uten oppfølgingsperiode", e)
        } catch (e: HistoriskArenaDeltakerException) {
            log.error("Kan ikke opprette aktivitetskort for historisk arena deltaker ${deltaker.id}", e)
        } catch (e: FeilOppfolgingsperiodeException) {
            log.info("Kan ikke opprette aktivitetskort for deltaker ${deltaker.id}", e)
        }
        return null
    }

    fun opprettMelding(
        deltaker: Deltaker,
        meldingId: UUID? = null,
    ): Melding {
        val oppfolgingsperiode = veilarboppfolgingClient.hentOppfolgingperiode(deltaker.personident)
            ?: throw IngenOppfolgingsperiodeException(
                "Kan ikke opprette aktivitetskort på deltaker ${deltaker.id} som ikke er under oppfølging",
            )

        val nyesteAktivitetskortForDeltaker = getSisteMeldingForDeltaker(deltaker.id)

        if (deltaker.kilde == Kilde.ARENA &&
            nyesteAktivitetskortForDeltaker == null &&
            deltaker.status.type in avsluttendeStatuser &&
            deltaker.status.gyldigFra?.isBefore(oppfolgingsperiode.startDato) == true
        ) {
            oppfolgingsperiodeRepository.upsert(oppfolgingsperiode)
            throw FeilOppfolgingsperiodeException(
                "Lager ikke aktivitetskort for ukjent arenadeltaker i oppfølgingsperiode: ${oppfolgingsperiode.id}" +
                    "deltaker ${deltaker.id} er avsluttet ${deltaker.status.gyldigFra} " +
                    "før nåværende oppfølgingsperiode startet ${oppfolgingsperiode.startDato}",
            )
        }

        val aktivitetskortId = meldingId ?: getAktivitetskortId(
            deltaker = deltaker,
            oppfolgingsperiode = oppfolgingsperiode,
        )

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

        transactionTemplate.executeWithoutResult {
            oppfolgingsperiodeRepository.upsert(oppfolgingsperiode)
            meldingRepository.upsert(melding)
        }

        log.info("Opprettet nytt aktivitetskort: ${melding.aktivitetskort.id} for deltaker: ${deltaker.id}")
        return melding
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
                subtekst = "Vi vil at du leser gjennom og godkjenner utkastet, slik at vi kan melde deg på aktiviteten.",
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
