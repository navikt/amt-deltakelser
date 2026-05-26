package pdl

import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import graphql.ExecutionInput
import io.ktor.http.*
import io.ktor.server.routing.*
import readRequestBody
import respondJson

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
        get {
            respondJson(call, HttpStatusCode.OK, "{\"status\":\"ok\"}")
        }

        post("graphql") {
            val body = readRequestBody(call)
            val request = runCatching { pdlObjectMapper.readTree(body) }
                .getOrElse {
                    respondJson(call, HttpStatusCode.BadRequest, graphqlError("Invalid JSON payload"))
                    return@post
                }

            val query = request.path("query").asText("").trim()
            if (query.isBlank()) {
                respondJson(call, HttpStatusCode.BadRequest, graphqlError("Missing GraphQL query"))
                return@post
            }

            val variablesNode = request.path("variables")
            if (!variablesNode.isMissingNode && !variablesNode.isNull && !variablesNode.isObject) {
                respondJson(call, HttpStatusCode.BadRequest, graphqlError("'variables' must be a JSON object"))
                return@post
            }

            val variables: Map<String, Any?> = if (variablesNode.isObject) {
                pdlObjectMapper.convertValue(variablesNode)
            } else {
                emptyMap()
            }

            val operationName = request.path("operationName")
                .asText("")
                .takeIf { it.isNotBlank() }

            val executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .operationName(operationName)
                .variables(variables)
                .build()

            val executionResult = pdlGraphql.execute(executionInput)
            val response = pdlObjectMapper.writeValueAsString(executionResult.toSpecification())
            val status = if (executionResult.errors.isEmpty()) HttpStatusCode.OK else HttpStatusCode.BadRequest

            respondJson(call, status, response)
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

private fun graphqlError(message: String): String = pdlObjectMapper.writeValueAsString(
    mapOf(
        "errors" to listOf(
            mapOf(
                "message" to message,
                "extensions" to mapOf("code" to "BAD_REQUEST"),
            ),
        ),
        "data" to null,
    ),
)

private fun loadPdlFakeData(): PdlFakeData {
    val stream = object {}.javaClass.getResourceAsStream(PDL_DATA_PATH)
        ?: throw IllegalStateException("Missing resource: $PDL_DATA_PATH")
    return stream.use { pdlObjectMapper.readValue(it) }
}


private data class PdlFakeData(
    val defaultIdent: String,
    val persons: Map<String, PdlPersonFixture>,
) {
    fun findPerson(ident: String): PdlPersonFixture = persons[ident]
        ?: persons[defaultIdent]
        ?: error("No PDL fixture found for ident '$ident' and missing default fixture '$defaultIdent'")
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
