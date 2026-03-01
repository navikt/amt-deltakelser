package no.nav.amt.deltaker.deltaker.endring.extensions

import no.nav.amt.deltaker.deltaker.endring.VellykketEndring
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder

fun DeltakerEndring.Endring.EndreStartdato.hasChanges(deltaker: Deltaker): Boolean =
    deltaker.startdato != this.startdato || deltaker.sluttdato != this.sluttdato

fun DeltakerEndring.Endring.EndreStartdato.endreStartdato(deltaker: Deltaker, deltakelsesmengder: Deltakelsesmengder) = VellykketEndring(
    deltaker.endreDeltakersOppstart(
        startdato = this.startdato,
        sluttdato = this.sluttdato,
        deltakelsesmengder = deltakelsesmengder,
    ),
)
