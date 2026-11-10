@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.ANSKAFFELSE_SUB_TYPE
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.INGENKOSTNADER_SUB_TYPE
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.utils.database.Database
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class PrisinfoRepositoryTest {
    @Nested
    inner class UpsertPrisinfoTests {
        @Test
        fun `lagrer prisinfo med alle felter`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val prisinfoDbo = PrisinfoDbo(
                gjennomforingId = gjennomforingInTest.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 15000,
                tilleggsopplysninger = "Standard opplysning",
                begrunnelse = "~begrunnelse~",
            )

            // Act
            PrisinfoRepository.upsertPrisinfo(prisinfoDbo)

            PrisinfoGjennomforingLinkRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoDbo.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            // Assert
            val result = PrisinfoRepository.hentPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            assertSoftly(result.shouldNotBeNull()) {
                gjennomforingId shouldBe gjennomforingInTest.id
                status shouldBe PrisinfoDbo.TotrinnskontrollStatus.KLADD_UTKAST
                prisinfoJsonSubtype shouldBe ANSKAFFELSE_SUB_TYPE
                anskaffelsePris shouldBe 15000
                tilleggsopplysninger shouldBe "Standard opplysning"
                begrunnelse shouldBe "~begrunnelse~"
            }
        }

        @Test
        fun `lagrer prisinfo med minimum felter satt`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val prisinfoDbo = PrisinfoDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = INGENKOSTNADER_SUB_TYPE,
            )

            // Act
            PrisinfoRepository.upsertPrisinfo(prisinfoDbo = prisinfoDbo)

            PrisinfoGjennomforingLinkRepository.upsert(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = prisinfoDbo.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            // Assert
            val result = PrisinfoRepository.hentPrisinfo(
                gjennomforingId = deltakerliste.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            assertSoftly(result.shouldNotBeNull()) {
                prisinfoJsonSubtype shouldBe INGENKOSTNADER_SUB_TYPE
                anskaffelsePris shouldBe null
                tilleggsopplysninger shouldBe null
                begrunnelse shouldBe null
            }
        }
    }

    @Nested
    inner class HentPrisinfoStatusTests {
        @Test
        fun `returnerer status når prisinfo finnes`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val prisinfoDbo = PrisinfoDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 10000,
            )
            PrisinfoRepository.upsertPrisinfo(prisinfoDbo)

            // Act
            val result = PrisinfoRepository.hentPrisinfoStatus(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = prisinfoDbo.id,
            )

            // Assert
            result shouldBe PrisinfoDbo.TotrinnskontrollStatus.KLADD_UTKAST
        }

        @Test
        fun `returnerer oppdatert status etter oppdaterStatus`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val prisinfoDbo = PrisinfoDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 10000,
            )
            PrisinfoRepository.upsertPrisinfo(prisinfoDbo)
            PrisinfoRepository.oppdaterStatusSomIkkeErGodkjent(prisinfoDbo.id, PrisinfoDbo.TotrinnskontrollStatus.RETURNERT)

            // Act
            val result = PrisinfoRepository.hentPrisinfoStatus(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = prisinfoDbo.id,
            )

            // Assert
            result shouldBe PrisinfoDbo.TotrinnskontrollStatus.RETURNERT
        }

        @Test
        fun `returnerer null når prisinfoId ikke finnes`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            // Act
            val result = PrisinfoRepository.hentPrisinfoStatus(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = UUID.randomUUID(),
            )

            // Assert
            result shouldBe null
        }

        @Test
        fun `returnerer null når gjennomforingId ikke stemmer`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val prisinfoDbo = PrisinfoDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 10000,
            )
            PrisinfoRepository.upsertPrisinfo(prisinfoDbo)

            // Act
            val result = PrisinfoRepository.hentPrisinfoStatus(
                gjennomforingId = UUID.randomUUID(),
                prisinformasjonId = prisinfoDbo.id,
            )

            // Assert
            result shouldBe null
        }
    }

    @Nested
    inner class SettGodkjentTests {
        @Test
        fun `returnerer false og lagrer ikke godkjenner når prisinfo mangler`() {
            // Arrange
            val gjennomforingId = UUID.randomUUID()
            val prisinformasjonId = UUID.randomUUID()

            // Act
            val result = PrisinfoRepository.settGodkjent(
                gjennomforingId = gjennomforingId,
                prisinformasjonId = prisinformasjonId,
                godkjentAv = UUID.randomUUID(),
                godkjentAvEnhet = UUID.randomUUID(),
            )

            // Assert
            result shouldBe false
        }

        @Test
        fun `returnerer false når prisinfo tilhører en annen gjennomføring`() {
            // Arrange
            val gjennomforing = lagDeltakerliste()
            TestRepository.insert(gjennomforing)
            val prisinfo = PrisinfoDbo(
                gjennomforingId = gjennomforing.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 10000,
            )
            PrisinfoRepository.upsertPrisinfo(prisinfo)

            // Act
            val result = PrisinfoRepository.settGodkjent(
                gjennomforingId = UUID.randomUUID(),
                prisinformasjonId = prisinfo.id,
                godkjentAv = UUID.randomUUID(),
                godkjentAvEnhet = UUID.randomUUID(),
            )

            // Assert
            result shouldBe false
            PrisinfoRepository.hentPrisinfoStatus(gjennomforing.id, prisinfo.id) shouldBe
                PrisinfoDbo.TotrinnskontrollStatus.KLADD_UTKAST
        }

        @Test
        fun `oppdaterer godkjenner når prisinfo godkjennes på nytt`() {
            // Arrange
            val gjennomforing = lagDeltakerliste()
            val forsteEnhet = lagNavEnhet()
            val forsteAnsatt = lagNavAnsatt(navEnhetId = forsteEnhet.id)
            val nyEnhet = lagNavEnhet()
            val nyAnsatt = lagNavAnsatt(navEnhetId = nyEnhet.id)
            TestRepository.insertAll(gjennomforing, forsteEnhet, forsteAnsatt, nyEnhet, nyAnsatt)
            val prisinfo = PrisinfoDbo(
                gjennomforingId = gjennomforing.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 10000,
            )
            PrisinfoRepository.upsertPrisinfo(prisinfo)
            val forsteGodkjenning = PrisinfoRepository.settGodkjent(
                gjennomforingId = gjennomforing.id,
                prisinformasjonId = prisinfo.id,
                godkjentAv = forsteAnsatt.id,
                godkjentAvEnhet = forsteEnhet.id,
            )
            PrisinfoRepository.oppdaterStatusSomIkkeErGodkjent(
                prisinformasjonId = prisinfo.id,
                status = PrisinfoDbo.TotrinnskontrollStatus.RETURNERT,
            )

            // Act
            val nyGodkjenning = PrisinfoRepository.settGodkjent(
                gjennomforingId = gjennomforing.id,
                prisinformasjonId = prisinfo.id,
                godkjentAv = nyAnsatt.id,
                godkjentAvEnhet = nyEnhet.id,
            )
            val lagretGodkjenner = Database.query { session ->
                session.run(
                    queryOf(
                        """
                        SELECT godkjent_av, godkjent_av_enhet
                        FROM prisinformasjon_godkjenning
                        WHERE prisinformasjon_id = ?
                        """.trimIndent(),
                        prisinfo.id,
                    ).map { row -> row.uuid("godkjent_av") to row.uuid("godkjent_av_enhet") }
                        .asSingle,
                )
            }

            // Assert
            forsteGodkjenning shouldBe true
            nyGodkjenning shouldBe true
            lagretGodkjenner shouldBe (nyAnsatt.id to nyEnhet.id)
        }
    }

    @Nested
    inner class HentPrisinfoTests {
        @Test
        fun `returnerer null når prisinfo ikke finnes`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            // Act
            val result = PrisinfoRepository.hentPrisinfo(
                gjennomforingId = deltakerliste.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            // Assert
            result shouldBe null
        }
    }

    @Nested
    inner class HentPrisinfoListeForHistorikkTests {
        private val navEnhet = lagNavEnhet()
        val deltakerliste = lagDeltakerliste(gjennomforingstype = GjennomforingType.Enkeltplass)
        val navAnsatt = lagNavAnsatt()
        val deltaker = lagDeltaker(
            gjennomforing = deltakerliste,
            status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
        )

        @BeforeEach
        fun beforeEach() {
            NavEnhetRepository().upsert(navEnhet)
            TestRepository.insert(navAnsatt)
        }

        @Test
        fun `returnerer tom liste når deltaker ikke har prisinfo`() {
            // Arrange
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
                sistEndretAv = navAnsatt,
                sistEndretAvEnhet = navEnhet,
            )
            TestRepository.insert(deltaker, vedtak)

            // Act
            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltaker.id)

            // Assert
            result shouldHaveSize 0
        }

        @Test
        fun `returnerer tom liste når prisinfo ikke er godkjent`() {
            // Arrange
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
                sistEndretAv = navAnsatt,
                sistEndretAvEnhet = navEnhet,
            )
            TestRepository.insert(deltaker, vedtak)

            val prisinfoDbo = PrisinfoDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 15000,
            )
            PrisinfoRepository.upsertPrisinfo(prisinfoDbo)

            // Act
            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltaker.id)

            // Assert
            result shouldHaveSize 0
        }

        @Test
        fun `returnerer hvem som godkjente prisinfo`() {
            // Arrange
            val sistEndret = LocalDateTime.now().minusDays(1)
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                sistEndret = sistEndret,
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
                sistEndretAv = navAnsatt,
                sistEndretAvEnhet = navEnhet,
            )
            TestRepository.insert(deltaker, vedtak)

            val prisinfoDbo = PrisinfoDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 20000,
                tilleggsopplysninger = "Opplysning",
            )
            PrisinfoRepository.upsertPrisinfo(prisinfoDbo)
            PrisinfoRepository.settGodkjent(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = prisinfoDbo.id,
                godkjentAv = navAnsatt.id,
                godkjentAvEnhet = navEnhet.id,
            )

            // Act
            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltaker.id)

            // Assert
            result shouldHaveSize 1
            assertSoftly(result.first()) {
                godkjentAvNavAnsattId shouldBe navAnsatt.id
                godkjentAvNavEnhetId shouldBe navEnhet.id
            }
        }

        @Test
        fun `returnerer bare godkjent prisinfo, ikke kladd`() {
            // Arrange
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
                sistEndretAv = navAnsatt,
                sistEndretAvEnhet = navEnhet,
            )
            TestRepository.insert(deltaker, vedtak)

            val godkjentPrisinfo = PrisinfoDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 10000,
            )
            val kladdPrisinfo = PrisinfoDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 25000,
            )
            PrisinfoRepository.upsertPrisinfo(godkjentPrisinfo)
            PrisinfoRepository.upsertPrisinfo(kladdPrisinfo)
            PrisinfoRepository.settGodkjent(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = godkjentPrisinfo.id,
                godkjentAv = navAnsatt.id,
                godkjentAvEnhet = navEnhet.id,
            )

            // Act
            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltaker.id)

            // Assert - bare den godkjente returneres
            result shouldHaveSize 1
        }

        @Test
        fun `returnerer godkjent prisinfo for deltaker uten vedtak`() {
            TestRepository.insert(deltaker)

            val prisinfoDbo = PrisinfoDbo(
                gjennomforingId = deltakerliste.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 15000,
            )
            PrisinfoRepository.upsertPrisinfo(prisinfoDbo)
            PrisinfoRepository.settGodkjent(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = prisinfoDbo.id,
                godkjentAv = navAnsatt.id,
                godkjentAvEnhet = navEnhet.id,
            )

            // Act
            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltaker.id)

            // Assert
            result shouldHaveSize 1
        }

        @Test
        fun `returnerer ikke prisinfo fra en annen deltakers deltakerliste`() {
            // Arrange
            val deltakerliste1 = lagDeltakerliste(gjennomforingstype = GjennomforingType.Enkeltplass)
            val deltakerliste2 = lagDeltakerliste(gjennomforingstype = GjennomforingType.Enkeltplass)

            val deltaker1 = lagDeltaker(
                gjennomforing = deltakerliste1,
                status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
            )
            val deltaker2 = lagDeltaker(
                gjennomforing = deltakerliste2,
                status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
            )
            val vedtak1 = lagVedtak(
                deltakerId = deltaker1.id,
                deltakerVedVedtak = deltaker1,
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
                sistEndretAv = navAnsatt,
                sistEndretAvEnhet = navEnhet,
            )
            val vedtak2 = lagVedtak(
                deltakerId = deltaker2.id,
                deltakerVedVedtak = deltaker2,
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
                sistEndretAv = navAnsatt,
                sistEndretAvEnhet = navEnhet,
            )
            TestRepository.insert(deltaker1, vedtak1)
            TestRepository.insert(deltaker2, vedtak2)

            // Prisinfo on deltakerliste2 only
            val prisinfoDbo = PrisinfoDbo(
                gjennomforingId = deltakerliste2.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 30000,
            )
            PrisinfoRepository.upsertPrisinfo(prisinfoDbo)
            PrisinfoRepository.settGodkjent(
                gjennomforingId = deltakerliste2.id,
                prisinformasjonId = prisinfoDbo.id,
                godkjentAv = navAnsatt.id,
                godkjentAvEnhet = navEnhet.id,
            )

            // Act
            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltaker1.id)

            // Assert
            result shouldHaveSize 0
        }

        @Test
        fun `returnerer ikke godkjent prisinfo for gruppedeltaker`() {
            val gruppe = lagDeltakerliste(gjennomforingstype = GjennomforingType.Gruppe)
            val gruppeDeltaker = lagDeltaker(
                gjennomforing = gruppe,
                status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(gruppeDeltaker)

            val prisinfoDbo = PrisinfoDbo(
                gjennomforingId = gruppe.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 30000,
            )
            PrisinfoRepository.upsertPrisinfo(prisinfoDbo)
            PrisinfoRepository.settGodkjent(
                gjennomforingId = gruppe.id,
                prisinformasjonId = prisinfoDbo.id,
                godkjentAv = navAnsatt.id,
                godkjentAvEnhet = navEnhet.id,
            )

            val result = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(gruppeDeltaker.id)

            result shouldHaveSize 0
        }
    }

    private companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        val gjennomforingInTest = lagDeltakerliste()
    }
}
