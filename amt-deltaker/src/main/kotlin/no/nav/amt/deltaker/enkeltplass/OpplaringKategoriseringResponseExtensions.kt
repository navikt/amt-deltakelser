package no.nav.amt.deltaker.enkeltplass

import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse.Alternativ
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse.Representerer
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

/**
 * Filtrerer ut IDene til verdier som er valgt basert på gitt sett med valgte IDer.
 *
 * @param verdivalg settet med valgte IDer
 * @return sett med IDene til verdier som er funnet i verdivalg
 */
private fun List<Alternativ.Verdi>.filterValgteIds(verdivalg: Set<UUID>): Set<UUID> = this
    .filter { alt -> alt.id in verdivalg }
    .map { alt -> alt.id }
    .toSet()

/**
 * Grupperer og filtrerer valgte ID-er etter hvilken representør de tilhører.
 *
 * Håndterer tre typer alternativer:
 * - Verdigrupper: mapper valgte verdier direkte
 * - Utdanningsgrupper: mapper valgt utdanningsprogram og dets lærefag
 * - Verdisøk: ignoreres
 *
 * @param verdivalg settet med valgte IDer
 * @return mapping fra Representerer til settet av valgte IDer for den representøren
 */
fun OpplaringKategoriseringResponse.grupperValgteIderPerRepresenterer(verdivalg: Set<UUID>): Map<Representerer, Set<UUID>> = buildMap {
    alternativer.forEach { alternativ ->
        when (alternativ) {
            is Alternativ.VerdigruppeSok -> Unit

            is Alternativ.Verdigruppe -> {
                val valgte = alternativ.alternativer.filterValgteIds(verdivalg)
                if (valgte.isNotEmpty()) {
                    put(alternativ.representerer, valgte)
                }
            }

            is Alternativ.UtdanningGruppe -> {
                val utdanningsprogram = alternativ.utdanninger
                    .firstOrNull { it.id in verdivalg }
                    ?: return@forEach

                put(Representerer.UTDANNINGSPROGRAM_ID, setOf(utdanningsprogram.id))

                val valgteLarefag = utdanningsprogram.larefag.alternativer.filterValgteIds(verdivalg)
                if (valgteLarefag.isNotEmpty()) {
                    put(Representerer.LAREFAG, valgteLarefag)
                }
            }
        }
    }
}

/**
 * Konverterer valgte verdier og sertifiseringer til Gjennomforing-payload format.
 * Benyttes ved publisering av gjennomføring til Mulighetsrommet.
 *
 * Grupperer valgte verdier per representør og legger med sertifiseringer.
 *
 * @param verdivalg settet med valgte IDer, kan være null
 * @param sertifiseringValg settet med valgte sertifiseringer, kan være null
 * @return OpplaringKategorisering-objekt
 */
fun OpplaringKategoriseringResponse.toOpplaringKategorisering(
    verdivalg: Set<UUID>?,
    sertifiseringValg: Set<SertifiseringValg>?,
): GjennomforingRequestPayload.UpsertEnkeltplass.OpplaringKategorisering =
    GjennomforingRequestPayload.UpsertEnkeltplass.OpplaringKategorisering(
        verdier = verdivalg
            ?.let { grupperValgteIderPerRepresenterer(it) }
            ?: emptyMap(),
        sertifiseringer = sertifiseringValg ?: emptySet(),
    )

/**
 * Konverterer valgte verdi- og sertifiseringsvalg til strukturert objektformat.
 * Benyttes ved lagring av kategoriseringsvalg i databasen.
 *
 * Mapper hierarkiet av valgte alternativer (verdigrupper, utdanningsprogrammer, lærefag)
 * til et flatt sett av ValgteFelt-objekter som representerer brukerens valg.
 *
 * Early-return med bare sertifiseringer hvis ingen verdier er valgt.
 *
 * @param verdivalg settet med valgte IDer
 * @param sertifiseringValg settet med valgte sertifiseringer
 * @return OpplaringKategoriseringValg med alle valgte kategoriseringer og sertifiseringer
 */
