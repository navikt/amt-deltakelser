package no.nav.amt.lib.testing.testdata.person

import no.nav.amt.lib.models.person.address.Adresse
import no.nav.amt.lib.models.person.address.Bostedsadresse
import no.nav.amt.lib.models.person.address.Kontaktadresse
import no.nav.amt.lib.models.person.address.Matrikkeladresse
import no.nav.amt.lib.models.person.address.Oppholdsadresse
import no.nav.amt.lib.models.person.address.Postboksadresse
import no.nav.amt.lib.models.person.address.Vegadresse

object AdresseTestData {
    val adresseInTest = Adresse(
        bostedsadresse = Bostedsadresse(
            coAdressenavn = "~bostedsadresse.coAdressenavn~",
            vegadresse = Vegadresse(
                husnummer = "~bostedsadresse.vegadresse.husnummer~",
                husbokstav = "~bostedsadresse.vegadresse.husbokstav~",
                adressenavn = "~bostedsadresse.vegadresse.adressenavn~",
                tilleggsnavn = "~bostedsadresse.vegadresse.tilleggsnavn~",
                postnummer = "~bostedsadresse.vegadresse.postnummer~",
                poststed = "~bostedsadresse.vegadresse.poststed~",
            ),
            matrikkeladresse = Matrikkeladresse(
                tilleggsnavn = "~bostedsadresse.matrikkeladresse.tilleggsnavn~",
                postnummer = "~bostedsadresse.matrikkeladresse.postnummer~",
                poststed = "~bostedsadresse.matrikkeladresse.poststed~",
            ),
        ),
        oppholdsadresse = Oppholdsadresse(
            coAdressenavn = "~oppholdsadresse.coAdressenavn~",
            vegadresse = Vegadresse(
                husnummer = "~oppholdsadresse.vegadresse.husnummer~",
                husbokstav = "~oppholdsadresse.vegadresse.husbokstav~",
                adressenavn = "~oppholdsadresse.vegadresse.adressenavn~",
                tilleggsnavn = "~oppholdsadresse.vegadresse.tilleggsnavn~",
                postnummer = "~oppholdsadresse.vegadresse.postnummer~",
                poststed = "~oppholdsadresse.vegadresse.poststed~",
            ),
            matrikkeladresse = Matrikkeladresse(
                tilleggsnavn = "~oppholdsadresse.matrikkeladresse.tilleggsnavn~",
                postnummer = "~oppholdsadresse.matrikkeladresse.postnummer~",
                poststed = "~oppholdsadresse.matrikkeladresse.poststed~",
            ),
        ),
        kontaktadresse = Kontaktadresse(
            coAdressenavn = "~kontaktadresse.coAdressenavn~",
            vegadresse = Vegadresse(
                husnummer = "~kontaktadresse.vegadresse.husnummer~",
                husbokstav = "~kontaktadresse.vegadresse.husbokstav~",
                adressenavn = "~kontaktadresse.vegadresse.adressenavn~",
                tilleggsnavn = "~kontaktadresse.vegadresse.tilleggsnavn~",
                postnummer = "~kontaktadresse.vegadresse.postnummer~",
                poststed = "~kontaktadresse.vegadresse.poststed~",
            ),
            postboksadresse = Postboksadresse(
                postboks = "~kontaktadresse.postboksadresse.postboks~",
                postnummer = "~kontaktadresse.postboksadresse.postnummer~",
                poststed = "~kontaktadresse.postboksadresse.poststed~",
            ),
        ),
    )
}
