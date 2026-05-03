package no.nav.amt.distribusjon.hendelse

import io.kotest.matchers.shouldBe
import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.distribusjon.journalforing.JournalforingstatusRepository
import no.nav.amt.distribusjon.journalforing.model.Journalforingstatus
import no.nav.amt.distribusjon.utils.TestRepository
import no.nav.amt.distribusjon.utils.data.HendelseTypeData
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class HendelseRepositoryTest {
    private val hendelseRepository = HendelseRepository()
    private val journalforingstatusRepository = JournalforingstatusRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `insert - inserter hendelse i database`() {
        // Arrange
        val hendelse = Hendelsesdata.hendelse(
            payload = HendelseTypeData.forlengDeltakelse(),
        )

        // Act
        hendelseRepository.insert(hendelse)

        // Assert
        val hendelser = hendelseRepository.getHendelser(listOf(hendelse.id))
        hendelser.size shouldBe 1
        hendelser.first().copy(opprettet = hendelse.opprettet) shouldBe hendelse
    }

    @Nested
    inner class HentIkkeJournalforteHendelserTests {
        @Test
        fun `hentIkkeJournalforteHendelser - hendelse er ikke journalfort - returnerer hendelse`() {
            // Arrange
            val hendelse = Hendelsesdata.hendelse(
                payload = HendelseTypeData.forlengDeltakelse(),
                opprettet = LocalDateTime.now().minusHours(1),
            )
            TestRepository.insertHendelse(hendelse)

            journalforingstatusRepository.upsert(
                Journalforingstatus(
                    hendelseId = hendelse.id,
                    journalpostId = null,
                    bestillingsId = null,
                    kanIkkeDistribueres = false,
                    kanIkkeJournalfores = false,
                ),
            )

            // Act
            val ikkeJournalforteHendelser = hendelseRepository.hentIkkeJournalforteHendelser()

            // Assert
            ikkeJournalforteHendelser.size shouldBe 1
            ikkeJournalforteHendelser.first().hendelse.id shouldBe hendelse.id
        }

        @Test
        fun `hentIkkeJournalforteHendelser - hendelse kan ikke journalfores - returnerer tom liste`() {
            // Arrange
            val hendelse = Hendelsesdata.hendelse(HendelseTypeData.forlengDeltakelse(), opprettet = LocalDateTime.now().minusHours(1))
            TestRepository.insertHendelse(hendelse)
            journalforingstatusRepository.upsert(
                Journalforingstatus(
                    hendelseId = hendelse.id,
                    journalpostId = null,
                    bestillingsId = null,
                    kanIkkeDistribueres = false,
                    kanIkkeJournalfores = true,
                ),
            )

            // Act
            val ikkeJournalforteHendelser = hendelseRepository.hentIkkeJournalforteHendelser()

            // Assert
            ikkeJournalforteHendelser.size shouldBe 0
        }

        @Test
        fun `hentIkkeJournalforteHendelser - hendelse er journalfort og skal ikke sendes brev - returnerer tom liste`() {
            // Arrange
            val hendelse = Hendelsesdata.hendelse(
                payload = HendelseTypeData.forlengDeltakelse(),
                opprettet = LocalDateTime.now().minusHours(1),
            )
            TestRepository.insertHendelse(hendelse)

            journalforingstatusRepository.upsert(
                Journalforingstatus(
                    hendelseId = hendelse.id,
                    journalpostId = "12345",
                    bestillingsId = null,
                    kanIkkeDistribueres = false,
                    kanIkkeJournalfores = false,
                ),
            )

            // Act
            val ikkeJournalforteHendelser = hendelseRepository.hentIkkeJournalforteHendelser()

            // Assert
            ikkeJournalforteHendelser.size shouldBe 0
        }

        @Test
        fun `hentIkkeJournalforteHendelser - journalforingstatus finnes ikke - returnerer tom liste`() {
            // Arrange
            val hendelse = Hendelsesdata.hendelse(
                payload = HendelseTypeData.forlengDeltakelse(),
                opprettet = LocalDateTime.now().minusHours(1),
            )
            TestRepository.insertHendelse(hendelse)

            // Act
            val ikkeJournalforteHendelser = hendelseRepository.hentIkkeJournalforteHendelser()

            // Assert
            ikkeJournalforteHendelser.size shouldBe 0
        }
    }

    @Nested
    inner class HentHendelserSomSkalDistribueresSomBrevTests {
        @Test
        fun `hentHendelserSomSkalDistribueresSomBrev - hendelse er ikke distribuert - returnerer hendelse`() {
            // Arrange
            val hendelse = Hendelsesdata.hendelse(
                payload = HendelseTypeData.forlengDeltakelse(),
                opprettet = LocalDateTime.now().minusHours(1),
                distribusjonskanal = Distribusjonskanal.PRINT,
            )
            TestRepository.insertHendelse(hendelse)

            journalforingstatusRepository.upsert(
                Journalforingstatus(
                    hendelseId = hendelse.id,
                    journalpostId = "test",
                    bestillingsId = null,
                    kanIkkeDistribueres = false,
                    kanIkkeJournalfores = false,
                ),
            )

            // Act
            val ikkeDistribuerteHendelser = hendelseRepository.hentHendelserSomSkalDistribueresSomBrev()

            // Assert
            ikkeDistribuerteHendelser.size shouldBe 1
            ikkeDistribuerteHendelser.first().hendelse.id shouldBe hendelse.id
        }

        @Test
        fun `hentHendelserSomSkalDistribueresSomBrev - hendelse er journalfort, kan ikke distribueres - returnerer tom liste`() {
            // Arrange
            val hendelse = Hendelsesdata.hendelse(
                HendelseTypeData.forlengDeltakelse(),
                opprettet = LocalDateTime.now().minusHours(1),
                distribusjonskanal = Distribusjonskanal.PRINT,
            )
            TestRepository.insertHendelse(hendelse)

            journalforingstatusRepository.upsert(
                Journalforingstatus(
                    hendelseId = hendelse.id,
                    journalpostId = "12345",
                    bestillingsId = null,
                    kanIkkeDistribueres = true,
                    kanIkkeJournalfores = false,
                ),
            )

            // Act
            val ikkeDistribuerteHendelser = hendelseRepository.hentHendelserSomSkalDistribueresSomBrev()

            // Assert
            ikkeDistribuerteHendelser.size shouldBe 0
        }

        @Test
        fun `hentHendelserSomSkalDistribueresSomBrev - hendelse er journalfort, brev skal sendes, er ikke sendt - returnerer hendelse`() {
            // Arrange
            val hendelse = Hendelsesdata.hendelse(
                payload = HendelseTypeData.forlengDeltakelse(),
                opprettet = LocalDateTime.now().minusHours(1),
                distribusjonskanal = Distribusjonskanal.PRINT,
            )
            TestRepository.insertHendelse(hendelse)

            journalforingstatusRepository.upsert(
                Journalforingstatus(
                    hendelseId = hendelse.id,
                    journalpostId = "12345",
                    bestillingsId = null,
                    kanIkkeDistribueres = false,
                    kanIkkeJournalfores = false,
                ),
            )

            // Act
            val ikkeDistribuerteHendelser = hendelseRepository.hentHendelserSomSkalDistribueresSomBrev()

            // Assert
            ikkeDistribuerteHendelser.size shouldBe 1
            ikkeDistribuerteHendelser.first().hendelse.id shouldBe hendelse.id
        }

        @Test
        fun `hentHendelserSomSkalDistribueresSomBrev - hendelse er journalfort og brev er sendt - returnerer tom liste`() {
            // Arrange
            val hendelse = Hendelsesdata.hendelse(
                payload = HendelseTypeData.forlengDeltakelse(),
                opprettet = LocalDateTime.now().minusHours(1),
            )
            TestRepository.insertHendelse(hendelse)

            journalforingstatusRepository.upsert(
                Journalforingstatus(
                    hendelseId = hendelse.id,
                    journalpostId = "12345",
                    bestillingsId = UUID.randomUUID(),
                    kanIkkeDistribueres = false,
                    kanIkkeJournalfores = false,
                ),
            )

            // Act
            val ikkeDistribuerteHendelser = hendelseRepository.hentHendelserSomSkalDistribueresSomBrev()

            // Assert
            ikkeDistribuerteHendelser.size shouldBe 0
        }
    }

    @Test
    fun `getHendelser - skal returnere hendelser`() {
        // Arrange
        val hendelse = Hendelsesdata.hendelse(HendelseTypeData.opprettUtkast())
        TestRepository.insertHendelse(hendelse)

        // Act
        val hendelser = hendelseRepository.getHendelser(listOf(hendelse.id))

        // Assert
        hendelser.size shouldBe 1
    }
}
