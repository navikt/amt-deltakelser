package no.nav.amt.internapi.deltaker.response

import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import java.time.LocalDate

data class DeltakelsesmengderResponse(
    // Første gyldige mengde med gyldigFra etter både i dag og deltakerens startdato.
    // Mengden fra fremtidig oppstart vises fra toppnivåfeltene, ikke som neste periode.
    val nesteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
    // Siste gyldige mengde sortert på gyldigFra, også når den ligger frem i tid.
    // Kan ligge ved fremtidig oppstart selv når nesteDeltakelsesmengde er null.
    // Brukes bare til validering i endringsmodalen, ikke til visning av deltakelsesmengde.
    // Gjeldende mengde vises fra deltakerresponsens toppnivåfelter deltakelsesprosent og dagerPerUke.
    val sisteDeltakelsesmengde: DeltakelsesmengdeResponse,
)

/**
 * Oppstartsmengden vises fra toppnivåfeltene også før startdato, og skal ikke vises på nytt som neste periode.
 * Domenets [Deltakelsesmengder.neste] følger fortsatt dagens dato, uavhengig av denne visningsregelen.
 */
fun Deltakelsesmengder.nesteTilVisning(
    startdato: LocalDate?,
    iDag: LocalDate = LocalDate.now(),
): Deltakelsesmengde? {
    // Neste periode må starte etter både i dag og deltakerens startdato.
    val grensedatoForNestePeriode = if (startdato != null && startdato > iDag) {
        startdato
    } else {
        iDag
    }

    return firstOrNull { it.gyldigFra > grensedatoForNestePeriode }
}
