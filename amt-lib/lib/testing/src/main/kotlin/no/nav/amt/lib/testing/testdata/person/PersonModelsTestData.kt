package no.nav.amt.lib.testing.testdata.person

import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.Oppfolgingsperiode
import java.time.LocalDateTime
import java.util.UUID

object PersonModelsTestData {
    val oppfolgingsperiodeInTest = Oppfolgingsperiode(
        id = UUID.randomUUID(),
        startdato = LocalDateTime.now(),
        sluttdato = null,
    )

    val enhetInTest = NavEnhet(
        id = UUID.randomUUID(),
        enhetsnummer = "~enhetsnummer~",
        navn = "~navn~",
    )

    val ansattInTest = NavAnsatt(
        id = UUID.randomUUID(),
        navIdent = "~navIdent~",
        navn = "~navn~",
        epost = "~epost~",
        telefon = "~telefon~",
        navEnhetId = UUID.randomUUID(),
    )

    val brukerInTest = NavBruker(
        personId = UUID.randomUUID(),
        personident = "~personident~",
        fornavn = "~fornavn~",
        mellomnavn = "~mellomnavn~",
        etternavn = "~etternavn~",
        navVeilederId = UUID.randomUUID(),
        navEnhetId = UUID.randomUUID(),
        telefon = "~telefon~",
        epost = "~epost~",
        erSkjermet = false,
        adresse = null,
        adressebeskyttelse = null,
        oppfolgingsperioder = emptyList(),
        innsatsgruppe = null,
    )
}
