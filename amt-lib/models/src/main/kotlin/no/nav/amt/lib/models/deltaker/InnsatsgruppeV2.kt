package no.nav.amt.lib.models.deltaker

/**
 * Dette er innsatsgruppene som vi får fra OBO, og fra Valp (på tiltakstyper) i dag, men vi har ennå ikke migrert over til å bruke disse,
 * så man må huske å konvertere enumene til første versjon `Innsatsgruppe` foreløpig.
 */
enum class InnsatsgruppeV2 {
    GODE_MULIGHETER,
    TRENGER_VEILEDNING,
    TRENGER_VEILEDNING_NEDSATT_ARBEIDSEVNE,
    JOBBE_DELVIS,
    LITEN_MULIGHET_TIL_A_JOBBE,
}
