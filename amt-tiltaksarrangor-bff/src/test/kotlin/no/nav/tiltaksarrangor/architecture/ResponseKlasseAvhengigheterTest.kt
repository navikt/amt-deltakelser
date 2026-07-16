package no.nav.tiltaksarrangor.architecture

import no.nav.amt.lib.testing.architecture.assertResponseFieldsUseAllowedTypes
import org.junit.jupiter.api.Test

private val responsePakker = arrayOf(
    "no.nav.tiltaksarrangor.api.response..",
)

private val tillatteIkkeResponsePakker = arrayOf(
    "no.nav.amt.lib.models.arrangor.melding..",
    "no.nav.amt.lib.models.deltaker..",
    "no.nav.amt.lib.models.tiltakskoordinator..",
    "no.nav.tiltaksarrangor.model..",
)

class ResponseKlasseAvhengigheterTest {
    @Test
    fun `Response-klasser skal kun ha tillatte felttyper`() {
        assertResponseFieldsUseAllowedTypes(
            importedPackages = listOf("no.nav.tiltaksarrangor"),
            responsePackagePatterns = responsePakker,
            additionalAllowedPackagePatterns = tillatteIkkeResponsePakker,
        )
    }
}
