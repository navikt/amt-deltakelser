package no.nav.amt.deltaker.repository

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class KodeverkValgRepositoryTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `skal lagre og hente kodeverk valg`() {
        val deltakerliste = lagDeltakerliste()
        TestRepository.insert(deltakerliste)

        val valg = setOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

        KodeverkValgRepository.lagreKodeverkValg(deltakerliste.id, valg)

        val lagret = KodeverkValgRepository.hentKodeverkValg(deltakerliste.id)
        lagret shouldBe valg
    }

    @Test
    fun `skal oppdatere kodeverk valg on conflict`() {
        val deltakerliste = lagDeltakerliste()
        TestRepository.insert(deltakerliste)

        val opprinneligeValg = setOf(UUID.randomUUID(), UUID.randomUUID())
        KodeverkValgRepository.lagreKodeverkValg(deltakerliste.id, opprinneligeValg)

        val oppdaterteValg = setOf(UUID.randomUUID())
        KodeverkValgRepository.lagreKodeverkValg(deltakerliste.id, oppdaterteValg)

        val lagret = KodeverkValgRepository.hentKodeverkValg(deltakerliste.id)
        lagret shouldBe oppdaterteValg
    }

    @Test
    fun `skal returnere empty set nar ingen valg i db`() {
        val lagret = KodeverkValgRepository.hentKodeverkValg(UUID.randomUUID())
        lagret shouldBe emptySet()
    }

    @Test
    fun `skal slette kodeverk valg`() {
        val deltakerliste = lagDeltakerliste()
        TestRepository.insert(deltakerliste)

        val valg = setOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

        KodeverkValgRepository.lagreKodeverkValg(deltakerliste.id, valg)

        val lagret = KodeverkValgRepository.hentKodeverkValg(deltakerliste.id)
        lagret shouldBe valg

        KodeverkValgRepository.deleteForGjennomforing(deltakerliste.id)
        val slettet = KodeverkValgRepository.hentKodeverkValg(deltakerliste.id)
        slettet shouldBe emptySet()
    }
}
