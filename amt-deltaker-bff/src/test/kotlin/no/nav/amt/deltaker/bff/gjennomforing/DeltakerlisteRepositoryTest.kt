package no.nav.amt.deltaker.bff.gjennomforing

import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.bff.tiltak.TiltakRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.ArrangorRepository
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerliste
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class DeltakerlisteRepositoryTest {
    private val deltakerlisteRepository = DeltakerlisteRepository()
    private val arrangorRepository = ArrangorRepository()
    private val tiltakRepository = TiltakRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class Upsert {
        @Test
        fun `ny deltakerliste - inserter`() {
            val arrangor = lagArrangor()
            arrangorRepository.upsert(arrangor)

            val tiltakstype = TestData.lagTiltakstype()
            val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
            tiltakRepository.upsert(TestData.tiltakAv(tiltakstype))

            deltakerlisteRepository.upsert(deltakerliste, tiltakstype.id)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe deltakerliste
        }

        @Test
        fun `deltakerliste ny sluttdato - oppdaterer`() {
            val arrangor = lagArrangor()
            arrangorRepository.upsert(arrangor)

            val tiltakstype = TestData.lagTiltakstype()
            val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
            tiltakRepository.upsert(TestData.tiltakAv(tiltakstype))

            deltakerlisteRepository.upsert(deltakerliste, tiltakstype.id)

            val oppdatertListe = deltakerliste.copy(sluttDato = LocalDate.now())

            deltakerlisteRepository.upsert(oppdatertListe, tiltakstype.id)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe oppdatertListe
        }
    }

    @Test
    fun `delete - sletter deltakerliste`() {
        val arrangor = lagArrangor()
        arrangorRepository.upsert(arrangor)

        val tiltakstype = TestData.lagTiltakstype()
        val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        tiltakRepository.upsert(TestData.tiltakAv(tiltakstype))
        deltakerlisteRepository.upsert(deltakerliste, tiltakstype.id)

        deltakerlisteRepository.delete(deltakerliste.id)

        deltakerlisteRepository.get(deltakerliste.id).shouldBeFailure<NoSuchElementException>()
    }

    @Test
    fun `get - deltakerliste og arrangor finnes - henter deltakerliste`() {
        val overordnetArrangor = lagArrangor()
        arrangorRepository.upsert(overordnetArrangor)

        val arrangor = lagArrangor(overordnetArrangorId = overordnetArrangor.id)
        arrangorRepository.upsert(arrangor)

        val tiltakstype = TestData.lagTiltakstype()
        val deltakerliste = lagDeltakerliste(arrangor = arrangor, overordnetArrangor = overordnetArrangor, tiltakstype = tiltakstype)
        tiltakRepository.upsert(TestData.tiltakAv(tiltakstype))
        deltakerlisteRepository.upsert(deltakerliste, tiltakstype.id)

        val deltakerlisteMedArrangor = deltakerlisteRepository.get(deltakerliste.id).getOrThrow()

        deltakerlisteMedArrangor shouldNotBe null
        deltakerlisteMedArrangor.arrangor.navn shouldBe arrangor.navn
        deltakerlisteMedArrangor.arrangor.overordnetArrangorId shouldBe overordnetArrangor.id
    }
}
