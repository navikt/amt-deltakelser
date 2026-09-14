package no.nav.amt.deltaker.tiltaksarrangor

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
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
    fun `getFunksjonellArrangorForGjennomforing - gruppe uten overordnet arrangor - returnerer eget navn`() {
        val arrangor = lagreArrangor(navn = "Test Arrangør")
        val gjennomforing = lagDeltakerliste(
            arrangor = arrangor,
            gjennomforingstype = GjennomforingType.Gruppe,
        )

        val funksjonellArrangor = arrangorService.getFunksjonellArrangorForGjennomforing(gjennomforing)
        funksjonellArrangor.id shouldBe arrangor.id
        funksjonellArrangor.navn shouldBe "Test Arrangør"
    }

    @Test
    fun `getFunksjonellArrangorForGjennomforing - gruppe med overordnet arrangor - returnerer overordnet navn`() {
        val overordnetArrangor = lagreArrangor(navn = "TEST ARRANGØR")
        val underordnetArrangor = lagreArrangor(navn = "Underordnet arrangør", overordnetArrangorId = overordnetArrangor.id)
        val gjennomforing = lagDeltakerliste(
            arrangor = underordnetArrangor,
            gjennomforingstype = GjennomforingType.Gruppe,
        )

        val funksjonellArrangor = arrangorService.getFunksjonellArrangorForGjennomforing(gjennomforing)
        funksjonellArrangor.id shouldBe overordnetArrangor.id
        funksjonellArrangor.navn shouldBe "Test Arrangør"
    }

    @Test
    fun `getFunksjonellArrangorForGjennomforing - gruppe med CAPS overordnet arrangor - formaterer navn`() {
        val overordnetArrangor = lagreArrangor(navn = "TEST ARRANGØR")
        val underordnetArrangor = lagreArrangor(navn = "UNDERORDNET ARRANGØR", overordnetArrangorId = overordnetArrangor.id)
        val gjennomforing = lagDeltakerliste(
            arrangor = underordnetArrangor,
            gjennomforingstype = GjennomforingType.Gruppe,
        )

        arrangorService.getFunksjonellArrangorForGjennomforing(gjennomforing).navn shouldBe "Test Arrangør"
    }

    @Test
    fun `getFunksjonellArrangorForGjennomforing - enkeltplass med overordnet arrangor - returnerer underenhetens navn`() {
        val overordnetArrangor = lagreArrangor(navn = "Overordnet Arrangør")
        val underordnetArrangor = lagreArrangor(navn = "Underenhet Oslo", overordnetArrangorId = overordnetArrangor.id)
        val gjennomforing = lagDeltakerliste(
            arrangor = underordnetArrangor,
            gjennomforingstype = GjennomforingType.Enkeltplass,
        )

        val funksjonellArrangor = arrangorService.getFunksjonellArrangorForGjennomforing(gjennomforing)
        funksjonellArrangor.id shouldBe underordnetArrangor.id
        funksjonellArrangor.navn shouldBe "Underenhet Oslo"
    }

    @Test
    fun `getFunksjonellArrangorForGjennomforing - enkeltplass med CAPS-navn - formaterer underenhetens navn`() {
        val arrangor = lagreArrangor(navn = "UNDERENHET OSLO AS")
        val gjennomforing = lagDeltakerliste(
            arrangor = arrangor,
            gjennomforingstype = GjennomforingType.Enkeltplass,
        )

        arrangorService.getFunksjonellArrangorForGjennomforing(gjennomforing).navn shouldBe "Underenhet Oslo AS"
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
