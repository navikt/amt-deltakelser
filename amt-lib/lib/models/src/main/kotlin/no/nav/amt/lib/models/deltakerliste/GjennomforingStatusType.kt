package no.nav.amt.lib.models.deltakerliste

enum class GjennomforingStatusType(
    val beskrivelse: String,
) {
    GJENNOMFORES("Gjennomføres"),
    AVSLUTTET("Avsluttet"),
    AVBRUTT("Avbrutt"),
    AVLYST("Avlyst"),
}
