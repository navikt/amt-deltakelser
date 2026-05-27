package pdl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.server.routing.*
import respondGraphqlFake
import shared.loadJsonResource

const val PDL_PATH_PREFIX = "/pdl"

private const val PDL_DATA_PATH = "/pdl/pdl-data.json"

private val pdlObjectMapper = jacksonObjectMapper()
private val pdlFakeData: PdlFakeData = loadPdlFakeData()
private val pdlGraphql = createPdlGraphql(
    hentPersonDataFetcher = { environment ->
        val ident = environment.getArgument<String>("ident") ?: ""
        pdlFakeData.findPerson(ident)
    },
    hentIdenterDataFetcher = { environment ->
        val ident = environment.getArgument<String>("ident") ?: ""
        val grupper = environment.getArgument<List<Any?>?>("grupper")
            ?.mapNotNull { it?.toString() }
        val historikk = environment.getArgument<Boolean?>("historikk")

        mapOf(
            "identer" to pdlFakeData
                .findPerson(ident)
                .filteredIdenter(grupper = grupper, historikk = historikk),
        )
    },
)

fun Route.pdlFakeRoutes() {
    route(PDL_PATH_PREFIX) {

        post("graphql") {
            respondGraphqlFake(call, pdlObjectMapper, pdlGraphql)
        }
    }
}

private fun PdlPersonFixture.filteredIdenter(
    grupper: List<String>?,
    historikk: Boolean?,
): List<IdentInformasjonFixture> {
    val includeHistorical = historikk == true

    return identer.filter { identInfo ->
        val isRequestedGroup = grupper.isNullOrEmpty() || grupper.contains(identInfo.gruppe)
        val isRequestedHistoricalState = includeHistorical || !identInfo.historisk

        isRequestedGroup && isRequestedHistoricalState
    }
}


private fun loadPdlFakeData(): PdlFakeData {
    return loadJsonResource(pdlObjectMapper, PDL_DATA_PATH)
}


private data class PdlFakeData(
    val persons: Map<String, PdlPersonFixture>,
) {
    fun findPerson(ident: String): PdlPersonFixture = persons[ident]
        ?: error("No PDL fixture found for ident '$ident'")
}

private data class PdlPersonFixture(
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

private data class FalskIdentitetFixture(
    val erFalsk: Boolean,
)

private data class NavnFixture(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

private data class FoedselsdatoFixture(
    val foedselsaar: Int,
)

private data class AdressebeskyttelseFixture(
    val gradering: String,
)

private data class IdentInformasjonFixture(
    val ident: String,
    val historisk: Boolean,
    val gruppe: String,
)

private data class TelefonnummerFixture(
    val landskode: String,
    val nummer: String,
    val prioritet: Int,
)

private data class BostedsadresseFixture(
    val coAdressenavn: String?,
    val vegadresse: VegadresseFixture?,
    val matrikkeladresse: MatrikkeladresseFixture?,
)

private data class OppholdsadresseFixture(
    val coAdressenavn: String?,
    val vegadresse: VegadresseFixture?,
    val matrikkeladresse: MatrikkeladresseFixture?,
)

private data class KontaktadresseFixture(
    val coAdressenavn: String?,
    val vegadresse: VegadresseFixture?,
    val postboksadresse: PostboksadresseFixture?,
)

private data class VegadresseFixture(
    val husnummer: String?,
    val husbokstav: String?,
    val adressenavn: String?,
    val tilleggsnavn: String?,
    val postnummer: String?,
)

private data class MatrikkeladresseFixture(
    val tilleggsnavn: String?,
    val postnummer: String?,
)

private data class PostboksadresseFixture(
    val postboks: String,
    val postnummer: String?,
)
