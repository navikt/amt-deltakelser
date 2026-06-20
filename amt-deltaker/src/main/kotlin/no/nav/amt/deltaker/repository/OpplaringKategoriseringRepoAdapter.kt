package no.nav.amt.deltaker.repository

import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.repository.dbo.OpplaeringKategoriseringValgDbo
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringValg
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringValg.ValgteFelt
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

object OpplaringKategoriseringRepoAdapter {
    fun hentOpplaringKategoriseringValgForMulighetsrommet(
        gjennomforingId: UUID,
    ): GjennomforingRequestPayload.UpsertEnkeltplass.OpplaringKategorisering {
        val kategoriseringsValg = OpplaeringKategoriseringValgRepository.hentKategoriseringValg(gjennomforingId)

        return GjennomforingRequestPayload.UpsertEnkeltplass.OpplaringKategorisering(
            sertifiseringer = SertifiseringValgRepository.hentSertifiseringValg(gjennomforingId),
            verdier = kategoriseringsValg
                .groupBy { it.representerer }
                .mapValues { (_, valg) -> valg.map { it.kodeverkId }.toSet() },
        )
    }

    fun hentOpplaringKategoriseringValgForAmt(gjennomforingId: UUID): OpplaringKategoriseringValg {
        val kategoriseringsValg = OpplaeringKategoriseringValgRepository.hentKategoriseringValg(gjennomforingId)

        return OpplaringKategoriseringValg(
            valgteSertifiseringer = SertifiseringValgRepository.hentSertifiseringValg(gjennomforingId),
            valgteKategoriseringer = kategoriseringsValg
                .groupBy { it.representerer }
                .map { (representerer, valg) ->
                    ValgteFelt(
                        representerer = representerer,
                        valg = valg.associate { it.kodeverkId to it.tekst },
                    )
                }.toSet(),
        )
    }

    fun lagreOpplaringKategoriseringValg(
        gjennomforingId: UUID,
        valgteVerdier: Set<ValgteFelt>?,
        valgteSertifiseringer: Set<SertifiseringValg>?,
    ) {
        if (valgteVerdier != null) {
            // insert-only, sletter eksisterende valg før insert
            OpplaeringKategoriseringValgRepository.deleteForGjennomforing(gjennomforingId)

            if (valgteVerdier.isNotEmpty()) {
                val dboListe = valgteVerdier.flatMap { kategoriseringValg ->
                    kategoriseringValg.valg.map { enkeltvalg ->
                        OpplaeringKategoriseringValgDbo(
                            representerer = kategoriseringValg.representerer,
                            kodeverkId = enkeltvalg.key,
                            tekst = enkeltvalg.value,
                        )
                    }
                }

                OpplaeringKategoriseringValgRepository.insertKategoriseringValg(
                    gjennomforingId = gjennomforingId,
                    valg = dboListe,
                )
            }
        }

        if (valgteSertifiseringer != null) {
            // insert-only, sletter eksisterende valg før insert
            SertifiseringValgRepository.deleteForGjennomforing(gjennomforingId)

            if (valgteSertifiseringer.isNotEmpty()) {
                SertifiseringValgRepository.lagreSertifiseringValg(
                    gjennomforingId = gjennomforingId,
                    sertifiseringValg = valgteSertifiseringer,
                )
            }
        }
    }
}
