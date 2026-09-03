package no.nav.amt.deltaker.bff.veileder.api.request

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestData.lagGjennomforingResponse
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EndreStartdatoRequestTest {
    @Nested
    inner class ValiderTest {
        @Test
        fun `valider - enkeltplass med ny startdato - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModel(
                startdato = LocalDate.now().minusMonths(1),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now(),
                sluttdato = null,
                begrunnelse = "Ny startdato for enkeltplass",
                pavirkerPris = false,
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - enkeltplass uten endring - skal kaste exception`() {
            val startdato = LocalDate.now().minusMonths(1)
            val sluttdato = LocalDate.now().plusMonths(1)

            val deltaker = lagDeltakerModel(
                startdato = startdato,
                sluttdato = sluttdato,
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

            val request = EndreStartdatoRequest(
                startdato = startdato,
                sluttdato = sluttdato,
                begrunnelse = "Ingen endring",
                pavirkerPris = false,
                forslagId = null,
            )

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
            exception.message shouldContain "Både startdato og sluttdato kan ikke være lik som før"
        }

        @Test
        fun `valider - enkeltplass med sluttdato før startdato - skal kaste exception`() {
            val deltaker = lagDeltakerModel(
                startdato = LocalDate.now().minusMonths(1),
                sluttdato = LocalDate.now().plusMonths(6),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

            val startdato = LocalDate.now()

            val request = EndreStartdatoRequest(
                startdato = startdato,
                sluttdato = startdato.minusDays(1),
                begrunnelse = "Endring for enkeltplass",
                pavirkerPris = false,
                forslagId = null,
            )

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
            exception.message shouldContain "Sluttdato må være etter startdato"
        }

        @Test
        fun `valider - ny startdato, gyldig status, gruppe - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModel(
                startdato = LocalDate.now().minusMonths(1),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now(),
                sluttdato = null,
                begrunnelse = "Ny startdato",
                pavirkerPris = false,
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - ny sluttdato, gyldig status, gruppe - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModel(
                startdato = LocalDate.now().minusMonths(2),
                sluttdato = LocalDate.now().minusMonths(1),
            )

            val request = EndreStartdatoRequest(
                startdato = null,
                sluttdato = LocalDate.now().plusMonths(1),
                begrunnelse = "Ny sluttdato",
                pavirkerPris = false,
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - både start- og sluttdato endret, gyldig status, gruppe - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModel(
                startdato = LocalDate.now().minusMonths(2),
                sluttdato = LocalDate.now().minusMonths(1),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusMonths(2),
                begrunnelse = "Endrer både start og slutt",
                pavirkerPris = false,
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - ingen endring, samme startdato og sluttdato - skal kaste exception`() {
            val startdato = LocalDate.now().minusMonths(1)
            val sluttdato = LocalDate.now().plusMonths(1)

            val deltaker = lagDeltakerModel(
                startdato = startdato,
                sluttdato = sluttdato,
            )

            val request = EndreStartdatoRequest(
                startdato = startdato,
                sluttdato = sluttdato,
                begrunnelse = "Ingen endring",
                pavirkerPris = false,
                forslagId = null,
            )

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
            exception.message shouldContain "Både startdato og sluttdato kan ikke være lik som før"
        }

        @Test
        fun `valider - status ikke i tillatt liste - skal kaste exception`() {
            val deltaker = lagDeltakerModel(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now(),
                sluttdato = null,
                begrunnelse = "Ny startdato",
                pavirkerPris = false,
                forslagId = null,
            )

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
            exception.message shouldContain "Kan ikke endre startdato for deltaker med status"
        }

        @Test
        fun `valider - startdato for gjennomforingListens startdato - skal kaste exception`() {
            val gjennomforingStartDato = LocalDate.now().minusMonths(1)
            val deltaker = lagDeltakerModel(
                startdato = gjennomforingStartDato,
                gjennomforingStartDato = gjennomforingStartDato,
            )

            val request = EndreStartdatoRequest(
                startdato = gjennomforingStartDato.minusDays(1),
                sluttdato = null,
                begrunnelse = "For tidlig startdato",
                pavirkerPris = false,
                forslagId = null,
            )

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
            exception.message shouldContain "Startdato kan ikke være tidligere enn deltakerlistens startdato"
        }

        @Test
        fun `valider - setter sluttdato, startdato uendret - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModel(
                startdato = LocalDate.now().minusMonths(2),
                sluttdato = null,
            )

            val request = EndreStartdatoRequest(
                startdato = null,
                sluttdato = LocalDate.now().plusMonths(1),
                begrunnelse = "Setter ny sluttdato",
                pavirkerPris = false,
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - status VENTER_PA_OPPSTART - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModel(
                startdato = LocalDate.now(),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now().plusDays(7),
                sluttdato = null,
                begrunnelse = "Utsetter oppstart",
                pavirkerPris = false,
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - status AVBRUTT - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModel(
                startdato = LocalDate.now().minusMonths(1),
                sluttdato = LocalDate.now(),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.AVBRUTT),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now().minusMonths(2),
                sluttdato = null,
                begrunnelse = "Endre avbrutt deltaker",
                pavirkerPris = false,
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - forlenger sluttdato - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModel(
                startdato = LocalDate.now().minusMonths(1),
                sluttdato = LocalDate.now(),
            )

            val request = EndreStartdatoRequest(
                startdato = null,
                sluttdato = LocalDate.now().plusDays(1),
                begrunnelse = "Forlenger sluttdato",
                pavirkerPris = false,
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - status FULLFORT - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModel(
                startdato = LocalDate.now().minusMonths(2),
                sluttdato = LocalDate.now().minusMonths(1),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.FULLFORT),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now().minusMonths(1),
                sluttdato = null,
                begrunnelse = "Justering av fullfort deltaker",
                pavirkerPris = false,
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - status HAR_SLUTTET - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModel(
                startdato = LocalDate.now().minusMonths(3),
                sluttdato = LocalDate.now().minusMonths(1),
                gjennomforingStartDato = LocalDate.now().minusMonths(4),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now().minusMonths(2),
                sluttdato = null,
                begrunnelse = "Justering av sluttdato",
                pavirkerPris = false,
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - status SOKT_INN for enkeltplass - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModel(
                startdato = LocalDate.now().minusMonths(1),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                gjennomforingType = GjennomforingType.Enkeltplass,
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now(),
                sluttdato = null,
                begrunnelse = "Justerer startdato",
                pavirkerPris = false,
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - status SOKT_INN for gruppe - skal kaste exception`() {
            val deltaker = lagDeltakerModel(
                startdato = LocalDate.now().minusMonths(1),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                gjennomforingType = GjennomforingType.Gruppe,
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now(),
                sluttdato = null,
                begrunnelse = "Justerer startdato",
                pavirkerPris = false,
                forslagId = null,
            )

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
            exception.message shouldContain "Kan ikke endre startdato for deltaker med status"
        }

        @Test
        fun `valider - enkeltplass med startdato for gjennomforingens startdato - skal ikke kaste exception`() {
            val gjennomforingStartDato = LocalDate.now().minusMonths(3)

            val deltaker = lagDeltakerModel(
                startdato = gjennomforingStartDato.minusDays(1),
                gjennomforingStartDato = gjennomforingStartDato,
                gjennomforingType = GjennomforingType.Enkeltplass,
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

            val request = EndreStartdatoRequest(
                startdato = gjennomforingStartDato.minusDays(10),
                sluttdato = null,
                begrunnelse = "Endring for enkeltplass",
                pavirkerPris = false,
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - enkeltplass med sluttdato etter gjennomforingens sluttdato - skal kaste exception`() {
            val gjennomforingSluttDato = LocalDate.now().plusMonths(6)
            val deltaker = lagDeltakerModel(
                startdato = LocalDate.now().minusMonths(1),
                sluttdato = gjennomforingSluttDato.minusDays(1),
                gjennomforingSluttDato = gjennomforingSluttDato,
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now(),
                sluttdato = gjennomforingSluttDato.plusDays(1),
                begrunnelse = "Endring for enkeltplass",
                pavirkerPris = false,
                forslagId = null,
            )

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
            exception.message shouldContain "Sluttdato kan ikke være senere enn deltakerlistens sluttdato"
        }
    }

    companion object {
        private fun lagDeltakerModel(
            startdato: LocalDate? = LocalDate.now().minusMonths(1),
            sluttdato: LocalDate? = LocalDate.now().plusMonths(1),
            gjennomforingStartDato: LocalDate = LocalDate.now().minusMonths(3),
            gjennomforingSluttDato: LocalDate? = LocalDate.now().plusYears(1),
            gjennomforingType: GjennomforingType = GjennomforingType.Gruppe,
            status: DeltakerStatus = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        ) = ModelMapper.toDeltaker(
            TestData.lagDeltakerResponse(
                deltakerliste = lagGjennomforingResponse(
                    startDato = gjennomforingStartDato,
                    sluttDato = gjennomforingSluttDato,
                ).copy(
                    type = gjennomforingType,
                ),
                startdato = startdato,
                sluttdato = sluttdato,
                status = status,
            ),
        )
    }
}
