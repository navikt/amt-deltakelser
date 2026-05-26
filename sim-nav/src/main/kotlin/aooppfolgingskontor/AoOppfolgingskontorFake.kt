package aooppfolgingskontor

import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import graphql.ExecutionInput
import io.ktor.http.*
import io.ktor.server.routing.*
import readRequestBody
import respondJson

const val AO_OPPFOLGINGSKONTOR_PATH_PREFIX = "/ao-oppfolgingskontor"

private const val AO_OPPFOLGINGSKONTOR_DATA_PATH = "/ao-oppfolgingskontor/ao-oppfolgingskontor-data.json"

private val aoOppfolgingskontorObjectMapper = jacksonObjectMapper().findAndRegisterModules()
private val aoOppfolgingskontorFakeData: AoOppfolgingskontorFakeData = loadAoOppfolgingskontorFakeData()
private val aoOppfolgingskontorGraphql = createAoOppfolgingskontorGraphql(
    kontorTilhorigheterDataFetcher = { environment ->
        val ident = environment.getArgument<String>("ident") ?: ""
        aoOppfolgingskontorFakeData.findKontorTilhorigheter(ident)
    },
)

fun Route.aoOppfolgingskontorFakeRoutes() {
    route(AO_OPPFOLGINGSKONTOR_PATH_PREFIX) {
        get {
            respondJson(call, HttpStatusCode.OK, "{\"status\":\"ok\"}")
        }

        post("graphql") {
            val body = readRequestBody(call)
            val request = runCatching { aoOppfolgingskontorObjectMapper.readTree(body) }
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
                aoOppfolgingskontorObjectMapper.convertValue(variablesNode)
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

            val executionResult = aoOppfolgingskontorGraphql.execute(executionInput)
            val response = aoOppfolgingskontorObjectMapper.writeValueAsString(executionResult.toSpecification())
            val status = if (executionResult.errors.isEmpty()) HttpStatusCode.OK else HttpStatusCode.BadRequest

            respondJson(call, status, response)
        }
    }
}

private fun graphqlError(message: String): String = aoOppfolgingskontorObjectMapper.writeValueAsString(
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

private fun loadAoOppfolgingskontorFakeData(): AoOppfolgingskontorFakeData {
    val stream = object {}.javaClass.getResourceAsStream(AO_OPPFOLGINGSKONTOR_DATA_PATH)
        ?: throw IllegalStateException("Missing resource: $AO_OPPFOLGINGSKONTOR_DATA_PATH")
    return stream.use { aoOppfolgingskontorObjectMapper.readValue(it) }
}

private data class AoOppfolgingskontorFakeData(
    val defaultIdent: String,
    val kontorTilhorigheter: Map<String, KontorTilhorigheterFixture>,
) {
    fun findKontorTilhorigheter(ident: String): KontorTilhorigheterFixture = kontorTilhorigheter[ident]
        ?: kontorTilhorigheter[defaultIdent]
        ?: error("No ao-oppfolgingskontor fixture found for ident '$ident' and missing default fixture '$defaultIdent'")
}

private data class KontorTilhorigheterFixture(
    val arbeidsoppfolging: ArbeidsoppfolgingFixture? = null,
)

private data class ArbeidsoppfolgingFixture(
    val kontorId: String,
    val kontorNavn: String,
)

