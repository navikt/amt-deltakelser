package no.nav.amt.deltaker.repository

import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.repository.dbo.OpplaeringKategoriseringValgDbo
import no.nav.amt.internapi.enkeltplass.ValgteKategoriseringerOgSertifiseringer
import no.nav.amt.internapi.enkeltplass.ValgteKategoriseringerOgSertifiseringer.ValgteFelt
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

object OpplaeringKategoriseringRepoAdapter {
    fun hentKategoriseringValgForMulighetsrommet(
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

    fun hentValgteKategoriseringerOgSertifiseringer(gjennomforingId: UUID): ValgteKategoriseringerOgSertifiseringer {
        val kategoriseringsValg = OpplaeringKategoriseringValgRepository.hentKategoriseringValg(gjennomforingId)

        return ValgteKategoriseringerOgSertifiseringer(
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

    fun lagreKategoriserimg(
        gjennomforingId: UUID,
        valgteKategoriseringer: Set<ValgteFelt>?,
        valgteSertifiseringer: Set<SertifiseringValg>?,
    ) {
        if (valgteKategoriseringer != null) {
            // insert-only, sletter eksisterende valg før insert
            OpplaeringKategoriseringValgRepository.deleteForGjennomforing(gjennomforingId)

            if (valgteKategoriseringer.isNotEmpty()) {
                val kategoriseringValg = valgteKategoriseringer.flatMap { kategoriseringValg ->
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
                    valg = kategoriseringValg,
                )
            }
        }

        // insert-only, sletter eksisterende valg før insert
        if (valgteSertifiseringer != null) {
            SertifiseringValgRepository.deleteForGjennomforing(gjennomforingId)

            if (valgteSertifiseringer.isNotEmpty()) {
                SertifiseringValgRepository.lagreSertifiseringValg(
                    deltakerlisteId = gjennomforingId,
                    sertifiseringValg = valgteSertifiseringer,
                )
            }
        }
    }
}
