package no.nav.tiltaksarrangor.consumer.model

import no.nav.amt.lib.models.person.address.Oppholdsadresse
import no.nav.tiltaksarrangor.model.Adresse
import no.nav.tiltaksarrangor.model.Adressetype
import no.nav.tiltaksarrangor.utils.objectMapper
import org.postgresql.util.PGobject

// Alt dette er duplikater fra lib men iom at dette brukes til å lagre json i databasen så beholder jeg datastrukturene
// for å unngå farlige situasjoner om modellen endres i lib uten å ta høyde for json formatet
data class AdresseJsonDbo(
	val bostedsadresse: BostedsadresseJsonDbo?,
	val oppholdsadresse: OppholdsadresseJsonDbo?,
	val kontaktadresse: KontaktadresseJsonDbo?,
) {
	fun toPGObject() = PGobject().also {
		it.type = "json"
		it.value = objectMapper.writeValueAsString(this)
	}

	companion object {
		fun fromModel(adresse: no.nav.amt.lib.models.person.address.Adresse) = AdresseJsonDbo(
			bostedsadresse = adresse.bostedsadresse?.let { BostedsadresseJsonDbo.fromModel(bostedsadresse = it) },
			oppholdsadresse = adresse.oppholdsadresse?.let { OppholdsadresseJsonDbo.fromModel(oppholdsadresse = it) },
			kontaktadresse = adresse.kontaktadresse?.let { KontaktadresseJsonDbo.fromModel(kontakadresse = it) },
		)
	}
}

data class BostedsadresseJsonDbo(
	val coAdressenavn: String?,
	val vegadresse: VegadresseJsonDbo?,
	val matrikkeladresse: MatrikkeladresseJsonDbo?,
) {
	fun toAdresse(): Adresse {
		if (vegadresse != null) {
			return Adresse(
				adressetype = Adressetype.BOSTEDSADRESSE,
				postnummer = vegadresse.postnummer,
				poststed = vegadresse.poststed,
				tilleggsnavn = vegadresse.tilleggsnavn,
				adressenavn = vegadresse.tilAdressenavn(coAdressenavn),
			)
		} else if (matrikkeladresse != null) {
			return Adresse(
				adressetype = Adressetype.BOSTEDSADRESSE,
				postnummer = matrikkeladresse.postnummer,
				poststed = matrikkeladresse.poststed,
				tilleggsnavn = matrikkeladresse.tilleggsnavn,
				adressenavn = coAdressenavn,
			)
		} else {
			throw IllegalStateException("Bostedsadresse må ha enten veiadresse eller matrikkeladresse")
		}
	}

	companion object {
		fun fromModel(bostedsadresse: no.nav.amt.lib.models.person.address.Bostedsadresse) = BostedsadresseJsonDbo(
			coAdressenavn = bostedsadresse.coAdressenavn,
			vegadresse = bostedsadresse.vegadresse?.let { VegadresseJsonDbo.fromModel(it) },
			matrikkeladresse = bostedsadresse.matrikkeladresse?.let { MatrikkeladresseJsonDbo.fromModel(it) },
		)
	}
}

data class OppholdsadresseJsonDbo(
	val coAdressenavn: String?,
	val vegadresse: VegadresseJsonDbo?,
	val matrikkeladresse: MatrikkeladresseJsonDbo?,
) {
	fun toAdresse(): Adresse {
		if (vegadresse != null) {
			return Adresse(
				adressetype = Adressetype.OPPHOLDSADRESSE,
				postnummer = vegadresse.postnummer,
				poststed = vegadresse.poststed,
				tilleggsnavn = vegadresse.tilleggsnavn,
				adressenavn = vegadresse.tilAdressenavn(coAdressenavn),
			)
		} else if (matrikkeladresse != null) {
			return Adresse(
				adressetype = Adressetype.OPPHOLDSADRESSE,
				postnummer = matrikkeladresse.postnummer,
				poststed = matrikkeladresse.poststed,
				tilleggsnavn = matrikkeladresse.tilleggsnavn,
				adressenavn = coAdressenavn,
			)
		} else {
			throw IllegalStateException("Oppholdsadresse må ha enten veiadresse eller matrikkeladresse")
		}
	}

	companion object {
		fun fromModel(oppholdsadresse: Oppholdsadresse) = OppholdsadresseJsonDbo(
			coAdressenavn = oppholdsadresse.coAdressenavn,
			vegadresse = oppholdsadresse.vegadresse?.let {
				VegadresseJsonDbo(
					it.husnummer,
					it.husbokstav,
					it.adressenavn,
					it.tilleggsnavn,
					it.postnummer,
					it.poststed,
				)
			},
			matrikkeladresse = oppholdsadresse.matrikkeladresse?.let { MatrikkeladresseJsonDbo(it.tilleggsnavn, it.postnummer, it.poststed) },
		)
	}
}

