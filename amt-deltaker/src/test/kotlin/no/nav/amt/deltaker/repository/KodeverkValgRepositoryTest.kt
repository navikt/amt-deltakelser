package no.nav.amt.deltaker.repository

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class KodeverkValgRepositoryTest {
    private val deltakerlisteRepository = DeltakerlisteRepository()
    private val arrangorRepository = ArrangorRepository()
    private val tiltakRepository = TiltakRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `should save and retrieve kodeverk valg`() {
        val arrangor = lagArrangor()
        arrangorRepository.upsert(arrangor)

        val tiltakstype = lagTiltakstype()
        tiltakRepository.upsert(tiltakstype)

        val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        deltakerlisteRepository.upsert(deltakerliste)

        val valg = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

        KodeverkValgRepository.lagreKodeverkValg(deltakerliste.id, valg)

        val lagret = KodeverkValgRepository.hentKodeverkValg(deltakerliste.id)
        lagret shouldBe valg
    }

    @Test
    fun `should update kodeverk valg on conflict`() {
        val arrangor = lagArrangor()
        arrangorRepository.upsert(arrangor)

        val tiltakstype = lagTiltakstype()
        tiltakRepository.upsert(tiltakstype)

        val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        deltakerlisteRepository.upsert(deltakerliste)

        val opprinneligeValg = listOf(UUID.randomUUID(), UUID.randomUUID())
        KodeverkValgRepository.lagreKodeverkValg(deltakerliste.id, opprinneligeValg)

        val oppdaterteValg = listOf(UUID.randomUUID())
        KodeverkValgRepository.lagreKodeverkValg(deltakerliste.id, oppdaterteValg)

        val lagret = KodeverkValgRepository.hentKodeverkValg(deltakerliste.id)
        lagret shouldBe oppdaterteValg
    }

    @Test
    fun `should return empty list when no valg exists`() {
        val lagret = KodeverkValgRepository.hentKodeverkValg(UUID.randomUUID())
        lagret shouldBe emptyList()
    }
}
