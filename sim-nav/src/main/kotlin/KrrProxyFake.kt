import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import shared.loadJsonResource

const val KRR_PROXY_PATH_PREFIX = "/digdir-krr-proxy"

private const val KRR_PROXY_DATA_PATH = "/krr/krr-data.json"

private val krrProxyObjectMapper = jacksonObjectMapper()
private val krrProxyFakeData: KrrProxyFakeData = loadKrrProxyFakeData()

fun Route.krrProxyFakeRoutes() {
    route(KRR_PROXY_PATH_PREFIX) {
        get {
            respondJson(call, HttpStatusCode.OK, "{\"status\":\"ok\"}")
        }

        post("rest/v1/personer") {
            val request = krrProxyObjectMapper.readValue<PostPersonerRequest>(readRequestBody(call))
            val personer = mutableMapOf<String, KontaktinformasjonFixtureResponse>()
            val feil = mutableMapOf<String, String>()

            request.personidenter.forEach { personident ->
                val kontaktinformasjon = krrProxyFakeData.findKontaktinformasjon(personident)
                if (kontaktinformasjon == null) {
                    feil[personident] = "Person ikke funnet"
                } else {
                    personer[personident] = KontaktinformasjonFixtureResponse(
                        personident = personident,
                        epostadresse = kontaktinformasjon.epostadresse,
                        mobiltelefonnummer = kontaktinformasjon.mobiltelefonnummer,
                    )
                }
            }

            respondJson(
                call,
                HttpStatusCode.OK,
                krrProxyObjectMapper.writeValueAsString(PostPersonerResponse(personer = personer, feil = feil)),
            )
        }
    }
}

private fun loadKrrProxyFakeData(): KrrProxyFakeData {
    return loadJsonResource(krrProxyObjectMapper, KRR_PROXY_DATA_PATH)
}

private data class KrrProxyFakeData(
    val personer: Map<String, KontaktinformasjonFixture>,
) {
    fun findKontaktinformasjon(personident: String): KontaktinformasjonFixture? = personer[personident]
}

private data class KontaktinformasjonFixture(
    val epostadresse: String?,
    val mobiltelefonnummer: String?,
)

private data class PostPersonerRequest(
    val personidenter: Set<String>,
)

private data class PostPersonerResponse(
    val personer: Map<String, KontaktinformasjonFixtureResponse>,
    val feil: Map<String, String>,
)

private data class KontaktinformasjonFixtureResponse(
    val personident: String,
    val epostadresse: String?,
    val mobiltelefonnummer: String?,
)

