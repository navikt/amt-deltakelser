package no.nav.amt.lib.models.journalforing.pdf

import com.fasterxml.jackson.annotation.JsonValue

enum class Forskriftskapittel(
    @JsonValue
    val verdi: String,
) {
    KAPITTEL_2("2"),
    KAPITTEL_4("4"),
    KAPITTEL_7("7"),
    KAPITTEL_12("12"),
    KAPITTEL_13("13"),
    KAPITTEL_14("14"),
    KAPITTEL_14A("14A"),
}
