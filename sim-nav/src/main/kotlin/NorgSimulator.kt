import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import shared.loadJsonResource

private const val NORG_DATA_PATH = "/norg/norg-data.json"

interface NorgDataSource {
	fun findEnhet(enhetId: String): NorgNavEnhetFixture?
	fun findEnheter(enhetsnummerListe: List<String>): List<NorgNavEnhetFixture>
	fun allEnheter(): List<NorgNavEnhetFixture>
}

class NorgSimulator(
	objectMapper: ObjectMapper = jacksonObjectMapper(),
) : NorgDataSource {
	private val data: NorgFakeData = loadJsonResource(objectMapper, NORG_DATA_PATH)

	override fun findEnhet(enhetId: String): NorgNavEnhetFixture? = data.enheter[enhetId]

	override fun findEnheter(enhetsnummerListe: List<String>): List<NorgNavEnhetFixture> =
		enhetsnummerListe.mapNotNull { data.enheter[it] }

	override fun allEnheter(): List<NorgNavEnhetFixture> = data.enheter.values.sortedBy { it.enhetNr }
}

data class NorgFakeData(
	val enheter: Map<String, NorgNavEnhetFixture>,
)

data class NorgNavEnhetFixture(
	val navn: String,
	val enhetNr: String,
)

