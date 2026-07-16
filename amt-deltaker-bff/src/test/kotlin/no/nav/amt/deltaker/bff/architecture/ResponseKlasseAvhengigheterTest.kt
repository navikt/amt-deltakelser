package no.nav.amt.deltaker.bff.architecture

import no.nav.amt.felles.testing.architecture.assertResponseFieldsUseAllowedTypes
import org.junit.jupiter.api.Test

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
