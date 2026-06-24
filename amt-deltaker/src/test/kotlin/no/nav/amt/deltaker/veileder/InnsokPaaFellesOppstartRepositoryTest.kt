package no.nav.amt.deltaker.veileder

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.service.DeltakerContext
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.Innsok
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class InnsokPaaFellesOppstartRepositoryTest {
    private val innsokRepository = InnsokRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `insert - ny innsok - inserter`() {
        with(DeltakerContext()) {
            medVedtak()
            val innsok = TestData.lagInnsok(deltaker)
            innsokRepository.insert(innsok)
            innsokRepository.get(innsok.id).isSuccess shouldBe true
        }
    }
}

fun TestData.lagInnsok(
    deltaker: Deltaker = lagDeltaker(),
    id: UUID = UUID.randomUUID(),
    innsokt: LocalDateTime = LocalDateTime.now(),
    innsoktAv: UUID = deltaker.vedtaksinformasjon!!.sistEndretAv,
    innsoktAvEnhet: UUID = deltaker.vedtaksinformasjon!!.sistEndretAvEnhet,
    utkastGodkjentAvNav: Boolean = false,
    utkastDelt: LocalDateTime? = LocalDateTime.now(),
    deltakelsesinnholdVedInnsok: Deltakelsesinnhold? = deltaker.deltakelsesinnhold,
) = Innsok(
    id = id,
    deltakerId = deltaker.id,
    innsokt = innsokt,
    innsoktAv = innsoktAv,
    innsoktAvEnhet = innsoktAvEnhet,
    deltakelsesinnholdVedInnsok = deltakelsesinnholdVedInnsok,
    utkastDelt = utkastDelt,
    utkastGodkjentAvNav = utkastGodkjentAvNav,
)
