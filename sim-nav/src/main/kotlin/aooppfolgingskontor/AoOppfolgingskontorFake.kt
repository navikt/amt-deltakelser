package aooppfolgingskontor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.response.respondText
import respondGraphqlFake
import shared.loadJsonResource

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
            call.respondText(text = "{\"status\":\"ok\"}", contentType = ContentType.Application.Json, status = HttpStatusCode.OK)
        }

        post("graphql") {
            respondGraphqlFake(call, aoOppfolgingskontorObjectMapper, aoOppfolgingskontorGraphql)
        }
    }
}

private fun loadAoOppfolgingskontorFakeData(): AoOppfolgingskontorFakeData {
    return loadJsonResource(aoOppfolgingskontorObjectMapper, AO_OPPFOLGINGSKONTOR_DATA_PATH)
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

