package nom

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.response.respondText
import respondGraphqlFake
import shared.loadJsonResource

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
            call.respondText(text = "{\"status\":\"ok\"}", contentType = ContentType.Application.Json, status = HttpStatusCode.OK)
        }

        post("graphql") {
            respondGraphqlFake(call, nomObjectMapper, nomGraphql)
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


private fun loadNomFakeData(): NomFakeData {
    return loadJsonResource(nomObjectMapper, NOM_DATA_PATH)
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



