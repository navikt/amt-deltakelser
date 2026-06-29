package no.nav.amt.deltaker.enkeltplass

import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse.Alternativ
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import java.util.UUID

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
        representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
        valg = mapOf(valgtUtdanningsgruppe.id to valgtUtdanningsgruppe.visningsnavn),
    )

    val valgteLarefag = OpplaringKategoriseringValg.ValgteFelt(
        representerer = OpplaringKategoriseringType.LAREFAG,
        valg = valgtUtdanningsgruppe.larefag.alternativer
            .filter { verdi -> verdi.valgt }
            .associate { verdi -> verdi.id to verdi.visningsnavn },
    )

    return setOf(valgtUtdanningsprogram, valgteLarefag)
}
