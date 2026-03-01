package no.nav.amt.deltaker.deltaker.db

import no.nav.amt.deltaker.deltaker.DeltakerTestUtils.sammenlignVedtak
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime

class VedtakRepositoryTest {
    private val vedtakRepository = VedtakRepository()
    private val navEnhetRepository = NavEnhetRepository()
    private val navAnsattRepository = NavAnsattRepository()

    private val navEnhet = lagNavEnhet()
    private val navAnsatt = lagNavAnsatt(navEnhetId = navEnhet.id)

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @BeforeEach
    fun setup() {
        navEnhetRepository.upsert(navEnhet)
        navAnsattRepository.upsert(navAnsatt)
    }

    @Test
    fun `upsert - nytt vedtak - inserter`() {
        val deltaker = TestData.lagDeltaker()
        val vedtak: Vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )
        TestRepository.insert(deltaker)

        val upsertedVedtak = vedtakRepository.upsert(vedtak)

        sammenlignVedtak(upsertedVedtak, vedtak)
    }

    @Test
    fun `upsert - oppdatert vedtak - oppdaterer`() {
        val deltaker = TestData.lagDeltaker()
        val vedtak: Vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )
        TestRepository.insert(deltaker)
        vedtakRepository.upsert(vedtak)

        val oppdatertVedtak = vedtak.copy(fattet = LocalDateTime.now())

        val upsertedVedtak = vedtakRepository.upsert(oppdatertVedtak)

        sammenlignVedtak(upsertedVedtak, oppdatertVedtak)
    }

    @Test
    fun `upsert - vedtak fattet av nav - inserter`() {
        val deltaker = TestData.lagDeltaker()
        val vedtak: Vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            fattet = LocalDateTime.now(),
            fattetAvNav = true,
        )
        TestRepository.insert(deltaker)

        val upsertedVedtak = vedtakRepository.upsert(vedtak)

        sammenlignVedtak(upsertedVedtak, vedtak)
    }
}
