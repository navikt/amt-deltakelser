package no.nav.amt.lib.models.deltaker

fun InnsatsgruppeV2.toV1() = when (this) {
    InnsatsgruppeV2.GODE_MULIGHETER -> Innsatsgruppe.STANDARD_INNSATS
    InnsatsgruppeV2.TRENGER_VEILEDNING -> Innsatsgruppe.SITUASJONSBESTEMT_INNSATS
    InnsatsgruppeV2.TRENGER_VEILEDNING_NEDSATT_ARBEIDSEVNE -> Innsatsgruppe.SPESIELT_TILPASSET_INNSATS
    InnsatsgruppeV2.JOBBE_DELVIS -> Innsatsgruppe.GRADERT_VARIG_TILPASSET_INNSATS
    InnsatsgruppeV2.LITEN_MULIGHET_TIL_A_JOBBE -> Innsatsgruppe.VARIG_TILPASSET_INNSATS
}

fun Innsatsgruppe.toV2() = when (this) {
    Innsatsgruppe.STANDARD_INNSATS -> InnsatsgruppeV2.GODE_MULIGHETER
    Innsatsgruppe.SITUASJONSBESTEMT_INNSATS -> InnsatsgruppeV2.TRENGER_VEILEDNING
    Innsatsgruppe.SPESIELT_TILPASSET_INNSATS -> InnsatsgruppeV2.TRENGER_VEILEDNING_NEDSATT_ARBEIDSEVNE
    Innsatsgruppe.GRADERT_VARIG_TILPASSET_INNSATS -> InnsatsgruppeV2.JOBBE_DELVIS
    Innsatsgruppe.VARIG_TILPASSET_INNSATS -> InnsatsgruppeV2.LITEN_MULIGHET_TIL_A_JOBBE
}
