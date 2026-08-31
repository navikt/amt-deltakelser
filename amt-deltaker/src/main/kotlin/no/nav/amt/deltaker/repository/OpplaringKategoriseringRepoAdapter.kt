package no.nav.amt.deltaker.repository

import no.nav.amt.deltaker.repository.dbo.OpplaeringKategoriseringValgDbo
import no.nav.amt.internapi.deltaker.request.EndretOpplaringKategoriseringRequest
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg.ValgteFelt
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

object OpplaringKategoriseringRepoAdapter {
    fun hentOpplaringKategoriseringValg(gjennomforingId: UUID): OpplaringKategoriseringValg {
        val kategoriseringValg = OpplaeringKategoriseringValgRepository.hentKategoriseringValg(gjennomforingId)
        val sertifiseringValg = SertifiseringValgRepository.hentSertifiseringValg(gjennomforingId)

        return OpplaringKategoriseringValg(
            valgteSertifiseringer = sertifiseringValg,
            valgteKategoriseringer = kategoriseringValg
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

    fun erUendretValg(
        gjennomforingId: UUID,
        endringRequest: EndretOpplaringKategoriseringRequest,
    ): Boolean {
        val eksisterendeValg = hentOpplaringKategoriseringValg(gjennomforingId)
        val eksisterendeKategoriseringIder = eksisterendeValg.valgteKategoriseringer
            .flatMap { it.valg.keys }
            .toSet()

        val kodeverkErUendret = eksisterendeKategoriseringIder == endringRequest.kodeverkValg()
        val sertifiseringerErUendret = eksisterendeValg.valgteSertifiseringer == endringRequest.sertifiseringValg

        return kodeverkErUendret && sertifiseringerErUendret
    }
}
