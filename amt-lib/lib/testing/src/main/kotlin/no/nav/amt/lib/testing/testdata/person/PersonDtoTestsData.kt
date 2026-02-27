package no.nav.amt.lib.testing.testdata.person

import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.models.person.dto.NavAnsattDto
import no.nav.amt.lib.models.person.dto.NavBrukerDto
import no.nav.amt.lib.models.person.dto.NavEnhetDto
import no.nav.amt.lib.testing.testdata.person.AdresseTestData.adresseInTest
import no.nav.amt.lib.testing.testdata.person.PersonModelsTestData.oppfolgingsperiodeInTest
import java.util.UUID

object PersonDtoTestsData {
    val enhetDtoInTest = NavEnhetDto(
        id = UUID.randomUUID(),
        enhetId = "~enhetId~",
        navn = "~navn~",
    )

    val ansattDtoInTest = NavAnsattDto(
        id = UUID.randomUUID(),
        navident = "~navident~",
        navn = "~navn~",
        epost = "~epost~",
        telefon = "~telefon~",
        navEnhetId = UUID.randomUUID(),
    )

    val brukerDtoInTest = NavBrukerDto(
        personId = UUID.randomUUID(),
        personident = "~personident~",
        fornavn = "~fornavn~",
        mellomnavn = "~mellomnavn~",
        etternavn = "~etternavn~",
        navVeilederId = UUID.randomUUID(),
        navEnhet = enhetDtoInTest,
        telefon = "~telefon~",
        epost = "~epost~",
        erSkjermet = true,
        adresse = adresseInTest,
        adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG,
        oppfolgingsperioder = listOf(oppfolgingsperiodeInTest),
        innsatsgruppe = Innsatsgruppe.STANDARD_INNSATS,
    )
}
