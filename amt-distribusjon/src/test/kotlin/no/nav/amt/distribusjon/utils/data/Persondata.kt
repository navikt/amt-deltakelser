package no.nav.amt.distribusjon.utils.data

import no.nav.amt.distribusjon.journalforing.person.model.Adresse
import no.nav.amt.distribusjon.journalforing.person.model.Adressebeskyttelse
import no.nav.amt.distribusjon.journalforing.person.model.Bostedsadresse
import no.nav.amt.distribusjon.journalforing.person.model.Kontaktadresse
import no.nav.amt.distribusjon.journalforing.person.model.Matrikkeladresse
import no.nav.amt.distribusjon.journalforing.person.model.NavBruker
import no.nav.amt.distribusjon.journalforing.person.model.NavEnhet
import no.nav.amt.distribusjon.journalforing.person.model.Oppfolgingsperiode
import no.nav.amt.distribusjon.journalforing.person.model.Vegadresse
import java.time.LocalDateTime
import java.util.UUID

object Persondata {
    fun lagNavBruker(
        fornavn: String = "Navn",
        mellomnavn: String? = null,
        etternavn: String = "Navnersen",
        navEnhet: NavEnhet? = lagNavEnhet(),
        oppfolgingsperioder: List<Oppfolgingsperiode> = listOf(lagOppfolgingsperiode()),
        adressebeskyttelse: Adressebeskyttelse? = null,
        adresse: Adresse? = lagAdresse(),
    ) = NavBruker(fornavn, mellomnavn, etternavn, navEnhet, oppfolgingsperioder, adressebeskyttelse, adresse)

    fun lagOppfolgingsperiode(
        id: UUID = UUID.randomUUID(),
        startdato: LocalDateTime = LocalDateTime.now().minusMonths(1),
        sluttdato: LocalDateTime? = null,
    ) = Oppfolgingsperiode(id, startdato, sluttdato)

    private fun lagNavEnhet(
        id: UUID = UUID.randomUUID(),
        enhetId: String = randomEnhetsnummer(),
        navn: String = "NAV Fyrstikkaleen",
    ) = NavEnhet(id, enhetId, navn)

    fun lagAdresse(): Adresse = Adresse(
        bostedsadresse = Bostedsadresse(
            coAdressenavn = "C/O Gutterommet",
            vegadresse = null,
            matrikkeladresse = Matrikkeladresse(
                tilleggsnavn = "Gården",
                postnummer = "0484",
                poststed = "OSLO",
            ),
        ),
        oppholdsadresse = null,
        kontaktadresse = Kontaktadresse(
            coAdressenavn = null,
            vegadresse = Vegadresse(
                husnummer = "1",
                husbokstav = null,
                adressenavn = "Gate",
                tilleggsnavn = null,
                postnummer = "1234",
                poststed = "MOSS",
            ),
            postboksadresse = null,
        ),
    )
}
