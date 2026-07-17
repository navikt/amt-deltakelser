package no.nav.amt.deltaker.enkeltplass

import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestProducer
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepository
import no.nav.amt.deltaker.service.VedtakService
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg

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

        produceUpsertGjennomforing(
            deltaker = deltaker,
            endretAvNavIdent = ansvarligNavAnsatt.navIdent,
            endretAvEnhet = ansvarligEnhet.enhetsnummer,
        )
    }

    fun produceUpsertGjennomforing(
        deltaker: Deltaker,
        endretAvNavIdent: String,
        endretAvEnhet: String,
    ) {
        val upsertPayload = GjennomforingRequestPayload.UpsertEnkeltplass(
            tiltakskode = deltaker.deltakerliste.tiltakstype.tiltakskode,
            prisinformasjon = GjennomforingRequestPayload.Prisinformasjon.fromAmtPrisinfo(
                PrisinfoRepoAdapter.hentPrisinfo(deltaker.deltakerliste.id)
                    ?: throw IllegalStateException("Prisinfo mangler for gjennomføring ${deltaker.deltakerliste.id}"),
            ),
            organisasjonsnummer = deltaker.deltakerliste.arrangor?.organisasjonsnummer
                ?: error("Organisasjonsnummer kan ikke være null"),
            ansvarligEnhet = endretAvEnhet,
            opprettetAv = endretAvNavIdent,
            kategorisering = deltaker.deltakerliste.opplaringKategorisering?.toMulighetsrommetKategorisering(),
        )

        val gjennomforingPayload = when (val statusType = deltaker.status.type) {
            DeltakerStatus.Type.UTKAST_TIL_PAMELDING -> GjennomforingRequestPayload.EnkeltplassUtkast(
                gjennomforingId = deltaker.deltakerliste.id,
                payload = upsertPayload,
            )

            DeltakerStatus.Type.SOKT_INN -> {
                val prisinfo = PrisinfoRepository.hentPrisinfo(
                    gjennomforingId = deltaker.deltakerliste.id,
                    okonomiGodkjent = false,
                ) ?: error("Fant ikke prisinformasjon for deltakerliste ${deltaker.deltakerliste.id}")

                GjennomforingRequestPayload.EnkeltplassSoktInn(
                    gjennomforingId = deltaker.deltakerliste.id,
                    payload = upsertPayload,
                    totrinnskontroll = GjennomforingRequestPayload.Totrinnskontroll(
                        id = prisinfo.id,
                        behandletAv = endretAvNavIdent,
                    ),
                )
            }

            else -> throw IllegalStateException("Deltaker ${deltaker.id} har status $statusType")
        }

        gjennomforingRequestProducer.produce(gjennomforingPayload)
    }

    companion object {
        internal fun OpplaringKategoriseringValg.toMulighetsrommetKategorisering() =
            GjennomforingRequestPayload.UpsertEnkeltplass.OpplaringKategorisering(
                sertifiseringer = valgteSertifiseringer,
                verdier = valgteKategoriseringer.associate { it.representerer to it.valg.keys },
            )
    }
}
