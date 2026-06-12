package no.nav.amt.lib.models.deltakerliste

enum class GjennomforingStatusType(
    val beskrivelse: String,
) {
    GJENNOMFORES("Gjennomføres"),
    AVSLUTTET("Avsluttet"),
    AVBRUTT("Avbrutt"),
    AVLYST("Avlyst"),

    /*
     * Denne statusen brukes for å markere at en gjennomføring er i en "kladd"-tilstand,
     * som betyr at valp ikke kjenner til gjennomføringen
     */
    KLADD("Kladd"),
}
