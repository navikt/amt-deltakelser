package no.nav.amt.deltaker.deltaker.endring.extensions

import no.nav.amt.deltaker.deltaker.endring.VellykketEndring
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring

fun DeltakerEndring.Endring.EndreSluttdato.hasChanges(deltaker: Deltaker): Boolean = this.sluttdato != deltaker.sluttdato

fun DeltakerEndring.Endring.EndreSluttdato.endreSluttdato(deltaker: Deltaker) = VellykketEndring(
    deltaker.copy(
        sluttdato = this.sluttdato,
        status = deltaker.getStatusEndretSluttdato(this.sluttdato),
    ),
)
