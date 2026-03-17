package no.nav.amt.lib.models.arrangor.melding

import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Wrapper/decorator rundt [Forslag] som kan legge til ekstra informasjon basert på status.
 *
 * Brukes for å berike [Forslag] med informasjon som ikke finnes i domenemodellen,
 * for eksempel visningsnavn for NAV-ansatt når status er Avvist.
 *
 * @property forslag Den originale [Forslag]-objektet som dekoratoren pakker rundt.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface ForslagDecorator {
    val forslag: Forslag

    data class DefaultDecorator(
        override val forslag: Forslag,
    ) : ForslagDecorator

    data class AvvistStatusDecorator(
        override val forslag: Forslag,
        val avvistAvAnsattNavn: String,
        val avvistAvEnhetNavn: String,
    ) : ForslagDecorator
}
