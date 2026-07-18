package no.nav.amt.deltaker.digitalbruker

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.utils.database.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class DigitalBrukerCacheRepositoryTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `hentForPersonidenter - tom input - returnerer tomt map`() {
        DigitalBrukerCacheRepository.hentForPersonidenter(emptySet()) shouldBe emptyMap()
    }

    @Test
    fun `hentForPersonidenter - ingen treff i db - returnerer tomt map`() {
        val resultat = DigitalBrukerCacheRepository.hentForPersonidenter(setOf("12345678901"))
        resultat shouldBe emptyMap()
    }

    @Test
    fun `hentForPersonidenter - ferske entries - returnerer kun matchende personidenter`() {
        DigitalBrukerCacheRepository.upsertBatch(
            listOf(
                "11111111111" to true,
                "22222222222" to false,
                "33333333333" to true,
            ),
        )

        val resultat = DigitalBrukerCacheRepository.hentForPersonidenter(
            setOf("11111111111", "22222222222", "ukjent_99999999999"),
        )

        resultat shouldContainExactly mapOf(
            "11111111111" to true,
            "22222222222" to false,
        )
    }

    @Test
    fun `hentForPersonidenter - entry eldre enn 24 timer - filtreres bort`() {
        DigitalBrukerCacheRepository.upsertBatch(listOf("11111111111" to true))
        gjorEntryUtdatert(hoursAgo = 25)

        DigitalBrukerCacheRepository.upsertBatch(listOf("22222222222" to false))

        val resultat = DigitalBrukerCacheRepository.hentForPersonidenter(
            setOf("11111111111", "22222222222"),
        )

        resultat shouldContainExactly mapOf("22222222222" to false)
    }

    @Test
    fun `hentForPersonidenter - entry akkurat under 24 timer - inkluderes`() {
        DigitalBrukerCacheRepository.upsertBatch(listOf("11111111111" to true))
        gjorEntryUtdatert(hoursAgo = 23)

        val resultat = DigitalBrukerCacheRepository.hentForPersonidenter(setOf("11111111111"))

        resultat shouldContainExactly mapOf("11111111111" to true)
    }

    @Test
    fun `upsertBatch - tom liste - gjor ingenting`() {
        DigitalBrukerCacheRepository.upsertBatch(emptyList())

        DigitalBrukerCacheRepository.hentForPersonidenter(setOf("11111111111")) shouldBe emptyMap()
    }

    @Test
    fun `upsertBatch - nye entries - lagres`() {
        DigitalBrukerCacheRepository.upsertBatch(
            listOf(
                "11111111111" to true,
                "22222222222" to false,
            ),
        )

        val resultat = DigitalBrukerCacheRepository.hentForPersonidenter(
            setOf("11111111111", "22222222222"),
        )

        resultat shouldContainExactly mapOf(
            "11111111111" to true,
            "22222222222" to false,
        )
    }

    @Test
    fun `upsertBatch - eksisterende personident - oppdaterer er_digital og modified_at`() {
        DigitalBrukerCacheRepository.upsertBatch(listOf("11111111111" to false))
        gjorEntryUtdatert(hoursAgo = 25)

        // Verifiser at den er filtrert bort som utdatert
        DigitalBrukerCacheRepository.hentForPersonidenter(setOf("11111111111")) shouldBe emptyMap()

        // Upsert med ny verdi — skal også oppdatere modified_at
        DigitalBrukerCacheRepository.upsertBatch(listOf("11111111111" to true))

        val resultat = DigitalBrukerCacheRepository.hentForPersonidenter(setOf("11111111111"))
        resultat shouldContainExactly mapOf("11111111111" to true)
    }

    private fun gjorEntryUtdatert(hoursAgo: Int) {
        Database.query { session ->
            session.run(
                action = queryOf(
                    "UPDATE digital_bruker_cache SET modified_at = NOW() - make_interval(hours => ?) WHERE personident = ?",
                    hoursAgo,
                    "11111111111",
                ).asUpdate,
            )
        }
    }
}
