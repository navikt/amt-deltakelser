import nom.createNomGraphql
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NomGraphqlWiringTest {
    @Test
    fun `can execute ressurser query against schema`() {
        val graphQL = createNomGraphql(
            ressurserDataFetcher = {
                listOf(
                    mapOf(
                        "code" to "OK",
                        "id" to "Z123456",
                        "ressurs" to mapOf(
                            "navident" to "Z123456",
                            "visningsnavn" to "Sim Veileder",
                            "fornavn" to "Sim",
                            "etternavn" to "Veileder",
                            "epost" to "sim.veileder@nav.no",
                            "telefon" to listOf(
                                mapOf(
                                    "nummer" to "+47 22 00 00 00",
                                    "type" to "NAV_TJENESTE_TELEFON",
                                ),
                            ),
                            "primaryTelefon" to "+47 40 00 00 00",
                            "orgTilknytning" to listOf(
                                mapOf(
                                    "gyldigFom" to "2020-01-01",
                                    "gyldigTom" to null,
                                    "orgEnhet" to mapOf("remedyEnhetId" to "0315"),
                                    "erDagligOppfolging" to true,
                                ),
                            ),
                        ),
                    ),
                )
            },
        )

        val result = graphQL.execute(
            """
            {
                ressurser(where: { navidenter: ["Z123456"] }) {
                    code
                }
            }
            """.trimIndent(),
        )

        assertTrue(result.errors.isEmpty(), "Expected no GraphQL errors for ressurser, but got: ${result.errors}")
    }
}

