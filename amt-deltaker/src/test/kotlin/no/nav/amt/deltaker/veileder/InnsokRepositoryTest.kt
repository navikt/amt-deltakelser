package no.nav.amt.deltaker.veileder

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.service.DeltakerContext
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Innsok
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class InnsokRepositoryTest {
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

    @Test
    fun `insert - ny innsok med opplæringkategorisering og innhold - inserter og leser alle felter korrekt`() {
        with(DeltakerContext()) {
            medVedtak()

            val kategorisering = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(UUID.randomUUID() to "Bygg og anlegg"),
                    ),
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.FORERKORT,
                        valg = mapOf(UUID.randomUUID() to "B", UUID.randomUUID() to "C1"),
                    ),
                ),
                valgteSertifiseringer = setOf(
                    SertifiseringValg(id = 1, navn = "Truckfører T1"),
                ),
            )

            val deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = "Innhold i tiltaket",
                innhold = listOf(
                    Innhold(tekst = "Arbeidspraksis", innholdskode = "arbeidspraksis", valgt = true, beskrivelse = null),
                ),
            )

            val innsokt = LocalDateTime.of(2026, 6, 15, 10, 30, 0)
            val utkastDelt = LocalDateTime.of(2026, 6, 14, 9, 0, 0)

            val innsok = Innsok(
                id = UUID.randomUUID(),
                deltakerId = deltaker.id,
                innsokt = innsokt,
                innsoktAv = deltaker.vedtaksinformasjon!!.sistEndretAv,
                innsoktAvEnhet = deltaker.vedtaksinformasjon!!.sistEndretAvEnhet,
                deltakelsesinnholdVedInnsok = deltakelsesinnhold,
                opplaringKategoriseringVedInnsok = kategorisering,
                utkastDelt = utkastDelt,
                utkastGodkjentAvNav = true,
            )

            innsokRepository.insert(innsok)

            val lagret = innsokRepository.get(innsok.id).getOrThrow()

            assertSoftly(lagret) {
                id shouldBe innsok.id
                deltakerId shouldBe innsok.deltakerId
                innsokt shouldBe innsok.innsokt
                innsoktAv shouldBe innsok.innsoktAv
                innsoktAvEnhet shouldBe innsok.innsoktAvEnhet
                utkastGodkjentAvNav shouldBe true
                utkastDelt shouldBe innsok.utkastDelt
                deltakelsesinnholdVedInnsok shouldBe deltakelsesinnhold
                opplaringKategoriseringVedInnsok shouldBe kategorisering
            }
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
    opplaringKategoriseringVedInnsok = deltaker.deltakerliste.opplaringKategorisering,
)
