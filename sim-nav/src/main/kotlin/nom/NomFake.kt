package nom

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import graphql.ExecutionInput
import io.ktor.http.*
import io.ktor.server.routing.*
import readRequestBody
import respondJson

const val NOM_PATH_PREFIX = "/nom"

private const val NOM_DATA_PATH = "/nom/nom-data.json"

private val nomObjectMapper = jacksonObjectMapper().findAndRegisterModules()
private val nomFakeData: NomFakeData = loadNomFakeData()
private val nomGraphql = createNomGraphql(
    ressurserDataFetcher = { environment ->
        val where = environment.getArgument<Map<String, Any?>?>("where") ?: emptyMap()
        val navidenter = readNavidenter(where)

        nomFakeData.toRessurserResult(navidenter)
    },
)

fun Route.nomFakeRoutes() {
    route(NOM_PATH_PREFIX) {
        get {
            respondJson(call, HttpStatusCode.OK, "{\"status\":\"ok\"}")
        }

        post("graphql") {
            val body = readRequestBody(call)
            val request = runCatching { nomObjectMapper.readTree(body) }
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
                nomObjectMapper.convertValue(variablesNode)
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

            val executionResult = nomGraphql.execute(executionInput)
            val response = nomObjectMapper.writeValueAsString(executionResult.toSpecification())
            val status = if (executionResult.errors.isEmpty()) HttpStatusCode.OK else HttpStatusCode.BadRequest

            respondJson(call, status, response)
        }
    }
}

private fun readNavidenter(where: Map<String, Any?>): List<String> =
    readStringList(where["navidenter"]) ?: readStringList(where["navIdenter"]) ?: emptyList()

private fun readStringList(value: Any?): List<String>? {
    val entries = value as? List<*> ?: return null
    return entries.mapNotNull { it?.toString() }
}

private fun NomFakeData.toRessurserResult(navidenter: List<String>): List<Any?> =
    navidenter.map { navident ->
        val ressurs = resources[navident] ?: resources[defaultNavident]

        if (ressurs != null && ressurs.navident == navident) {
            mapOf(
                "code" to "OK",
                "id" to navident,
                "ressurs" to ressurs,
            )
        } else {
            mapOf(
                "code" to "NOT_FOUND",
                "id" to navident,
                "ressurs" to null,
            )
        }
    }

private fun graphqlError(message: String): String = nomObjectMapper.writeValueAsString(
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

private fun loadNomFakeData(): NomFakeData {
    val stream = object {}.javaClass.getResourceAsStream(NOM_DATA_PATH)
        ?: throw IllegalStateException("Missing resource: $NOM_DATA_PATH")
    return stream.use { nomObjectMapper.readValue(it) }
}

private data class NomFakeData(
    val defaultNavident: String,
    val resources: Map<String, NomRessursFixture>,
)

private data class NomRessursFixture(
    val navident: String,
    val visningsnavn: String,
    val fornavn: String,
    val etternavn: String,
    val epost: String,
    val primaryTelefon: String?,
    val telefon: List<NomTelefonFixture>,
    val orgTilknytning: List<NomOrgTilknytningFixture>,
)

private data class NomTelefonFixture(
    val nummer: String,
    val type: String,
)

private data class NomOrgTilknytningFixture(
    val gyldigFom: String,
    val gyldigTom: String?,
    val orgEnhet: NomOrgEnhetFixture,
    val erDagligOppfolging: Boolean,
)

private data class NomOrgEnhetFixture(
    val remedyEnhetId: String?,
)