fun OpplaringKategoriseringResponse.toOpplaringKategoriseringValg(
    verdivalg: Set<UUID>,
    sertifiseringValg: Set<SertifiseringValg>,
): OpplaringKategoriseringValg = if (verdivalg.isEmpty()) {
    OpplaringKategoriseringValg(
        valgteKategoriseringer = emptySet(),
        valgteSertifiseringer = sertifiseringValg,
    )
} else {
    val kategoriseringResponseMedValgteElementer = settValg(
        verdivalg = verdivalg,
        sertifiseringValg = sertifiseringValg,
    )

    OpplaringKategoriseringValg(
        valgteKategoriseringer = kategoriseringResponseMedValgteElementer.alternativer
            .flatMap { it.tilValgteFelt() }
            .toSet(),
        valgteSertifiseringer = sertifiseringValg,
    )
}

/**
 * Konverterer alternative-container til et sett av ValgteFelt dersom det inneholder valgte elementer.
 *
 * Delegerer til type-spesifikke implementasjoner basert på alternativtype.
 *
 * @return sett av ValgteFelt for valgte elementer, tomt hvis ingen er valgt
 */
private fun Alternativ.Container.tilValgteFelt(): Set<OpplaringKategoriseringValg.ValgteFelt> = when (this) {
    is Alternativ.UtdanningGruppe -> tilValgteFeltInternal()
    is Alternativ.Verdigruppe -> tilValgteFeltInternal()
    is Alternativ.VerdigruppeSok -> emptySet()
}

/**
 * Konverterer valgte verdier i en Verdigruppe til et sett av ValgteFelt.
 *
 * Filtrerer ut kun de verdiene som er merket som valgt, og bygger opp
 * en mapping fra ID til visningsnavn for disse.
 *
 * @return sett med ett ValgteFelt-objekt hvis noen verdier er valgt, tomt sett ellers
 */
private fun Alternativ.Verdigruppe.tilValgteFeltInternal(): Set<OpplaringKategoriseringValg.ValgteFelt> {
    if (alternativer.none { it.valgt }) return emptySet()

    return setOf(
        OpplaringKategoriseringValg.ValgteFelt(
            representerer = representerer,
            valg = alternativer
                .filter { verdi -> verdi.valgt }
                .associate { verdi -> verdi.id to verdi.visningsnavn },
        ),
    )
}

/**
 * Konverterer valgt utdanningsprogram og valgte lærefag til et sett av ValgteFelt.
 *
 * Mapper både det valgte utdanningsprogrammet og de valgte lærefagene det inneholder
 * til to separate ValgteFelt-objekter som er representert av deres respektive representørtyper.
 *
 * Returnerer tomt sett hvis ingen utdanningsgruppe har valgte elementer.
 *
 * @return sett med ValgteFelt-objekter - ett for utdanningsprogram og ett for lærefag,
 *         returnerer tomt sett hvis ingenting er valgt
 */
private fun Alternativ.UtdanningGruppe.tilValgteFeltInternal(): Set<OpplaringKategoriseringValg.ValgteFelt> {
    val valgtUtdanningsgruppe = utdanninger
        .firstOrNull { utdanningValg ->
            utdanningValg.valgt || utdanningValg.larefag.alternativer.any { verdi -> verdi.valgt }
        }
        ?: return emptySet()

    val valgtUtdanningsprogram = OpplaringKategoriseringValg.ValgteFelt(
        representerer = Representerer.UTDANNINGSPROGRAM_ID,
        valg = mapOf(valgtUtdanningsgruppe.id to valgtUtdanningsgruppe.visningsnavn),
    )

    val valgteLarefag = OpplaringKategoriseringValg.ValgteFelt(
        representerer = Representerer.LAREFAG,
        valg = valgtUtdanningsgruppe.larefag.alternativer
            .filter { verdi -> verdi.valgt }
            .associate { verdi -> verdi.id to verdi.visningsnavn },
    )

    return setOf(valgtUtdanningsprogram, valgteLarefag)
}
