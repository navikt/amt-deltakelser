package no.nav.amt.distribusjon.hendelse

import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.distribusjon.journalforing.JournalforingstatusRepository
import no.nav.amt.distribusjon.journalforing.model.Journalforingstatus
import no.nav.amt.distribusjon.utils.TestRepository
import no.nav.amt.distribusjon.utils.data.HendelseTypeData
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.toPGObject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import tools.jackson.databind.node.ObjectNode
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

    @Nested
    inner class HentDistribuerteEnkeltplassHovedvedtakTests {
        @Test
        fun `hentDistribuerteEnkeltplassHovedvedtak - returnerer kun faktisk distribuerte hovedvedtak`() {
            // Arrange
            val deltakerId = UUID.randomUUID()
            val fattetDato = LocalDateTime.now().minusMinutes(45)
            val hovedvedtakForPapir = Hendelsesdata.hendelse(
                payload = HendelseType.EnkeltplassOkonomiGodkjennUtkast(
                    utkast = HendelseTypeData.utkast(),
                    hovedvedtakFattetDato = fattetDato,
                ),
                deltaker = Hendelsesdata.lagDeltaker(id = deltakerId),
                distribusjonskanal = Distribusjonskanal.PRINT,
            )
            val hovedvedtakDigitalt = Hendelsesdata.hendelse(
                payload = HendelseType.EnkeltplassOkonomiGodkjennUtkast(
                    utkast = HendelseTypeData.utkast(),
                    hovedvedtakFattetDato = fattetDato,
                ),
                deltaker = Hendelsesdata.lagDeltaker(id = deltakerId),
                distribusjonskanal = Distribusjonskanal.DITT_NAV,
            )
            val ikkeSendtHovedvedtak = Hendelsesdata.hendelse(
                payload = HendelseType.EnkeltplassOkonomiGodkjennUtkast(
                    utkast = HendelseTypeData.utkast(),
                    hovedvedtakFattetDato = fattetDato,
                ),
                deltaker = Hendelsesdata.lagDeltaker(id = deltakerId),
                distribusjonskanal = Distribusjonskanal.PRINT,
            )
            val annetHovedvedtak = Hendelsesdata.hendelse(
                payload = HendelseTypeData.navGodkjennUtkast(),
                deltaker = Hendelsesdata.lagDeltaker(id = deltakerId),
                distribusjonskanal = Distribusjonskanal.PRINT,
            )

            listOf(
                hovedvedtakForPapir,
                hovedvedtakDigitalt,
                ikkeSendtHovedvedtak,
                annetHovedvedtak,
            ).forEach {
                TestRepository.insertHendelse(it)
            }
            journalforingstatusRepository.upsert(
                Journalforingstatus(hovedvedtakForPapir.id, "papir", UUID.randomUUID(), false, false),
            )
            journalforingstatusRepository.upsert(
                Journalforingstatus(hovedvedtakDigitalt.id, "digitalt", null, false, false),
            )
            journalforingstatusRepository.upsert(
                Journalforingstatus(ikkeSendtHovedvedtak.id, "ikke-sendt", null, false, false),
            )
            journalforingstatusRepository.upsert(
                Journalforingstatus(annetHovedvedtak.id, "annet-hovedvedtak", UUID.randomUUID(), false, false),
            )

            // Act
            val result = hendelseRepository.hentDistribuerteEnkeltplassHovedvedtak(setOf(deltakerId))

            // Assert
            val returnerteHovedvedtakIder = result.map { it.hendelse.id }.toSet()
            val hovedvedtakForPapirPayload = result
                .first { it.hendelse.id == hovedvedtakForPapir.id }
                .hendelse.payload as HendelseType.EnkeltplassOkonomiGodkjennUtkast

            returnerteHovedvedtakIder shouldBe setOf(
                hovedvedtakForPapir.id,
                hovedvedtakDigitalt.id,
            )
            hovedvedtakForPapirPayload.hovedvedtakFattetDato shouldBe fattetDato
        }
    }

    @Test
    fun `annuller - gjør hendelser utilgjengelige for journalføring og distribusjon`() {
        // Arrange
        val ikkeJournalfort = Hendelsesdata.hendelse(HendelseTypeData.forlengDeltakelse())
        val ikkeDistribuert = Hendelsesdata.hendelse(
            payload = HendelseTypeData.forlengDeltakelse(),
            distribusjonskanal = Distribusjonskanal.PRINT,
        )
        TestRepository.insertHendelse(ikkeJournalfort)
        TestRepository.insertHendelse(ikkeDistribuert)
        val ikkeJournalfortStatus = Journalforingstatus(
            hendelseId = ikkeJournalfort.id,
            journalpostId = null,
            bestillingsId = null,
            kanIkkeDistribueres = false,
            kanIkkeJournalfores = false,
        )
        val ikkeDistribuertStatus = Journalforingstatus(
            hendelseId = ikkeDistribuert.id,
            journalpostId = "journalpost",
            bestillingsId = null,
            kanIkkeDistribueres = false,
            kanIkkeJournalfores = false,
        )
        journalforingstatusRepository.upsert(ikkeJournalfortStatus)
        journalforingstatusRepository.upsert(ikkeDistribuertStatus)

        // Act
        val antallAnnullert = journalforingstatusRepository.annuller(listOf(ikkeJournalfort.id, ikkeDistribuert.id))
        val statusIkkeJournalfort = journalforingstatusRepository.get(ikkeJournalfort.id)
        val statusIkkeDistribuert = journalforingstatusRepository.get(ikkeDistribuert.id)
        val ikkeJournalforteHendelseIder = hendelseRepository.hentIkkeJournalforteHendelser().map { it.hendelse.id }
        val ikkeDistribuerteHendelseIder =
            hendelseRepository.hentHendelserSomSkalDistribueresSomBrev().map { it.hendelse.id }

        // Assert
        antallAnnullert shouldBe 2
        statusIkkeJournalfort?.annullert shouldBe true
        statusIkkeDistribuert?.annullert shouldBe true
        ikkeJournalforteHendelseIder.contains(ikkeJournalfort.id) shouldBe false
        ikkeDistribuerteHendelseIder.contains(ikkeDistribuert.id) shouldBe false

        // Act
        journalforingstatusRepository.upsert(ikkeJournalfortStatus)
        val statusEtterUpsert = journalforingstatusRepository.get(ikkeJournalfort.id)

        // Assert
        statusEtterUpsert?.annullert shouldBe true
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

    @Test
    fun `getHendelser - deltaker med legacy overordnetArrangor i json - leses uten feil`() {
        // Arrange
        val hendelse = Hendelsesdata.hendelse(HendelseTypeData.opprettUtkast())
        val deltakerNode = objectMapper.valueToTree<ObjectNode>(hendelse.deltaker)
        val deltakerlisteNode = deltakerNode.get("deltakerliste") as ObjectNode
        val arrangorNode = deltakerlisteNode.get("arrangor") as ObjectNode

        arrangorNode.putObject("overordnetArrangor").apply {
            put("id", UUID.randomUUID().toString())
            put("organisasjonsnummer", "999999999")
            put("navn", "Overordnet Arrangør")
        }

        val sql =
            """
            INSERT INTO hendelse (
                id,
                deltaker_id,
                deltaker,
                ansvarlig,
                payload,
                distribusjonskanal,
                manuelloppfolging,
                created_at
            )
            VALUES (
                :id,
                :deltaker_id,
                :deltaker,
                :ansvarlig,
                :payload,
                :distribusjonskanal,
                :manuelloppfolging,
                :created_at
            )
            """.trimIndent()

        val params = mapOf(
            "id" to hendelse.id,
            "deltaker_id" to hendelse.deltaker.id,
            "deltaker" to objectMapper.toPGObject(deltakerNode),
            "ansvarlig" to objectMapper.toPGObject(hendelse.ansvarlig),
            "payload" to objectMapper.toPGObject(hendelse.payload),
            "distribusjonskanal" to hendelse.distribusjonskanal.name,
            "manuelloppfolging" to hendelse.manuellOppfolging,
            "created_at" to hendelse.opprettet,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }

        // Act
        val hendelser = hendelseRepository.getHendelser(listOf(hendelse.id))

        // Assert
        hendelser.size shouldBe 1
        hendelser.first().id shouldBe hendelse.id
        hendelser.first().deltaker.id shouldBe hendelse.deltaker.id
    }
}
