package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.lib.models.deltaker.DeltakerStatus

object DeltakerResponseUtils {
    const val ADRESSEBESKYTTET_PLACEHOLDER_NAVN = "Adressebeskyttet"
    const val SKJERMET_PERSON_PLACEHOLDER_NAVN = "Skjermet person"

    fun DeltakerModel.skalSkjules() = status.type in listOf(
        DeltakerStatus.Type.KLADD,
        DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
        DeltakerStatus.Type.AVBRUTT_UTKAST,
        DeltakerStatus.Type.FEILREGISTRERT,
        DeltakerStatus.Type.PABEGYNT_REGISTRERING,
    )
}
