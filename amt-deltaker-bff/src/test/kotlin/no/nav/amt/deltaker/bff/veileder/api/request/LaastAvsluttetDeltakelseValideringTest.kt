package no.nav.amt.deltaker.bff.veileder.api.request

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.stream.Stream

/**
 * Tester for endringer av deltakelser som er låst for endringer (kanEndres = false)
 * pga. nyere deltakelse på samme tiltak, men avsluttet for under 2 måneder siden.
 */
class LaastAvsluttetDeltakelseValideringTest {
    companion object {
        private val sluttdatoIGår = LocalDate.now().minusDays(1)
        private val sluttdatoFireUkerSiden = LocalDate.now().minusWeeks(4)

        /** Deltaker som har sluttet nylig og er låst */
        val laastNyligAvsluttetDeltaker = lagDeltaker(
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusWeeks(4),
            ),
            sluttdato = sluttdatoFireUkerSiden,
            kanEndres = false,
        )

        /** Deltaker som har sluttet for lenge siden og er låst – skal ikke kunne endres */
        val laastGammelAvsluttetDeltaker = lagDeltaker(
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusMonths(4),
            ),
            sluttdato = LocalDate.now().minusMonths(4),
            kanEndres = false,
        )

        @JvmStatic
        fun tillattEndringRequestTypes(): Stream<EndringRequestFromFrontend> = Stream.of(
            EndreSluttarsakRequest(
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                begrunnelse = "begrunnelse",
                forslagId = null,
            ),
            EndreAvslutningRequest(
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                sluttdato = sluttdatoIGår,
                begrunnelse = "begrunnelse",
                forslagId = null,
            ),
        )

        @JvmStatic
        fun ikkeTillattEndringRequestTypes(): Stream<EndringRequestFromFrontend> = Stream.of(
            EndreBakgrunnsinformasjonRequest(bakgrunnsinformasjon = "ny info"),
            EndreDeltakelsesmengdeRequest(
                deltakelsesprosent = 50,
                dagerPerUke = null,
                gyldigFra = LocalDate.now().minusDays(1),
                begrunnelse = null,
                forslagId = null,
            ),
            EndreStartdatoRequest(
                startdato = LocalDate.now().minusMonths(2),
                sluttdato = sluttdatoIGår,
                begrunnelse = null,
                forslagId = null,
            ),
            ForlengDeltakelseRequest(
                sluttdato = LocalDate.now().plusMonths(1),
                begrunnelse = null,
                forslagId = null,
            ),
            // EndreSluttdatoRequest er ikke tillatt for låste deltakere – frontend eksponerer det ikke
            EndreSluttdatoRequest(
                sluttdato = sluttdatoIGår,
                begrunnelse = "begrunnelse",
                forslagId = null,
            ),
        )
    }

    @Nested
    inner class TillattForLaastAvsluttetDeltakelse {
        @ParameterizedTest
        @MethodSource("no.nav.amt.deltaker.bff.veileder.api.request.LaastAvsluttetDeltakelseValideringTest#tillattEndringRequestTypes")
        fun `skal returnere true for tillatte endringstyper`(request: EndringRequestFromFrontend) {
            request.tillattForLaastAvsluttetDeltakelse() shouldBe true
        }

        @ParameterizedTest
        @MethodSource("no.nav.amt.deltaker.bff.veileder.api.request.LaastAvsluttetDeltakelseValideringTest#ikkeTillattEndringRequestTypes")
        fun `skal returnere false for ikke-tillatte endringstyper`(request: EndringRequestFromFrontend) {
            request.tillattForLaastAvsluttetDeltakelse() shouldBe false
        }
    }

    @Nested
    inner class EndreAvslutningValideringLaastDeltaker {
        @Test
        fun `skal tillate endring av avsluttende status med sluttdato tilbake i tid for låst nylig avsluttet deltaker`() {
            val request = EndreAvslutningRequest(
                harFullfort = true,
                sluttdato = sluttdatoIGår,
                aarsak = null,
                begrunnelse = "begrunnelse",
                forslagId = UUID.randomUUID(),
            )
            val deltaker = laastNyligAvsluttetDeltaker.copy(
                status = lagDeltakerStatus(DeltakerStatus.Type.AVBRUTT),
                sluttdato = sluttdatoFireUkerSiden,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `skal avvise endring for låst deltaker med status IKKE_AKTUELL`() {
            val request = EndreAvslutningRequest(
                harFullfort = true,
                sluttdato = sluttdatoIGår,
                aarsak = null,
                begrunnelse = "begrunnelse",
                forslagId = UUID.randomUUID(),
            )
            val deltaker = laastNyligAvsluttetDeltaker.copy(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.IKKE_AKTUELL,
                    gyldigFra = LocalDateTime.now().minusWeeks(4),
                ),
                sluttdato = null,
            )

            // IKKE_AKTUELL er ikke i kanEndreAvslutning-listen og blokkeres av den generelle statussjekken
            shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }.message shouldBe "Kan ikke endre avslutning for deltaker som ikke har status AVBRUTT, FULLFORT, HAR_SLUTTET eller DELTAR"
        }

        @Test
        fun `skal avvise endring av avsluttende status med sluttdato frem i tid for låst deltaker`() {
            val request = EndreAvslutningRequest(
                harFullfort = true,
                sluttdato = LocalDate.now().plusDays(1),
                aarsak = null,
                begrunnelse = "begrunnelse",
                forslagId = UUID.randomUUID(),
            )
            val deltaker = laastNyligAvsluttetDeltaker.copy(
                status = lagDeltakerStatus(DeltakerStatus.Type.AVBRUTT),
                sluttdato = sluttdatoFireUkerSiden,
            )

            shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }.message shouldBe "Sluttdato må være tilbake i tid når deltakelsen er låst for endringer"
        }

        @Test
        fun `skal tillate endring av avsluttende status uten sluttdato for låst deltaker`() {
            // Endring av bare harFullfort uten å endre sluttdato
            val request = EndreAvslutningRequest(
                harFullfort = true,
                sluttdato = null,
                aarsak = null,
                begrunnelse = "begrunnelse",
                forslagId = UUID.randomUUID(),
            )
            val deltaker = laastNyligAvsluttetDeltaker.copy(
                status = lagDeltakerStatus(DeltakerStatus.Type.AVBRUTT),
                sluttdato = sluttdatoFireUkerSiden,
            )

            // Ingen sluttdato endres – ingen validering av past
            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `skal avvise endring for låst gammelt avsluttet deltaker`() {
            val request = EndreAvslutningRequest(
                harFullfort = true,
                sluttdato = sluttdatoIGår,
                aarsak = null,
                begrunnelse = "begrunnelse",
                forslagId = UUID.randomUUID(),
            )
            // Bruker gammel gyldigFra for at harSluttetForMindreEnnToMndSiden() skal returnere false
            val gammelGyldigFra = LocalDateTime.now().minusMonths(4)
            val deltaker = laastGammelAvsluttetDeltaker.copy(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.AVBRUTT,
                    gyldigFra = gammelGyldigFra,
                ),
            )

            shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }.message shouldBe "Kan ikke endre deltaker som fikk avsluttende status for mer enn to måneder siden"
        }
    }

    @Nested
    inner class EndreSluttarsakValideringLaastDeltaker {
        @Test
        fun `skal tillate endring av sluttarsak for låst nylig avsluttet deltaker`() {
            val request = EndreSluttarsakRequest(
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                begrunnelse = "begrunnelse",
                forslagId = UUID.randomUUID(),
            )
            val deltaker = laastNyligAvsluttetDeltaker.copy(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.IKKE_MOTT,
                ),
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `skal avvise endring for låst gammelt avsluttet deltaker`() {
            val request = EndreSluttarsakRequest(
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                begrunnelse = "begrunnelse",
                forslagId = UUID.randomUUID(),
            )
            // Bruker gammel gyldigFra for at harSluttetForMindreEnnToMndSiden() skal returnere false
            val gammelGyldigFra = LocalDateTime.now().minusMonths(4)
            val deltaker = laastGammelAvsluttetDeltaker.copy(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.IKKE_MOTT,
                    gyldigFra = gammelGyldigFra,
                ),
            )

            shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }.message shouldBe "Kan ikke endre deltaker som fikk avsluttende status for mer enn to måneder siden"
        }
    }
}
