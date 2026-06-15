package no.nav.amt.lib.models.deltakerliste

data class Priskomponent(
    val pristype: Pristype,
    val pris: UInt,
) {
    enum class Pristype {
        ANSKAFFELSE,
        SKOLEPENGER,
        STUDIEREISE,
        EKSAMENSGEBYR,
        SEMESTERAVGIFT,
        INTEGRERT_BOTILBUD,
    }
}
