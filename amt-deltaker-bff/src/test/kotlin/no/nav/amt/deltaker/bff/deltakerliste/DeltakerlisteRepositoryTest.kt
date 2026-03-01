package no.nav.amt.deltaker.bff.deltakerliste

import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.bff.arrangor.ArrangorRepository
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.bff.utils.data.TestData.lagTiltakstype
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class DeltakerlisteRepositoryTest {
    private val deltakerlisteRepository = DeltakerlisteRepository()
    private val arrangorRepository = ArrangorRepository()
    private val tiltakstypeRepository = TiltakstypeRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class Upsert {
        @Test
        fun `ny minimal deltakerliste - inserter`() {
            val arrangor = lagArrangor()
            arrangorRepository.upsert(arrangor)

            val tiltakstype = lagTiltakstype()
            tiltakstypeRepository.upsert(tiltakstype)

            val deltakerliste = lagDeltakerliste(
                arrangor = arrangor,
                tiltakstype = tiltakstype,
            ).copy(
                status = null,
                startDato = null,
                sluttDato = null,
                oppstart = null,
                antallPlasser = null,
            )

            deltakerlisteRepository.upsert(deltakerliste)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe deltakerliste
        }

        @Test
        fun `ny deltakerliste - inserter`() {
            val arrangor = lagArrangor()
            arrangorRepository.upsert(arrangor)

            val deltakerliste = lagDeltakerliste(arrangor = arrangor)
            tiltakstypeRepository.upsert(deltakerliste.tiltak)

            deltakerlisteRepository.upsert(deltakerliste)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe deltakerliste
        }

        @Test
        fun `deltakerliste ny sluttdato - oppdaterer`() {
            val arrangor = lagArrangor()
            arrangorRepository.upsert(arrangor)

            val deltakerliste = lagDeltakerliste(arrangor = arrangor)
            tiltakstypeRepository.upsert(deltakerliste.tiltak)

            deltakerlisteRepository.upsert(deltakerliste)

            val oppdatertListe = deltakerliste.copy(sluttDato = LocalDate.now())

            deltakerlisteRepository.upsert(oppdatertListe)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe oppdatertListe
        }
    }

    @Test
    fun `delete - sletter deltakerliste`() {
        val arrangor = lagArrangor()
        arrangorRepository.upsert(arrangor)

        val deltakerliste = lagDeltakerliste(arrangor = arrangor)
        tiltakstypeRepository.upsert(deltakerliste.tiltak)
        deltakerlisteRepository.upsert(deltakerliste)

        deltakerlisteRepository.delete(deltakerliste.id)

        deltakerlisteRepository.get(deltakerliste.id).shouldBeFailure<NoSuchElementException>()
    }

    @Test
    fun `get - deltakerliste og arrangor finnes - henter deltakerliste`() {
        val overordnetArrangor = lagArrangor()
        arrangorRepository.upsert(overordnetArrangor)

        val arrangor = lagArrangor(overordnetArrangorId = overordnetArrangor.id)
        arrangorRepository.upsert(arrangor)

        val deltakerliste = lagDeltakerliste(arrangor = arrangor, overordnetArrangor = overordnetArrangor)
        tiltakstypeRepository.upsert(deltakerliste.tiltak)
        deltakerlisteRepository.upsert(deltakerliste)

        val deltakerlisteMedArrangor = deltakerlisteRepository.get(deltakerliste.id).getOrThrow()

        deltakerlisteMedArrangor shouldNotBe null
        deltakerlisteMedArrangor.navn shouldBe deltakerliste.navn
        deltakerlisteMedArrangor.arrangor.arrangor.navn shouldBe arrangor.navn
        deltakerlisteMedArrangor.arrangor.overordnetArrangorNavn shouldBe overordnetArrangor.navn
    }
}
