package no.nav.amt.deltaker.repository

import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.repository.dbo.OpplaeringKategoriseringValgDbo
import no.nav.amt.internapi.enkeltplass.UtflatetKodeverk
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

    fun hentUtflatetKodeverk(gjennomforingId: UUID): UtflatetKodeverk {
        val kategoriseringsValg = OpplaeringKategoriseringValgRepository.hentKategoriseringValg(gjennomforingId)

        return UtflatetKodeverk(
            valgteSertifiseringer = SertifiseringValgRepository.hentSertifiseringValg(gjennomforingId),
            valgteKategoriseringer = kategoriseringsValg
                .groupBy { it.representerer }
                .map { (representerer, valg) ->
                    UtflatetKodeverk.ValgteFelt(
                        representerer = representerer,
                        valg = valg.associate { it.kodeverkId to it.tekst },
                    )
                }.toSet(),
        )
    }

    fun lagreKategoriserimg(
        gjennomforingId: UUID,
        harKategoriseringer: Boolean,
        harSertifiseringer: Boolean,
        utflatetKodeverk: UtflatetKodeverk,
    ) {
        if (!harKategoriseringer && !harSertifiseringer) return

        if (harKategoriseringer) {
            // insert-only, sletter eksisterende valg før insert
            OpplaeringKategoriseringValgRepository.deleteForGjennomforing(gjennomforingId)

            if (utflatetKodeverk.valgteKategoriseringer.isNotEmpty()) {
                val kategoriseringValg = utflatetKodeverk.valgteKategoriseringer.flatMap { kategoriseringValg ->
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
        if (harSertifiseringer) {
            SertifiseringValgRepository.deleteForGjennomforing(gjennomforingId)

            if (utflatetKodeverk.valgteSertifiseringer.isNotEmpty()) {
                SertifiseringValgRepository.lagreSertifiseringValg(
                    deltakerlisteId = gjennomforingId,
                    sertifiseringValg = utflatetKodeverk.valgteSertifiseringer,
                )
            }
        }
    }
}
