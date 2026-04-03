package no.nav.amt.lib.testing.utils

import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerVedImport
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.person.Oppfolgingsperiode
import no.nav.amt.lib.models.person.address.Adresse
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.models.person.address.Bostedsadresse
import no.nav.amt.lib.models.person.address.Kontaktadresse
import no.nav.amt.lib.models.person.address.Matrikkeladresse
import no.nav.amt.lib.models.person.address.Vegadresse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

object TestData {
    fun randomOrgnr() = (900_000_000..999_999_998).random().toString()

    fun randomIdent() = (10_00_00_00_000..31_12_00_99_999).random().toString()

    fun randomNavIdent() = ('A'..'Z').random().toString() + (100_000..999_999).random().toString()

    fun randomEnhetsnummer() = (1_000..9_999_999).random().toString()

    fun lagImportertFraArena(
        deltakerId: UUID = UUID.randomUUID(),
        importertDato: LocalDateTime = LocalDateTime.now(),
        deltakerVedImport: DeltakerVedImport = lagDeltakerVedImport(),
    ) = ImportertFraArena(
        deltakerId = deltakerId,
        importertDato = importertDato,
        deltakerVedImport = deltakerVedImport,
    )

    fun lagDeltakerVedImport(
        innsoktDato: LocalDate = LocalDate.now(),
        startdato: LocalDate? = null,
        sluttdato: LocalDate? = null,
        dagerPerUke: Float? = null,
        deltakelsesprosent: Float? = null,
        status: DeltakerStatus = DeltakerStatus(
            id = UUID.randomUUID(),
            type = DeltakerStatus.Type.DELTAR,
            aarsak = null,
            gyldigFra = LocalDateTime.now(),
            gyldigTil = null,
            opprettet = LocalDateTime.now(),
        ),
    ) = DeltakerVedImport(
        deltakerId = UUID.randomUUID(),
        innsoktDato = innsoktDato,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        status = status,
    )

    fun lagArrangor(
        id: UUID = UUID.randomUUID(),
        navn: String = "Arrangor 1",
        organisasjonsnummer: String = randomOrgnr(),
        overordnetArrangorId: UUID? = null,
    ) = Arrangor(
        id = id,
        navn = navn,
        organisasjonsnummer = organisasjonsnummer,
        overordnetArrangorId = overordnetArrangorId,
    )

    fun lagNavAnsatt(
        id: UUID = UUID.randomUUID(),
        navIdent: String = randomNavIdent(),
        navn: String = "Veileder Veiledersen",
        telefon: String = "99988777",
        epost: String = "ansatt@nav.no",
        navEnhetId: UUID? = null,
    ) = NavAnsatt(id, navIdent, navn, epost, telefon, navEnhetId)

    fun lagNavEnhet(
        id: UUID = UUID.randomUUID(),
        enhetsnummer: String = randomEnhetsnummer(),
        navn: String = "Nav Testheim",
    ) = NavEnhet(id, enhetsnummer, navn)

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

    fun lagOppfolgingsperiode(
        id: UUID = UUID.randomUUID(),
        startdato: LocalDateTime = LocalDateTime.now().minusMonths(1),
        sluttdato: LocalDateTime? = null,
    ) = Oppfolgingsperiode(
        id,
        startdato,
        sluttdato,
    )

    fun lagNavBruker(
        personId: UUID = UUID.randomUUID(),
        personident: String = randomIdent(),
        fornavn: String = "Fornavn",
        mellomnavn: String? = "Mellomnavn",
        etternavn: String = "Etternavn",
        navVeilederId: UUID? = UUID.randomUUID(),
        navEnhetId: UUID? = UUID.randomUUID(),
        telefon: String? = null,
        epost: String? = null,
        erSkjermet: Boolean = false,
        adresse: Adresse? = lagAdresse(),
        adressebeskyttelse: Adressebeskyttelse? = null,
        oppfolgingsperioder: List<Oppfolgingsperiode> = listOf(lagOppfolgingsperiode()),
        innsatsgruppe: Innsatsgruppe? = Innsatsgruppe.STANDARD_INNSATS,
    ) = NavBruker(
        personId,
        personident,
        fornavn,
        mellomnavn,
        etternavn,
        navVeilederId,
        navEnhetId,
        telefon,
        epost,
        erSkjermet,
        adresse,
        adressebeskyttelse,
        oppfolgingsperioder,
        innsatsgruppe,
    )

    fun lagDeltakerRegistreringInnhold(
        innholdselementer: List<Innholdselement> = listOf(Innholdselement("Tekst", "kode")),
        ledetekst: String = "Beskrivelse av tilaket",
    ) = DeltakerRegistreringInnhold(innholdselementer, ledetekst)
}
