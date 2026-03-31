package no.nav.amt.internapi.enkeltplass

import java.time.LocalDate

class MeldPaaDirekteEnkeltplassRequest(
    val beskrivelse: String,
    val prisinformasjon: String,
    val startdato: LocalDate? = null,
    val sluttdato: LocalDate? = null,
)
