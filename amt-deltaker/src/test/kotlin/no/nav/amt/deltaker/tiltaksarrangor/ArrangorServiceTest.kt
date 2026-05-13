package no.nav.amt.deltaker.tiltaksarrangor

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.randomOrgnr
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class ArrangorServiceTest {
    private val arrangorRepository = ArrangorRepository()
    private val amtArrangorClient = mockk<AmtArrangorClient>()
    val arrangorService = ArrangorService(arrangorRepository, amtArrangorClient)

    @Test
    fun `getArrangorNavn - overordnet arrangør - returnerer eget navn`() {
        val arrangor = lagreArrangor(navn = "Test Arrangør")

        arrangorService.getArrangorNavn(
            arrangor = arrangor,
            gjennomforingstype = GjennomforingType.Gruppe,
        ) shouldBe "Test Arrangør"
    }

    @Test
    fun `getArrangorNavn - underordnet arrangør - returnerer overordnet arrangør navn`() {
        val overordnetArrangor = lagreArrangor(navn = "Test Arrangør")
        val underordnetArrangor = lagreArrangor(navn = "Underordnet arrangør", overordnetArrangorId = overordnetArrangor.id)

        arrangorService.getArrangorNavn(
            arrangor = underordnetArrangor,
            gjennomforingstype = GjennomforingType.Gruppe,
        ) shouldBe "Test Arrangør"
    }

    @Test
    fun `getArrangorNavn - CAPS - formaterer navn`() {
        val arrangor = lagreArrangor(navn = "TEST ARRANGØR")

        arrangorService.getArrangorNavn(
            arrangor = arrangor,
            gjennomforingstype = GjennomforingType.Gruppe,
        ) shouldBe "Test Arrangør"
    }

    @Test
    fun `getArrangorNavn - Enkeltplass med overordnet arrangør - returnerer underenhetens navn`() {
        val overordnetArrangor = lagreArrangor(navn = "Overordnet Arrangør")
        val underordnetArrangor = lagreArrangor(navn = "Underenhet Oslo", overordnetArrangorId = overordnetArrangor.id)

        arrangorService.getArrangorNavn(
            arrangor = underordnetArrangor,
            gjennomforingstype = GjennomforingType.Enkeltplass,
        ) shouldBe "Underenhet Oslo"
    }

    @Test
    fun `getArrangorNavn - Enkeltplass med CAPS-navn - formaterer underenhetens navn`() {
        val arrangor = lagreArrangor(navn = "UNDERENHET OSLO AS")

        arrangorService.getArrangorNavn(
            arrangor = arrangor,
            gjennomforingstype = GjennomforingType.Enkeltplass,
        ) shouldBe "Underenhet Oslo AS"
    }

    private fun lagreArrangor(
        navn: String,
        overordnetArrangorId: UUID? = null,
    ): Arrangor = lagArrangor(navn, overordnetArrangorId)
        .also { arrangorRepository.upsert(it) }

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        private fun lagArrangor(
            navn: String,
            overordnetArrangorId: UUID? = null,
        ) = Arrangor(
            id = UUID.randomUUID(),
            navn = navn,
            organisasjonsnummer = randomOrgnr(),
            overordnetArrangorId = overordnetArrangorId,
        )
    }
}
