package no.nav.amt.deltaker.deltaker.endring.extensions

import no.nav.amt.deltaker.deltaker.endring.VellykketEndring
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengde
import java.time.LocalDate
import java.time.LocalDateTime

fun DeltakerEndring.Endring.EndreDeltakelsesmengde.hasChanges(deltakelsemengder: Deltakelsesmengder): Boolean {
    val nyDeltakelsesmengde = this.toDeltakelsesmengde(LocalDateTime.now())
    return deltakelsemengder.validerNyDeltakelsesmengde(nyDeltakelsesmengde)
}

fun DeltakerEndring.Endring.EndreDeltakelsesmengde.endreDeltakelsesmengde(deltaker: Deltaker): VellykketEndring {
    val nyDeltakelsesmengde = this.toDeltakelsesmengde(LocalDateTime.now())

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
