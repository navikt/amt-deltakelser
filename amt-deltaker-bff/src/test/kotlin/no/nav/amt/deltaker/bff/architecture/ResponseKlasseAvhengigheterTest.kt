package no.nav.amt.deltaker.bff.architecture

import no.nav.amt.felles.testing.architecture.assertResponseFieldsUseAllowedTypes
import org.junit.jupiter.api.Test

val responsePakker = arrayOf(
    "no.nav.amt.deltaker.bff.commonresponse..",
    "no.nav.amt.deltaker.bff..api.response..",
    "no.nav.amt.internapi.deltaker.response..",
)

class ResponseKlasseAvhengigheterTest {
    @Test
    fun `Response-klasser skal kun ha tillatte felttyper`() {
        assertResponseFieldsUseAllowedTypes(
            importedPackages = listOf(
                "no.nav.amt.deltaker.bff",
            ),
            responsePackagePatterns = responsePakker,
        )
    }
}
