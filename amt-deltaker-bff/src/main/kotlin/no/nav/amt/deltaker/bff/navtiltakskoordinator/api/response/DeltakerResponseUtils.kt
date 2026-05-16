package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.lib.models.deltaker.DeltakerStatus

object DeltakerResponseUtils {
    const val ADRESSEBESKYTTET_PLACEHOLDER_NAVN = "Adressebeskyttet"
    const val SKJERMET_PERSON_PLACEHOLDER_NAVN = "Skjermet person"

    /** Statuser som ikke skal vises i tiltakskoordinator-listen. */
    val SKJULTE_STATUSER = setOf(
        DeltakerStatus.Type.KLADD,
        DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
        DeltakerStatus.Type.AVBRUTT_UTKAST,
        DeltakerStatus.Type.FEILREGISTRERT,
        DeltakerStatus.Type.PABEGYNT_REGISTRERING,
    )
}
