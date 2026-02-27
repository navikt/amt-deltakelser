package no.nav.amt.distribusjon.distribusjonskanal

enum class Distribusjonskanal {
    PRINT,
    SDP,
    DITT_NAV,
    LOKAL_PRINT,
    INGEN_DISTRIBUSJON,
    TRYGDERETTEN,
    DPVT,
}

fun Distribusjonskanal.skalDistribueresDigitalt() = when (this) {
    Distribusjonskanal.DITT_NAV,
    Distribusjonskanal.SDP,
    -> true

    else -> false
}
