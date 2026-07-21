package no.nav.amt.deltaker.enkeltplass

import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestProducer
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.repository.OpplaringKategoriseringRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepository
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto

class GjennomforingUpserter(
    private val navEnhetRepository: NavEnhetRepository,
    private val navAnsattRepository: NavAnsattRepository,
    private val vedtakService: VedtakService,
    private val gjennomforingRequestProducer: GjennomforingRequestProducer,
) {
    // benyttes av #innbyggerGodkjennUtkast
    fun publiserGjennomforing(deltaker: Deltaker) {
        val vedtak = vedtakService.hentIkkeFattetVedtakOrThrow(deltaker.id)
        val ansvarligEnhet = navEnhetRepository.getOrThrow(vedtak.opprettetAvEnhet)
        val ansvarligNavAnsatt = navAnsattRepository.getOrThrow(vedtak.opprettetAv)

        publiserGjennomforingUpsert(
            deltaker = deltaker,
            endretAvNavIdent = ansvarligNavAnsatt.navIdent,
            endretAvEnhet = ansvarligEnhet.enhetsnummer,
        )
    }

    fun produceEndrePrisinfo(
        prisinfo: PrisinformasjonDto,
        deltaker: Deltaker,
        endretAvNavIdent: String,
    ) {
        val totrinnskontrollId = PrisinfoRepoAdapter.lagrePrisinfoEndring(
            gjennomforingId = deltaker.deltakerliste.id,
            prisinformasjon = prisinfo,
        )

        val endrePrisinfoPayload = GjennomforingRequestPayload.EnkeltplassEndrePrisinformasjon(
            gjennomforingId = deltaker.deltakerliste.id,
            totrinnskontroll = GjennomforingRequestPayload.Totrinnskontroll(
                id = totrinnskontrollId,
                behandletAv = endretAvNavIdent,
            ),
            payload = GjennomforingRequestPayload.Prisinformasjon.fromAmtPrisinfo(
                PrisinfoRepoAdapter.hentPrisinfo(
                    gjennomforingId = deltaker.deltakerliste.id,
                    brukEndring = true,
                ) ?: throw IllegalStateException("Prisinfo mangler for gjennomføring ${deltaker.deltakerliste.id}"),
            ),
        )

        gjennomforingRequestProducer.produce(endrePrisinfoPayload)
    }

    fun publiserGjennomforingUpsert(
        deltaker: Deltaker,
        endretAvNavIdent: String,
        endretAvEnhet: String,
    ) {
        val upsertPayload = buildUpsertPayload(
            deltaker = deltaker,
            opprettetAvNavIdent = endretAvNavIdent,
            ansvarligEnhet = endretAvEnhet,
        )

        val gjennomforingRequestPayload = buildGjennomforingRequest(
            deltaker = deltaker,
            upsertPayload = upsertPayload,
            behandletAv = endretAvNavIdent,
        )

        gjennomforingRequestProducer.produce(gjennomforingRequestPayload)
    }

    internal fun buildUpsertPayload(
        deltaker: Deltaker,
        opprettetAvNavIdent: String,
        ansvarligEnhet: String,
    ) = GjennomforingRequestPayload.UpsertEnkeltplass(
        tiltakskode = deltaker.deltakerliste.tiltakstype.tiltakskode,
        prisinformasjon = GjennomforingRequestPayload.Prisinformasjon.fromAmtPrisinfo(
            PrisinfoRepoAdapter.hentPrisinfo(
                gjennomforingId = deltaker.deltakerliste.id,
                brukEndring = true,
            ) ?: throw IllegalStateException("Prisinfo mangler for gjennomføring ${deltaker.deltakerliste.id}"),
        ),
        organisasjonsnummer = deltaker.deltakerliste.arrangor?.organisasjonsnummer
            ?: error("Organisasjonsnummer kan ikke være null"),
        ansvarligEnhet = ansvarligEnhet,
        opprettetAv = opprettetAvNavIdent,
        kategorisering = OpplaringKategoriseringRepoAdapter
            .hentOpplaringKategoriseringValg(deltaker.deltakerliste.id)
            .toMulighetsrommetKategorisering(),
    )

    internal fun buildGjennomforingRequest(
        deltaker: Deltaker,
        upsertPayload: GjennomforingRequestPayload.UpsertEnkeltplass,
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
        internal fun OpplaringKategoriseringValg.toMulighetsrommetKategorisering() =
            GjennomforingRequestPayload.UpsertEnkeltplass.OpplaringKategorisering(
                sertifiseringer = valgteSertifiseringer,
                verdier = valgteKategoriseringer.associate { it.representerer to it.valg.keys },
            )
    }
}
