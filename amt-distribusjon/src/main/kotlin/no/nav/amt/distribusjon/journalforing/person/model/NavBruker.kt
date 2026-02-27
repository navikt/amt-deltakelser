package no.nav.amt.distribusjon.journalforing.person.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class NavBruker(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val navEnhet: NavEnhet?,
    val oppfolgingsperioder: List<Oppfolgingsperiode>,
    val adressebeskyttelse: Adressebeskyttelse?,
    val adresse: Adresse?,
) {
    fun getAktivOppfolgingsperiode(): Oppfolgingsperiode? = oppfolgingsperioder.find { it.erAktiv() }

    fun harAdresse(): Boolean = adresse != null
}

data class Oppfolgingsperiode(
    val id: UUID,
    val startdato: LocalDateTime,
    val sluttdato: LocalDateTime?,
) {
    fun erAktiv(): Boolean {
        val now = LocalDate.now()
        val antallDagerGracePeriod = 28L
        return !(
            now.isBefore(
                startdato.toLocalDate(),
            ) ||
                (sluttdato != null && now.isAfter(sluttdato.toLocalDate().plusDays(antallDagerGracePeriod)))
        )
    }
}

enum class Adressebeskyttelse {
    STRENGT_FORTROLIG,
    FORTROLIG,
    STRENGT_FORTROLIG_UTLAND,
}
