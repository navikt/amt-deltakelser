package no.nav.tiltaksarrangor.architecture

import no.nav.amt.felles.testing.architecture.assertResponseFieldsUseAllowedTypes
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

private val responsePakker = arrayOf(
    "no.nav.tiltaksarrangor.api.response..",
)

class ResponseKlasseAvhengigheterTest {
    @Test
    @Disabled("Fix me")
    fun `Response-klasser skal kun ha tillatte felttyper`() {
        assertResponseFieldsUseAllowedTypes(
            importedPackages = listOf("no.nav.tiltaksarrangor"),
            responsePackagePatterns = responsePakker,
        )
    }
}
