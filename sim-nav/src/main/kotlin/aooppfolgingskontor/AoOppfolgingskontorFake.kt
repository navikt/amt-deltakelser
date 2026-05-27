package aooppfolgingskontor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.server.routing.*
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

        post("graphql") {
            respondGraphqlFake(call, aoOppfolgingskontorObjectMapper, aoOppfolgingskontorGraphql)
        }
    }
}

private fun loadAoOppfolgingskontorFakeData(): AoOppfolgingskontorFakeData {
    return loadJsonResource(aoOppfolgingskontorObjectMapper, AO_OPPFOLGINGSKONTOR_DATA_PATH)
}

private data class AoOppfolgingskontorFakeData(
    val kontorTilhorigheter: Map<String, KontorTilhorigheterFixture>,
) {
    fun findKontorTilhorigheter(ident: String): KontorTilhorigheterFixture = kontorTilhorigheter[ident]
        ?: error("No ao-oppfolgingskontor fixture found for ident '$ident'")
}

private data class KontorTilhorigheterFixture(
    val arbeidsoppfolging: ArbeidsoppfolgingFixture? = null,
)

private data class ArbeidsoppfolgingFixture(
    val kontorId: String,
    val kontorNavn: String,
)

