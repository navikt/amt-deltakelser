package no.nav.amt.deltaker.enkeltplass

import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload.UpsertEnkeltplass
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestProducer
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.OpplaringKategoriseringRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepository
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

/**
 * Håndterer publisering av gjennomføringsforespørsler til Mulighetsrommet via Kafka.
 *
 * Bygger opp korrekt payload basert på deltakerens status og sender til [GjennomforingRequestProducer].
 */
class GjennomforingUpserter(
    private val navEnhetRepository: NavEnhetRepository,
    private val navAnsattRepository: NavAnsattRepository,
    private val vedtakService: VedtakService,
    private val gjennomforingRequestProducer: GjennomforingRequestProducer,
    private val deltakerRepository: DeltakerRepository,
) {
    /**
     * Publiserer gjennomføring basert på ansvarlig Nav-ansatt fra det ufatttede vedtaket.
     *
     * Henter vedtak og tilhørende Nav-ansatt og enhet, og kaller [produserGjennomforingUpsert].
     * Benyttes av `innbyggerGodkjennUtkast`.
     *
     * @param deltaker Deltakeren som skal publiseres.
     */
    fun produserGjennomforing(deltaker: Deltaker) {
        val vedtak = vedtakService.hentIkkeFattetVedtakOrThrow(deltaker.id)
        val ansvarligEnhet = navEnhetRepository.getOrThrow(vedtak.opprettetAvEnhet)
        val ansvarligNavAnsatt = navAnsattRepository.getOrThrow(vedtak.opprettetAv)

        produserGjennomforingUpsert(
            deltaker = deltaker,
            endretAvNavIdent = ansvarligNavAnsatt.navIdent,
            endretAvEnhet = ansvarligEnhet.enhetsnummer,
        )
    }

    /**
     * Lagrer ny prisinformasjon og sender endringsforespørsel til totrinnskontroll.
     *
     * Prisinfo lagres med status [no.nav.amt.deltaker.repository.dbo.PrisinfoDbo.PrisinfoStatus.SENDT]
     * og kobles til gjennomføringen som ENDRING. Deretter produseres en Kafka-melding
     * med totrinnskontroll-ID slik at ekstern behandler kan godkjenne eller returnere endringen.
     *
     * @param gjennomforingId gjennomføring-ID som prisinformasjonen skal knyttes til.
     * @param prisinfo Ny prisinformasjon som skal sendes til behandling.
     * @param endretAvNavIdent Nav-ident for saksbehandleren som utfører endringen.
     */
    fun lagreOgProduserPrisinfoEndring(
        gjennomforingId: UUID,
        prisinfo: PrisinformasjonDto,
        endretAvNavIdent: String,
    ): UUID {
        val totrinnskontrollId = PrisinfoRepoAdapter.lagrePrisinfoEndring(
            gjennomforingId = gjennomforingId,
            prisinformasjon = prisinfo,
        )

        val endrePrisinfoPayload = GjennomforingRequestPayload.EnkeltplassEndrePrisinformasjon(
            gjennomforingId = gjennomforingId,
            totrinnskontroll = GjennomforingRequestPayload.Totrinnskontroll(
                id = totrinnskontrollId,
                behandletAv = endretAvNavIdent,
            ),
            payload = GjennomforingRequestPayload.Prisinformasjon.fromAmtPrisinfo(
                PrisinfoRepoAdapter.hentPrisinfo(
                    gjennomforingId = gjennomforingId,
                    rolle = PrisinfoDbo.Rolle.ENDRING,
                ) ?: throw IllegalStateException("Prisinfo mangler for gjennomføring $gjennomforingId"),
            ),
        )

        gjennomforingRequestProducer.produce(endrePrisinfoPayload)

        return totrinnskontrollId
    }

    /**
     * Lagrer endringer i innhold/kodeverk og publiserer oppdatert kategorisering til Mulighetsrommet.
     *
     * @param gjennomforingId Gjennomføring-ID som kategorisering skal knyttes til.
     * @param kodeverkValg Kodeverkverdier som skal lagres.
     * @param sertifiseringValg Sertifiseringer som skal lagres.
     * @param kategoriseringForTiltak Alle tilgjengelig kategoriseringsvalg for gjennomføringen.
     */
    fun lagreOgProduserEnkeltplassEndreInnhold(
        gjennomforingId: UUID,
        kodeverkValg: Set<UUID>?,
        sertifiseringValg: Set<SertifiseringValg>?,
        kategoriseringForTiltak: OpplaringKategoriseringResponse,
    ) {
        val opplaringKategoriseringValg = kategoriseringForTiltak.toOpplaringKategoriseringValg(
            kategoriseringValg = kodeverkValg ?: emptySet(),
            sertifiseringValg = sertifiseringValg ?: emptySet(),
        )

        OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
            gjennomforingId = gjennomforingId,
            valgteVerdier = kodeverkValg?.let { opplaringKategoriseringValg.valgteKategoriseringer },
            valgteSertifiseringer = sertifiseringValg?.let { opplaringKategoriseringValg.valgteSertifiseringer },
        )

        val endreInnholdPayload = GjennomforingRequestPayload.EnkeltplassEndreInnhold(
            gjennomforingId = gjennomforingId,
            payload = OpplaringKategoriseringRepoAdapter
                .hentOpplaringKategoriseringValg(gjennomforingId)
                .toMulighetsrommetKategorisering(),
        )

        gjennomforingRequestProducer.produce(endreInnholdPayload)
    }

    /**
     * Fjerner kopling mellom gjennomføring og prisinfo, og produserer melding til Mulighetsrommet
     * om tilbakekalling av prisendring.
     *
     * @param deltakerId Deltaker-ID
     * @param endretAvNavIdent Nav-ident for saksbehandleren som utfører endringen.
     */
    fun produserTilbakekallPrisendring(
        deltakerId: UUID,
        endretAvNavIdent: String,
    ) {
        val gjennomforingId = deltakerRepository
            .get(deltakerId)
            .getOrThrow()
            .deltakerliste.id

        Database.transaction {
            val prisinformasjonId = PrisinfoRepoAdapter.tilbakekallPrisinfoEndring(gjennomforingId)

            val tilbakekallPrisinfoPayload = GjennomforingRequestPayload.EnkeltplassTilbakekallPrisinformasjon(
                gjennomforingId = gjennomforingId,
                totrinnskontroll = GjennomforingRequestPayload.Totrinnskontroll(
                    id = prisinformasjonId,
                    behandletAv = endretAvNavIdent,
                ),
            )

            gjennomforingRequestProducer.produce(tilbakekallPrisinfoPayload)
        }
    }

    /**
     * Bygger og sender en gjennomførings-upsert til Mulighetsrommet.
     *
     * Payload-typen bestemmes av deltakerens nåværende status:
     * - [DeltakerStatus.Type.UTKAST_TIL_PAMELDING] → [GjennomforingRequestPayload.EnkeltplassUtkast]
     * - [DeltakerStatus.Type.SOKT_INN] → [GjennomforingRequestPayload.EnkeltplassSoktInn] med totrinnskontroll.
     *   Prisinfo-status oppdateres til [PrisinfoDbo.PrisinfoStatus.SENDT] etter vellykket publisering.
     *
     * @param deltaker Deltakeren som skal upsert-es.
     * @param endretAvNavIdent Nav-ident for saksbehandleren som utfører endringen.
     * @param endretAvEnhet Enhetsnummer for saksbehandlerens enhet.
     */
    fun produserGjennomforingUpsert(
        deltaker: Deltaker,
        endretAvNavIdent: String,
        endretAvEnhet: String,
    ) {
        val upsertPayload = buildUpsertEnkeltplassPayload(
            deltaker = deltaker,
            opprettetAvNavIdent = endretAvNavIdent,
            ansvarligEnhet = endretAvEnhet,
        )

        val gjennomforingRequestPayload = buildGjennomforingRequestPayload(
            deltaker = deltaker,
            upsertPayload = upsertPayload,
            behandletAv = endretAvNavIdent,
        )

        gjennomforingRequestProducer.produce(gjennomforingRequestPayload)

        if (gjennomforingRequestPayload is GjennomforingRequestPayload.EnkeltplassSoktInn) {
            PrisinfoRepository.oppdaterStatus(
                prisinformasjonId = gjennomforingRequestPayload.totrinnskontroll.id,
                status = PrisinfoDbo.PrisinfoStatus.SENDT,
            )
        }
    }

    /**
     * Bygger [GjennomforingRequestPayload.UpsertEnkeltplass] med prisinfo, kategorisering og ansvarlig enhet.
     *
     * Henter gjeldende ENDRING-prisinfo og opplæringskategorisering for gjennomføringen.
     *
     * @param deltaker Deltakeren tilknyttet gjennomføringen.
     * @param opprettetAvNavIdent Nav-ident for saksbehandleren som oppretter upserten.
     * @param ansvarligEnhet Enhetsnummer for ansvarlig enhet.
     * @return Ferdig bygget upsert-payload.
     */
    internal fun buildUpsertEnkeltplassPayload(
        deltaker: Deltaker,
        opprettetAvNavIdent: String,
        ansvarligEnhet: String,
    ) = UpsertEnkeltplass(
        tiltakskode = deltaker.deltakerliste.tiltakstype.tiltakskode,
        organisasjonsnummer = deltaker.deltakerliste.arrangor?.organisasjonsnummer
            ?: error("Organisasjonsnummer kan ikke være null"),
        ansvarligEnhet = ansvarligEnhet,
        opprettetAv = opprettetAvNavIdent,
        prisinformasjon = GjennomforingRequestPayload.Prisinformasjon.fromAmtPrisinfo(
            PrisinfoRepoAdapter.hentPrisinfo(
                gjennomforingId = deltaker.deltakerliste.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            ) ?: throw IllegalStateException("Prisinfo mangler for gjennomføring ${deltaker.deltakerliste.id}"),
        ),
        kategorisering = OpplaringKategoriseringRepoAdapter
            .hentOpplaringKategoriseringValg(deltaker.deltakerliste.id)
            .toMulighetsrommetKategorisering(),
    )

    /**
     * Bygger riktig [GjennomforingRequestPayload] basert på deltakerens status.
     *
     * - [DeltakerStatus.Type.UTKAST_TIL_PAMELDING] → [GjennomforingRequestPayload.EnkeltplassUtkast]
     * - [DeltakerStatus.Type.SOKT_INN] → [GjennomforingRequestPayload.EnkeltplassSoktInn] med totrinnskontroll
     *
     * @throws IllegalStateException hvis deltakerstatus ikke støttes, eller prisinfo mangler for SOKT_INN.
     */
    internal fun buildGjennomforingRequestPayload(
        deltaker: Deltaker,
        upsertPayload: UpsertEnkeltplass,
        behandletAv: String,
    ) = when (val statusType = deltaker.status.type) {
        DeltakerStatus.Type.UTKAST_TIL_PAMELDING -> GjennomforingRequestPayload.EnkeltplassUtkast(
            gjennomforingId = deltaker.deltakerliste.id,
            payload = upsertPayload,
        )

        DeltakerStatus.Type.SOKT_INN -> {
            val prisinfo = PrisinfoRepository.hentPrisinfo(
                gjennomforingId = deltaker.deltakerliste.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            ) ?: error("Fant ikke prisinformasjon for deltakerliste ${deltaker.deltakerliste.id}")

            GjennomforingRequestPayload.EnkeltplassSoktInn(
                gjennomforingId = deltaker.deltakerliste.id,
                payload = upsertPayload,
                totrinnskontroll = GjennomforingRequestPayload.Totrinnskontroll(
                    id = prisinfo.id,
                    behandletAv = behandletAv,
                ),
            )
        }

        else -> throw IllegalStateException("Deltaker ${deltaker.id} har status $statusType")
    }

    companion object {
        internal fun OpplaringKategoriseringValg.toMulighetsrommetKategorisering() = UpsertEnkeltplass.OpplaringKategorisering(
            sertifiseringer = valgteSertifiseringer,
            verdier = valgteKategoriseringer.associate { it.representerer to it.valg.keys },
        )
    }
}
