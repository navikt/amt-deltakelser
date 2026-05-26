import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import graphql.ExecutionInput
import io.ktor.http.*
import io.ktor.server.routing.*

const val PDL_PATH_PREFIX = "/pdl"

private const val PDL_DATA_PATH = "/pdl-data.json"

private val pdlObjectMapper = jacksonObjectMapper()
private val pdlFakeData: PdlFakeData = loadPdlFakeData()
private val pdlGraphql = createPdlGraphql(
    hentPersonDataFetcher = { environment ->
        val ident = environment.getArgument<String>("ident") ?: ""
        pdlFakeData.findPerson(ident).toGraphqlPerson()
    },
    hentIdenterDataFetcher = { environment ->
        val ident = environment.getArgument<String>("ident") ?: ""
        val grupper = environment.getArgument<List<String>?>("grupper")
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

private fun PdlPersonFixture.toGraphqlPerson(): Map<String, Any?> = mapOf(
    "falskIdentitet" to mapOf("erFalsk" to erFalskIdentitet),
    "navn" to listOf(
        mapOf(
            "fornavn" to fornavn,
            "mellomnavn" to mellomnavn,
            "etternavn" to etternavn,
        ),
    ),
    "foedselsdato" to listOf(mapOf("foedselsaar" to foedselsaar)),
    "telefonnummer" to telefonnummer,
    "adressebeskyttelse" to listOf(mapOf("gradering" to adressebeskyttelse)),
    "bostedsadresse" to bostedsadresse,
    "oppholdsadresse" to oppholdsadresse,
    "kontaktadresse" to kontaktadresse,
)

private fun PdlPersonFixture.filteredIdenter(
    grupper: List<String>?,
    historikk: Boolean?,
): List<Map<String, Any?>> {
    val includeHistorical = historikk == true

    return identer.filter { identInfo ->
        val gruppe = identInfo["gruppe"] as? String
        val identIsHistorical = identInfo["historisk"] as? Boolean ?: false

        val isRequestedGroup = grupper.isNullOrEmpty() || (gruppe != null && grupper.contains(gruppe))
        val isRequestedHistoricalState = includeHistorical || !identIsHistorical

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
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val foedselsaar: Int,
    val erFalskIdentitet: Boolean,
    val adressebeskyttelse: String,
    val identer: List<Map<String, Any?>>,
    val telefonnummer: List<Map<String, Any?>>,
    val bostedsadresse: List<Map<String, Any?>>,
    val oppholdsadresse: List<Map<String, Any?>>,
    val kontaktadresse: List<Map<String, Any?>>,
)

