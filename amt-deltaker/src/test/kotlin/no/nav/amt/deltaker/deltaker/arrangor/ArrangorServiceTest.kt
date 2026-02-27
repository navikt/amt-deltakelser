package no.nav.amt.deltaker.deltaker.arrangor

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class ArrangorServiceTest {
    private val arrangorRepository = ArrangorRepository()
    private val amtArrangorClient = mockk<AmtArrangorClient>()
    val arrangorService = ArrangorService(arrangorRepository, amtArrangorClient)

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `getArrangorNavn - overordnet arrangør - returnerer eget navn`() {
        val arrangorNavn = "Test Arrangør"
        val arrangor = Arrangor(
            id = UUID.randomUUID(),
            navn = arrangorNavn,
            organisasjonsnummer = "123456789",
            overordnetArrangorId = null,
        )
        arrangorRepository.upsert(arrangor)
        arrangorService.getArrangorNavn(arrangor) shouldBe arrangorNavn
    }

    @Test
    fun `getArrangorNavn - underordnet arrangør - returnerer overordnet arrangør navn`() {
        val arrangorNavn = "Test Arrangør"
        val overordnetArrangor = Arrangor(
            id = UUID.randomUUID(),
            navn = arrangorNavn,
            organisasjonsnummer = "123456789",
            overordnetArrangorId = null,
        )
        val underordnetArrangor = Arrangor(
            id = UUID.randomUUID(),
            navn = "Underordnet arrangør",
            organisasjonsnummer = "1234567892",
            overordnetArrangorId = overordnetArrangor.id,
        )
        arrangorRepository.upsert(overordnetArrangor)
        arrangorRepository.upsert(underordnetArrangor)

        arrangorService.getArrangorNavn(underordnetArrangor) shouldBe arrangorNavn
    }

    @Test
    fun `getArrangorNavn - CAPS - formaterer navn`() {
        val arrangorNavn = "TEST ARRANGØR"
        val arrangor = Arrangor(
            id = UUID.randomUUID(),
            navn = arrangorNavn,
            organisasjonsnummer = "123456789",
            overordnetArrangorId = null,
        )
        arrangorRepository.upsert(arrangor)
        arrangorService.getArrangorNavn(arrangor) shouldBe "Test Arrangør"
    }
}
