package no.nav.amt.internapi.deltaker.response

import io.kotest.matchers.shouldBe
import no.nav.amt.internapi.tiltakskoordinator.response.TiltakskoordinatorNavBrukerResponse
import no.nav.amt.lib.models.person.Beskyttelsesmarkering
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue

class TiltakskoordinatorNavBrukerResponseTest {
    private fun lagResponse(
        erSkjermet: Boolean = false,
        adressebeskyttelse: Adressebeskyttelse? = null,
    ) = TiltakskoordinatorNavBrukerResponse(
        personident = "12345678901",
        fornavn = "Fornavn",
        mellomnavn = null,
        etternavn = "Etternavn",
        erSkjermet = erSkjermet,
        adressebeskyttelse = adressebeskyttelse,
        navEnhet = null,
        ikkeDigitalOgManglerAdresse = false,
    )

    @Test
    fun `beskyttelsesmarkeringer - ingen beskyttelse - returnerer tom liste`() {
        val response = lagResponse()

        response.beskyttelsesmarkeringer shouldBe emptyList()
    }

    @Test
    fun `beskyttelsesmarkeringer - skjermet - returnerer SKJERMET`() {
        val response = lagResponse(erSkjermet = true)

        response.beskyttelsesmarkeringer shouldBe listOf(Beskyttelsesmarkering.SKJERMET)
    }

    @Test
    fun `beskyttelsesmarkeringer - adressebeskyttelse FORTROLIG - returnerer FORTROLIG`() {
        val response = lagResponse(adressebeskyttelse = Adressebeskyttelse.FORTROLIG)

        response.beskyttelsesmarkeringer shouldBe listOf(Beskyttelsesmarkering.FORTROLIG)
    }

    @Test
    fun `beskyttelsesmarkeringer - adressebeskyttelse og skjermet - returnerer begge`() {
        val response = lagResponse(
            erSkjermet = true,
            adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG,
        )

        response.beskyttelsesmarkeringer shouldBe listOf(
            Beskyttelsesmarkering.STRENGT_FORTROLIG,
            Beskyttelsesmarkering.SKJERMET,
        )
    }

    @Test
    fun `beskyttelsesmarkeringer - serialiseres ikke til JSON`() {
        val response = lagResponse(erSkjermet = true)

        val json = objectMapper.writeValueAsString(response)
        val jsonMap: Map<String, Any?> = objectMapper.readValue(json)

        jsonMap.containsKey("beskyttelsesmarkeringer") shouldBe false
    }

    @Test
    fun `beskyttelsesmarkeringer - deserialisering uten feltet gir korrekt beregnet verdi`() {
        val original = lagResponse(
            erSkjermet = true,
            adressebeskyttelse = Adressebeskyttelse.FORTROLIG,
        )

        val json = objectMapper.writeValueAsString(original)
        val deserialized: TiltakskoordinatorNavBrukerResponse = objectMapper.readValue(json)

        deserialized.beskyttelsesmarkeringer shouldBe listOf(
            Beskyttelsesmarkering.FORTROLIG,
            Beskyttelsesmarkering.SKJERMET,
        )
    }

    @Test
    fun `getVisningsnavn - ingen beskyttelse - returnerer fullt navn`() {
        val response = lagResponse()

        response.getVisningsnavn(tilgangTilBruker = true) shouldBe Triple("Fornavn", null, "Etternavn")
    }

    @Test
    fun `getVisningsnavn - adressebeskyttet uten tilgang - returnerer placeholder`() {
        val response = lagResponse(adressebeskyttelse = Adressebeskyttelse.FORTROLIG)

        response.getVisningsnavn(tilgangTilBruker = false) shouldBe Triple("Adressebeskyttet", null, "")
    }

    @Test
    fun `getVisningsnavn - adressebeskyttet med tilgang - returnerer fullt navn`() {
        val response = lagResponse(adressebeskyttelse = Adressebeskyttelse.FORTROLIG)

        response.getVisningsnavn(tilgangTilBruker = true) shouldBe Triple("Fornavn", null, "Etternavn")
    }

    @Test
    fun `getVisningsnavn - skjermet uten tilgang - returnerer placeholder`() {
        val response = lagResponse(erSkjermet = true)

        response.getVisningsnavn(tilgangTilBruker = false) shouldBe Triple("Skjermet person", null, "")
    }

    @Test
    fun `getVisningsnavn - skjermet med tilgang - returnerer fullt navn`() {
        val response = lagResponse(erSkjermet = true)

        response.getVisningsnavn(tilgangTilBruker = true) shouldBe Triple("Fornavn", null, "Etternavn")
    }
}