data class KontaktadresseJsonDbo(
	val coAdressenavn: String?,
	val vegadresse: VegadresseJsonDbo?,
	val postboksadresse: PostboksadresseJsonDbo?,
) {
	fun toAdresse(): Adresse {
		if (vegadresse != null) {
			return Adresse(
				adressetype = Adressetype.KONTAKTADRESSE,
				postnummer = vegadresse.postnummer,
				poststed = vegadresse.poststed,
				tilleggsnavn = vegadresse.tilleggsnavn,
				adressenavn = vegadresse.tilAdressenavn(coAdressenavn),
			)
		} else if (postboksadresse != null) {
			return Adresse(
				adressetype = Adressetype.KONTAKTADRESSE,
				postnummer = postboksadresse.postnummer,
				poststed = postboksadresse.poststed,
				tilleggsnavn = null,
				adressenavn = postboksadresse.tilAdressenavn(coAdressenavn),
			)
		} else {
			throw IllegalStateException("Kontaktadresse må ha enten veiadresse eller postboksadresse")
		}
	}

	companion object {
		fun fromModel(kontakadresse: no.nav.amt.lib.models.person.address.Kontaktadresse) = KontaktadresseJsonDbo(
			coAdressenavn = kontakadresse.coAdressenavn,
			vegadresse = kontakadresse.vegadresse?.let { VegadresseJsonDbo.fromModel(it) },
			postboksadresse = kontakadresse.postboksadresse?.let { PostboksadresseJsonDbo.fromModel(it) },
		)
	}
}

data class VegadresseJsonDbo(
	val husnummer: String?,
	val husbokstav: String?,
	val adressenavn: String?,
	val tilleggsnavn: String?,
	val postnummer: String,
	val poststed: String,
) {
	fun tilAdressenavn(coAdressenavn: String?): String {
		val adressenavn = "${(adressenavn ?: "")} ${(husnummer ?: "")}${(husbokstav ?: "")}"
		if (coAdressenavn.isNullOrEmpty()) {
			return adressenavn
		}
		return "$coAdressenavn, $adressenavn"
	}

	companion object {
		fun fromModel(vegadresse: no.nav.amt.lib.models.person.address.Vegadresse) = VegadresseJsonDbo(
			husnummer = vegadresse.husnummer,
			husbokstav = vegadresse.husbokstav,
			adressenavn = vegadresse.adressenavn,
			tilleggsnavn = vegadresse.tilleggsnavn,
			postnummer = vegadresse.postnummer,
			poststed = vegadresse.poststed,
		)
	}
}

data class MatrikkeladresseJsonDbo(
	val tilleggsnavn: String?,
	val postnummer: String,
	val poststed: String,
) {
	companion object {
		fun fromModel(matrikkeladresse: no.nav.amt.lib.models.person.address.Matrikkeladresse) = MatrikkeladresseJsonDbo(
			tilleggsnavn = matrikkeladresse.tilleggsnavn,
			postnummer = matrikkeladresse.postnummer,
			poststed = matrikkeladresse.poststed,
		)
	}
}

data class PostboksadresseJsonDbo(
	val postboks: String,
	val postnummer: String,
	val poststed: String,
) {
	fun tilAdressenavn(coAdressenavn: String?): String {
		if (coAdressenavn.isNullOrEmpty()) {
			return "Postboks $postboks"
		}
		return "$coAdressenavn, postboks $postboks"
	}

	companion object {
		fun fromModel(postboksadresse: no.nav.amt.lib.models.person.address.Postboksadresse) = PostboksadresseJsonDbo(
			postboks = postboksadresse.postboks,
			postnummer = postboksadresse.postnummer,
			poststed = postboksadresse.poststed,
		)
	}
}
