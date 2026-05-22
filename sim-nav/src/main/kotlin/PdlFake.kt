import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.routing.*

const val PDL_PATH_PREFIX = "/pdl"

private const val PDL_DATA_PATH = "/pdl-data.json"

private val pdlObjectMapper = jacksonObjectMapper()
private val pdlFakeData: PdlFakeData = loadPdlFakeData()

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

            val query = request.path("query").asText("")
            val ident = request.path("variables").path("ident").asText("")
            val person = pdlFakeData.findPerson(ident)

            val response = when (detectPdlOperation(query)) {
                PdlOperation.HENT_PERSON -> graphqlData(
                    mapOf(
                        "hentPerson" to person.toHentPersonResponse(),
                        "hentIdenter" to mapOf("identer" to person.identer),
                    ),
                )

                PdlOperation.HENT_PERSON_FODSELSAAR -> graphqlData(
                    mapOf(
                        "hentPerson" to mapOf(
                            "foedselsdato" to listOf(mapOf("foedselsaar" to person.foedselsaar)),
                        ),
                    ),
                )

                PdlOperation.HENT_ADRESSEBESKYTTELSE -> graphqlData(
                    mapOf(
                        "hentPerson" to mapOf(
                            "adressebeskyttelse" to listOf(mapOf("gradering" to person.adressebeskyttelse)),
                        ),
                    ),
                )

                PdlOperation.HENT_TELEFON -> graphqlData(
                    mapOf(
                        "hentPerson" to mapOf("telefonnummer" to person.telefonnummer),
                    ),
                )

                PdlOperation.HENT_IDENTER -> graphqlData(
                    mapOf(
                        "hentIdenter" to mapOf("identer" to person.identer),
                    ),
                )

                PdlOperation.UNKNOWN -> graphqlError("Unsupported PDL query")
            }

            val status = if (response.contains("\"errors\"")) HttpStatusCode.BadRequest else HttpStatusCode.OK
            respondJson(call, status, response)
        }
    }
}

private fun detectPdlOperation(query: String): PdlOperation {
    val compact = query.replace("\n", " ")

    return when {
        compact.contains("hentPerson") && compact.contains("falskIdentitet") && compact.contains("hentIdenter") -> PdlOperation.HENT_PERSON
        compact.contains("foedselsdato") -> PdlOperation.HENT_PERSON_FODSELSAAR
        compact.contains("hentPerson") && compact.contains("adressebeskyttelse") && !compact.contains("telefonnummer") && !compact.contains(
            "navn"
        ) -> PdlOperation.HENT_ADRESSEBESKYTTELSE

        compact.contains("hentPerson") && compact.contains("telefonnummer") && !compact.contains("hentIdenter") && !compact.contains("navn") -> PdlOperation.HENT_TELEFON
        compact.contains("hentIdenter") && !compact.contains("hentPerson") -> PdlOperation.HENT_IDENTER
        else -> PdlOperation.UNKNOWN
    }
}

private fun PdlPersonFixture.toHentPersonResponse(): Map<String, Any?> = mapOf(
    "falskIdentitet" to mapOf("erFalsk" to erFalskIdentitet),
    "navn" to listOf(
        mapOf(
            "fornavn" to fornavn,
            "mellomnavn" to mellomnavn,
            "etternavn" to etternavn,
        ),
    ),
    "telefonnummer" to telefonnummer,
    "adressebeskyttelse" to listOf(mapOf("gradering" to adressebeskyttelse)),
    "bostedsadresse" to bostedsadresse,
    "oppholdsadresse" to oppholdsadresse,
    "kontaktadresse" to kontaktadresse,
)

private fun graphqlData(data: Any): String = pdlObjectMapper.writeValueAsString(mapOf("data" to data))

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

private enum class PdlOperation {
    HENT_PERSON,
    HENT_PERSON_FODSELSAAR,
    HENT_ADRESSEBESKYTTELSE,
    HENT_TELEFON,
    HENT_IDENTER,
    UNKNOWN,
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

