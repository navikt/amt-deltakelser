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
 * @param kategoriseringValg settet med valgte IDer
 * @param sertifiseringValg settet med valgte sertifiseringer
 * @return OpplaringKategoriseringValg med alle valgte kategoriseringer og sertifiseringer
 */
fun OpplaringKategoriseringResponse.toOpplaringKategoriseringValg(
    kategoriseringValg: Set<UUID>,
    sertifiseringValg: Set<SertifiseringValg>,
): OpplaringKategoriseringValg {
    validerAtKategoriseringIderFinnes(kategoriseringValg)
    validerEnkeltvalg(kategoriseringValg)

    val responsMedValg = settValg(
        verdivalg = kategoriseringValg,
        sertifiseringValg = sertifiseringValg,
    )
    val resultat = OpplaringKategoriseringValg(
        valgteKategoriseringer = responsMedValg.alternativer
            .flatMap { it.tilValgteFelt() }
            .toSet(),
        valgteSertifiseringer = sertifiseringValg,
    )

    validerAtAlleValgteKategoriseringerBleBrukt(
        valgteKategoriseringIder = kategoriseringValg,
        opplaringKategoriseringValg = resultat,
    )

    return resultat
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
        } ?: return emptySet()

    val valgtUtdanningsprogram = OpplaringKategoriseringValg.ValgteFelt(
        representerer = OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID,
        valg = mapOf(valgtUtdanningsgruppe.id to valgtUtdanningsgruppe.visningsnavn),
    )

    val valgteLarefag = valgtUtdanningsgruppe.larefag.alternativer
        .filter { it.valgt }
        .associate { it.id to it.visningsnavn }

    return buildSet {
        add(valgtUtdanningsprogram)

        if (valgteLarefag.isNotEmpty()) {
            add(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.LAREFAG,
                    valg = valgteLarefag,
                ),
            )
        }
    }
}

private fun OpplaringKategoriseringResponse.validerAtKategoriseringIderFinnes(kategoriseringValg: Set<UUID>) {
    val ugyldigeKategoriseringIder = kategoriseringValg - gyldigeKategoriseringIder()
    require(ugyldigeKategoriseringIder.isEmpty()) {
        "Ugyldig kategoriseringsvalg. Følgende ID-er finnes ikke for tiltaket: $ugyldigeKategoriseringIder"
    }
}

private fun OpplaringKategoriseringResponse.gyldigeKategoriseringIder(): Set<UUID> = alternativer
    .flatMap { alternativ ->
        when (alternativ) {
            is Alternativ.Verdigruppe -> alternativ.alternativer.map { it.id }
            is Alternativ.VerdigruppeSok -> emptyList()
            is Alternativ.UtdanningGruppe -> alternativ.utdanninger.flatMap { utdanning ->
                listOf(utdanning.id) + utdanning.larefag.alternativer.map { it.id }
            }
        }
    }.toSet()

private fun validerAtAlleValgteKategoriseringerBleBrukt(
    valgteKategoriseringIder: Set<UUID>,
    opplaringKategoriseringValg: OpplaringKategoriseringValg,
) {
    val ikkeValgteKategoriseringer = valgteKategoriseringIder - opplaringKategoriseringValg.valgteKategoriseringIder()
    require(ikkeValgteKategoriseringer.isEmpty()) {
        "Ugyldig kategoriseringsvalg. Noen valgte ID-er kunne ikke brukes: $ikkeValgteKategoriseringer"
    }
}

private fun OpplaringKategoriseringResponse.validerEnkeltvalg(kategoriseringValg: Set<UUID>) {
    alternativer
        .filterIsInstance<Alternativ.Verdigruppe>()
        .filter { it.seleksjonstype == OpplaringKategoriseringResponse.Seleksjonstype.ENKELTVALG }
        .forEach { verdigruppe ->
            val valgteIGruppe = verdigruppe.alternativer.count { it.id in kategoriseringValg }
            require(valgteIGruppe <= 1) {
                "Ugyldig kategoriseringsvalg. Kun ett valg er tillatt for ${verdigruppe.representerer}"
            }
        }
}

private fun OpplaringKategoriseringValg.valgteKategoriseringIder(): Set<UUID> = valgteKategoriseringer
    .flatMap { it.valg.keys }
    .toSet()
