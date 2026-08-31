package no.nav.amt.internapi.deltaker.request

import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype

fun EndringRequest.toEndring(): DeltakerEndring.Endring = EndringRequestMapper.toEndring(this)

fun EndringRequest.toEndring(tiltakstype: Tiltakstype): DeltakerEndring.Endring =
    EndringRequestMapper.toEndring(this, tiltakstype = tiltakstype)
