import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PdlGraphqlWiringTest {
    @Test
    fun `can execute hentPerson query against schema`() {
        val graphQL = createPdlGraphql()

        val result = graphQL.execute(
            """
            {
                hentPerson(ident: "123") {
                    navn {
                        fornavn
                    }
                }
            }
            """.trimIndent(),
        )

        assertTrue(result.errors.isEmpty(), "Expected no GraphQL errors for hentPerson, but got: ${'$'}{result.errors}")
    }

    @Test
    fun `can execute hentIdenter query against schema`() {
        val graphQL = createPdlGraphql()

        val result = graphQL.execute(
            """
            {
                hentIdenter(ident: "123") {
                    identer {
                        ident
                    }
                }
            }
            """.trimIndent(),
        )

        assertTrue(result.errors.isEmpty(), "Expected no GraphQL errors for hentIdenter, but got: ${'$'}{result.errors}")
    }
}


