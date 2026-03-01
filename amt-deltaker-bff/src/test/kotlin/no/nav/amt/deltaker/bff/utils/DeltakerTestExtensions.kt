package no.nav.amt.deltaker.bff.utils

import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltaker.model.Deltakeroppdatering
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.DeltakerEndringResponse
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.response.UtkastResponse
import no.nav.amt.lib.models.deltaker.internalapis.tiltakskoordinator.response.DeltakerOppdateringResponse

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
