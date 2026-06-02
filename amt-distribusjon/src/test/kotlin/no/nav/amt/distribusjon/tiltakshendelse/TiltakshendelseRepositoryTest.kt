package no.nav.amt.distribusjon.tiltakshendelse

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.distribusjon.IntegrationTestBase
import no.nav.amt.distribusjon.tiltakshendelse.model.Tiltakshendelse
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class TiltakshendelseRepositoryTest : IntegrationTestBase() {
    @Nested
    inner class UpsertTests {
        @Test
        fun `upsert - utkast med samme id oppdaterer eksisterende rad`() {
            // Arrange
            val id = UUID.randomUUID()
            val original = tiltakshendelse(
                id = id,
                forslagId = null,
                hendelser = listOf(UUID.randomUUID()),
                tekst = "Utkast",
                type = Tiltakshendelse.Type.UTKAST,
            )

            tiltakshendelseRepository.upsert(original)

            val oppdatert = original.copy(
                hendelser = original.hendelser + UUID.randomUUID(),
                aktiv = false,
                tekst = "Utkast stoppet",
            )

            // Act
            val lagret = tiltakshendelseRepository.upsert(oppdatert)

            // Assert
            lagret.id shouldBe id
            val fraDb = tiltakshendelseRepository.get(id).shouldBeSuccess()
            fraDb.hendelser shouldBe oppdatert.hendelser
            fraDb.aktiv shouldBe false
            fraDb.tekst shouldBe "Utkast stoppet"
        }

        @Test
        fun `upsert - forslag med samme forslagId oppdaterer eksisterende rad og beholder kanonisk id`() {
            // Arrange
            val forslagId = UUID.randomUUID()

            val original = tiltakshendelse(
                id = UUID.randomUUID(),
                forslagId = forslagId,
                hendelser = emptyList(),
                tekst = "Forslag: Forleng deltakelse",
                type = Tiltakshendelse.Type.FORSLAG,
            )

            val firstPersisted = tiltakshendelseRepository.upsert(original)

            val reprosessert = original.copy(
                id = UUID.randomUUID(),
                aktiv = false,
                tekst = "Forslag: Forleng deltakelse (godkjent)",
            )

            // Act
            val secondPersisted = tiltakshendelseRepository.upsert(reprosessert)

            // Assert
            secondPersisted.id shouldBe firstPersisted.id

            val fraDb = tiltakshendelseRepository.getForslagHendelse(forslagId).shouldBeSuccess()
            assertSoftly(fraDb) {
                id shouldBe firstPersisted.id
                aktiv shouldBe false
                tekst shouldBe "Forslag: Forleng deltakelse (godkjent)"
            }
        }
    }

    @Test
    fun `getByHendelseId - finner tiltakshendelse med hendelse i array`() {
        // Arrange
        val hendelseId = UUID.randomUUID()
        val tiltakshendelse = tiltakshendelse(
            id = UUID.randomUUID(),
            forslagId = null,
            hendelser = listOf(UUID.randomUUID(), hendelseId),
            tekst = "Utkast",
            type = Tiltakshendelse.Type.UTKAST,
        )

        tiltakshendelseRepository.upsert(tiltakshendelse)

        // Act
        val funnet = tiltakshendelseRepository.getByHendelseId(hendelseId).shouldBeSuccess()

        // Assert
        funnet.id shouldBe tiltakshendelse.id
    }

    companion object {
        private fun tiltakshendelse(
            id: UUID,
            forslagId: UUID?,
            hendelser: List<UUID>,
            tekst: String,
            type: Tiltakshendelse.Type,
        ) = Tiltakshendelse(
            id = id,
            type = type,
            deltakerId = UUID.randomUUID(),
            forslagId = forslagId,
            hendelser = hendelser,
            personident = "12345678901",
            aktiv = true,
            tekst = tekst,
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            opprettet = LocalDateTime.now(),
        )
    }
}
