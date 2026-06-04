package no.nav.amt.deltaker.bff.utils

import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.model.Deltakeroppdatering

fun Deltaker.toDeltakeroppdatering() = Deltakeroppdatering(
    id,
    startdato,
    sluttdato,
    dagerPerUke,
    deltakelsesprosent,
    bakgrunnsinformasjon,
    deltakelsesinnhold,
    status,
    historikk,
    sistEndret,
    erManueltDeltMedArrangor,
)
