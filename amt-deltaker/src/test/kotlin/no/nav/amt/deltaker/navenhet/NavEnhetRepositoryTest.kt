package no.nav.amt.deltaker.navenhet

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class NavEnhetRepositoryTest {
    private val navEnhetRepository = NavEnhetRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `upsert - ny nav enhet - inserter`() {
        val navEnhet = lagNavEnhet()

        val result = navEnhetRepository.upsert(navEnhet)

        result shouldBe navEnhet
        navEnhetRepository.get(navEnhet.id) shouldBe navEnhet
    }

    @Test
    fun `upsert - eksisterende nav enhet - oppdaterer`() {
        val navEnhet = lagNavEnhet()
        navEnhetRepository.upsert(navEnhet)

        val oppdatertNavEnhet = navEnhet.copy(
            navn = "Oppdatert Nav-enhet",
            enhetsnummer = "9999",
        )
        val result = navEnhetRepository.upsert(oppdatertNavEnhet)

        result shouldBe oppdatertNavEnhet
        navEnhetRepository.get(navEnhet.id) shouldBe oppdatertNavEnhet
    }

    @Test
    fun `get by enhetsnummer - eksisterende enhet - returnerer enhet`() {
        val navEnhet = lagNavEnhet(enhetsnummer = "1234", navn = "NAV Test")
        navEnhetRepository.upsert(navEnhet)

        val result = navEnhetRepository.get(navEnhet.enhetsnummer)

        result shouldBe navEnhet
    }

    @Test
    fun `get by enhetsnummer - ikke eksisterende enhet - returnerer null`() {
        val result = navEnhetRepository.get("9999")

        result shouldBe null
    }

    @Test
    fun `get by id - eksisterende enhet - returnerer enhet`() {
        val navEnhet = lagNavEnhet()
        navEnhetRepository.upsert(navEnhet)

        val result = navEnhetRepository.get(navEnhet.id)

        result shouldBe navEnhet
    }

    @Test
    fun `get by id - ikke eksisterende enhet - returnerer null`() {
        val result = navEnhetRepository.get(UUID.randomUUID())

        result shouldBe null
    }

    @Nested
    inner class GetManyTests {
        @Test
        fun `getMany - flere nav enheter - returnerer alle enheter`() {
            val navEnheter = listOf(
                lagNavEnhet(enhetsnummer = "1111", navn = "Nav En"),
                lagNavEnhet(enhetsnummer = "2222", navn = "Nav To"),
                lagNavEnhet(enhetsnummer = "3333", navn = "Nav Tre"),
            )
            navEnheter.forEach { navEnhetRepository.upsert(it) }

            val result = navEnhetRepository.getMany(navEnheter.map { it.id }.toSet())

            result.size shouldBe navEnheter.size
            result.toSet() shouldBe navEnheter.toSet()
        }

        @Test
        fun `getMany - delvis eksisterende enheter - returnerer kun eksisterende`() {
            val eksisterendeNavEnhet = lagNavEnhet(enhetsnummer = "1234", navn = "Nav Eksisterende")
            navEnhetRepository.upsert(eksisterendeNavEnhet)
            val ikkeEksisterendeId = UUID.randomUUID()

            val result = navEnhetRepository.getMany(setOf(eksisterendeNavEnhet.id, ikkeEksisterendeId))

            result.size shouldBe 1
            result[0] shouldBe eksisterendeNavEnhet
        }

        @Test
        fun `getMany - tom liste - returnerer tom liste`() {
            val result = navEnhetRepository.getMany(emptySet())

            result.size shouldBe 0
        }

        @Test
        fun `getMany - ingen eksisterende enheter - returnerer tom liste`() {
            val ikkeEksisterendeIder = setOf(UUID.randomUUID(), UUID.randomUUID())

            val result = navEnhetRepository.getMany(ikkeEksisterendeIder)

            result.size shouldBe 0
        }
    }
}
