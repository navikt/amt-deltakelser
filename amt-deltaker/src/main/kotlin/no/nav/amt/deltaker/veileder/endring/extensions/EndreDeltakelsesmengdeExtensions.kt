package no.nav.amt.deltaker.veileder.endring.extensions

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.veileder.endring.VellykketEndring
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengde
import java.time.LocalDate
import java.time.LocalDateTime

fun DeltakerEndring.Endring.EndreDeltakelsesmengde.hasChanges(deltakelsemengder: Deltakelsesmengder): Boolean {
    val nyDeltakelsesmengde = this.toDeltakelsesmengde(LocalDateTime.now())
    return deltakelsemengder.validerNyDeltakelsesmengde(nyDeltakelsesmengde)
}

fun DeltakerEndring.Endring.EndreDeltakelsesmengde.validerGyldigFra(deltaker: Deltaker) {
    val nyDeltakelsesmengde = this.toDeltakelsesmengde(LocalDateTime.now())

    val startdato = deltaker.startdato
    require(startdato == null || nyDeltakelsesmengde.gyldigFra >= startdato) {
        "gyldigFra (${nyDeltakelsesmengde.gyldigFra}) kan ikke være før startdato ($startdato)"
    }

    val sluttdato = deltaker.sluttdato
    require(sluttdato == null || nyDeltakelsesmengde.gyldigFra <= sluttdato) {
        "gyldigFra (${nyDeltakelsesmengde.gyldigFra}) kan ikke være etter sluttdato ($sluttdato)"
    }
}

fun DeltakerEndring.Endring.EndreDeltakelsesmengde.endreDeltakelsesmengde(deltaker: Deltaker): VellykketEndring {
    val nyDeltakelsesmengde = this.toDeltakelsesmengde(LocalDateTime.now())

    // Defence-in-depth: også validert i DeltakerService før runCatching for å gi 400 Bad Request
    validerGyldigFra(deltaker)

    return if (nyDeltakelsesmengde.gyldigFra <= LocalDate.now()) {
        VellykketEndring(
            deltaker.copy(
                deltakelsesprosent = this.deltakelsesprosent,
                dagerPerUke = this.dagerPerUke,
            ),
        )
    } else {
        VellykketEndring(deltaker = deltaker, erFremtidigEndring = true)
    }
}
