package no.nav.amt.lib.models.deltaker

/*
 * Angir hva en [Alternativ] representerer, for eksempel "Bransje", "Førerkortklasse" eller "Sertifisering".
 */
enum class OpplaringKategoriseringType {
    // Arbeidsmarkedsopplæring
    BRANSJE_ID,
    FORERKORT,
    SERTIFISERINGER,

    // Norskopplæring, grunnleggende ferdigheter og FOV
    KURSTYPE_ID, // FOV

    // Fag og yrkesopplæring
    UTDANNINGSPROGRAM_ID,
    LAREFAG,

    INNHOLDSELEMENTER,
    NORSKPROVE,
}
