package no.nav.amt.lib.models.deltaker

/*
 * Angir hva en [Alternativ] representerer, for eksempel "Bransje", "Førerkortklasse" eller "Sertifisering".
 *
 * Kopiert fra no.nav.mulighetsrommet.admin.opplaring.OpplaringKategoriseringResponse hos Mulighetsrommet.
 */
enum class OpplaringKategoriseringType {
    // Arbeidsmarkedsopplæring
    BRANSJE_ID,
    FORERKORT,
    SERTIFISERINGER,

    // Norskopplæring, grunnleggende ferdigheter og FOV
    KURSTYPE_ID,

    // Fag og yrkesopplæring
    UTDANNINGSPROGRAM_ID,
    LAREFAG,

    // Ikke i bruk hos Komet men er tatt med for kompabilitet med Valp
    INNHOLDSELEMENTER,
    NORSKPROVE,
}
