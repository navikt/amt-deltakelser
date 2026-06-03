package tjenester.nav.pdl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import shared.loadJsonResource

private const val PDL_DATA_PATH = "/pdl/pdl-data.json"

interface PdlDataSource {
    fun findPerson(ident: String): PdlPersonFixture
    fun allPersons(): Map<String, PdlPersonFixture>
}

class PdlSimulator(
    objectMapper: ObjectMapper = jacksonObjectMapper(),
) : PdlDataSource {
    private val data: PdlFakeData = loadJsonResource(objectMapper, PDL_DATA_PATH)

    override fun findPerson(ident: String): PdlPersonFixture = data.persons[ident]
        ?: error("No PDL fixture found for ident '$ident'")

    override fun allPersons(): Map<String, PdlPersonFixture> = data.persons
}

data class PdlFakeData(
    val persons: Map<String, PdlPersonFixture>,
)

data class PdlPersonFixture(
    val falskIdentitet: FalskIdentitetFixture,
    val navn: List<NavnFixture>,
    val foedselsdato: List<FoedselsdatoFixture>,
    val adressebeskyttelse: List<AdressebeskyttelseFixture>,
    val identer: List<IdentInformasjonFixture>,
    val telefonnummer: List<TelefonnummerFixture>,
    val bostedsadresse: List<BostedsadresseFixture>,
    val oppholdsadresse: List<OppholdsadresseFixture>,
    val kontaktadresse: List<KontaktadresseFixture>,
)

data class FalskIdentitetFixture(
    val erFalsk: Boolean,
)

data class NavnFixture(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

data class FoedselsdatoFixture(
    val foedselsaar: Int,
)

data class AdressebeskyttelseFixture(
    val gradering: String,
)

data class IdentInformasjonFixture(
    val ident: String,
    val historisk: Boolean,
    val gruppe: String,
)

data class TelefonnummerFixture(
    val landskode: String,
    val nummer: String,
    val prioritet: Int,
)

data class BostedsadresseFixture(
    val coAdressenavn: String?,
    val vegadresse: VegadresseFixture?,
    val matrikkeladresse: MatrikkeladresseFixture?,
)

data class OppholdsadresseFixture(
    val coAdressenavn: String?,
    val vegadresse: VegadresseFixture?,
    val matrikkeladresse: MatrikkeladresseFixture?,
)

data class KontaktadresseFixture(
    val coAdressenavn: String?,
    val vegadresse: VegadresseFixture?,
    val postboksadresse: PostboksadresseFixture?,
)

data class VegadresseFixture(
    val husnummer: String?,
    val husbokstav: String?,
    val adressenavn: String?,
    val tilleggsnavn: String?,
    val postnummer: String?,
)

data class MatrikkeladresseFixture(
    val tilleggsnavn: String?,
    val postnummer: String?,
)

data class PostboksadresseFixture(
    val postboks: String,
    val postnummer: String?,
)

