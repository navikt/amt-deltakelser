package no.nav.amt.internapi.deltaker.response

import no.nav.amt.lib.models.deltaker.DeltakerEndring

data class DeltakerEndringAarsakResponse(
    val type: Type,
    val beskrivelse: String? = null,
) {
    constructor(model: DeltakerEndring.Aarsak) : this(
        type = Type.valueOf(model.type.name),
        beskrivelse = model.beskrivelse,
    )

    enum class Type {
        SYK,
        FATT_JOBB,
        TRENGER_ANNEN_STOTTE,
        UTDANNING,
        IKKE_MOTT,
        ANNET,
    }
}
