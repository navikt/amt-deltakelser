package no.nav.amt.deltaker.deltaker.endring.extensions

import no.nav.amt.deltaker.deltaker.endring.VellykketEndring
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring

fun DeltakerEndring.Endring.ForlengDeltakelse.hasChanges(deltaker: Deltaker) = deltaker.sluttdato != this.sluttdato

fun DeltakerEndring.Endring.ForlengDeltakelse.forlengDeltakelse(deltaker: Deltaker) = VellykketEndring(
    deltaker.copy(
        sluttdato = this.sluttdato,
        status = deltaker.getStatusEndretSluttdato(this.sluttdato),
    ),
)
