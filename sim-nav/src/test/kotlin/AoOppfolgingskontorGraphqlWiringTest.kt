import aooppfolgingskontor.createAoOppfolgingskontorGraphql
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AoOppfolgingskontorGraphqlWiringTest {
    @Test
    fun `can execute kontorTilhorigheter query against schema`() {
        val graphQL = createAoOppfolgingskontorGraphql(
            kontorTilhorigheterDataFetcher = {
                mapOf(
                    "arbeidsoppfolging" to mapOf(
                        "kontorId" to "1234",
                        "kontorNavn" to "NAV Testkontor",
                    ),
                )
            },
        )

        val result = graphQL.execute(
            """
            {
                kontorTilhorigheter(ident: "12345678901") {
                    arbeidsoppfolging {
                        kontorId
                    }
                }
            }
            """.trimIndent(),
        )

        assertTrue(
            result.errors.isEmpty(),
            "Expected no GraphQL errors for kontorTilhorigheter, but got: ${result.errors}",
        )
    }
}

