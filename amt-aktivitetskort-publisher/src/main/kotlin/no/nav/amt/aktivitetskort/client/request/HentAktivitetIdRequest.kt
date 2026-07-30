package no.nav.amt.aktivitetskort.client.request

data class HentAktivitetIdRequest(
    val arenaId: Long,
    val aktivitetKategori: String = "TILTAKSAKTIVITET",
)
