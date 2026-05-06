package no.nav.amt.deltaker.bff.utils

import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.model.Deltakeroppdatering
import no.nav.amt.internapi.deltaker.response.DeltakerEndringResponse
import no.nav.amt.internapi.paamelding.response.UtkastResponse
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringResponse

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

fun Deltaker.toDeltakerEndringResponse() = DeltakerEndringResponse(
    id,
    startdato,
    sluttdato,
    dagerPerUke,
    deltakelsesprosent,
    bakgrunnsinformasjon,
    deltakelsesinnhold,
    status,
    historikk,
)

fun Deltaker.toDeltakeroppdateringResponse() = DeltakerOppdateringResponse(
    id,
    startdato,
    sluttdato,
    dagerPerUke,
    deltakelsesprosent,
    bakgrunnsinformasjon,
    deltakelsesinnhold,
    status,
    historikk,
    erManueltDeltMedArrangor = erManueltDeltMedArrangor,
    feilkode = null,
)

fun Deltaker.toUtkastResponse() = UtkastResponse(
    id = id,
    startdato = startdato,
    sluttdato = sluttdato,
    dagerPerUke = dagerPerUke,
    deltakelsesprosent = deltakelsesprosent,
    bakgrunnsinformasjon = bakgrunnsinformasjon,
    deltakelsesinnhold = deltakelsesinnhold,
    status = status,
    historikk = historikk,
    erManueltDeltMedArrangor = erManueltDeltMedArrangor,
)
