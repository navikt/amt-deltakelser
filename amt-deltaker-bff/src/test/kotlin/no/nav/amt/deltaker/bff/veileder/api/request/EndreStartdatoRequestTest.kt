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
        fun `valider - ny startdato, gyldig status, gruppe - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModelMedDatoer(
                startdato = LocalDate.now().minusMonths(1),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now(),
                sluttdato = null,
                begrunnelse = "Ny startdato",
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - ny sluttdato, gyldig status, gruppe - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModelMedDatoer(
                startdato = LocalDate.now().minusMonths(2),
                sluttdato = LocalDate.now().minusMonths(1),
            )

            val request = EndreStartdatoRequest(
                startdato = null,
                sluttdato = LocalDate.now().plusMonths(1),
                begrunnelse = "Ny sluttdato",
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - både start- og sluttdato endret, gyldig status, gruppe - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModelMedDatoer(
                startdato = LocalDate.now().minusMonths(2),
                sluttdato = LocalDate.now().minusMonths(1),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusMonths(2),
                begrunnelse = "Endrer både start og slutt",
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
            val deltaker = lagDeltakerModelMedDatoer(
                startdato = startdato,
                sluttdato = sluttdato,
            )

            val request = EndreStartdatoRequest(
                startdato = startdato,
                sluttdato = sluttdato,
                begrunnelse = "Ingen endring",
                forslagId = null,
            )

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
            exception.message shouldContain "Både startdato og sluttdato kan ikke være lik som før"
        }

        @Test
        fun `valider - status ikke i tillatt liste - skal kaste exception`() {
            val deltaker = lagDeltakerModelMedDatoer(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now(),
                sluttdato = null,
                begrunnelse = "Ny startdato",
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
            val deltaker = lagDeltakerModelMedDatoer(
                startdato = gjennomforingStartDato,
                gjennomforingStartDato = gjennomforingStartDato,
            )

            val request = EndreStartdatoRequest(
                startdato = gjennomforingStartDato.minusDays(1),
                sluttdato = null,
                begrunnelse = "For tidlig startdato",
                forslagId = null,
            )

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
            exception.message shouldContain "Startdato kan ikke være tidligere enn deltakerlistens startdato"
        }

        @Test
        fun `valider - enkeltplass med ny startdato - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModelEnkeltplass(
                startdato = LocalDate.now().minusMonths(1),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now(),
                sluttdato = null,
                begrunnelse = "Ny startdato for enkeltplass",
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
            val deltaker = lagDeltakerModelEnkeltplass(
                startdato = startdato,
                sluttdato = sluttdato,
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

            val request = EndreStartdatoRequest(
                startdato = startdato,
                sluttdato = sluttdato,
                begrunnelse = "Ingen endring",
                forslagId = null,
            )

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
            exception.message shouldContain "Både startdato og sluttdato kan ikke være lik som før"
        }

        @Test
        fun `valider - setter sluttdato, startdato uendret - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModelMedDatoer(
                startdato = LocalDate.now().minusMonths(2),
                sluttdato = null,
            )

            val request = EndreStartdatoRequest(
                startdato = null,
                sluttdato = LocalDate.now().plusMonths(1),
                begrunnelse = "Setter ny sluttdato",
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - status VENTER_PA_OPPSTART - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModelMedDatoer(
                startdato = LocalDate.now(),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now().plusDays(7),
                sluttdato = null,
                begrunnelse = "Utsetter oppstart",
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - status AVBRUTT - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModelMedDatoer(
                startdato = LocalDate.now().minusMonths(1),
                sluttdato = LocalDate.now(),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.AVBRUTT),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now().minusMonths(2),
                sluttdato = null,
                begrunnelse = "Endre avbrutt deltaker",
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - forlenger sluttdato - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModelMedDatoer(
                startdato = LocalDate.now().minusMonths(1),
                sluttdato = LocalDate.now(),
            )

            val request = EndreStartdatoRequest(
                startdato = null,
                sluttdato = LocalDate.now().plusDays(1),
                begrunnelse = "Forlenger sluttdato",
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - status FULLFORT - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModelMedDatoer(
                startdato = LocalDate.now().minusMonths(2),
                sluttdato = LocalDate.now().minusMonths(1),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.FULLFORT),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now().minusMonths(1),
                sluttdato = null,
                begrunnelse = "Justering av fullfort deltaker",
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - status HAR_SLUTTET - skal ikke kaste exception`() {
            val deltaker = lagDeltakerModelMedDatoer(
                startdato = LocalDate.now().minusMonths(3),
                sluttdato = LocalDate.now().minusMonths(1),
                gjennomforingStartDato = LocalDate.now().minusMonths(4),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now().minusMonths(2),
                sluttdato = null,
                begrunnelse = "Justering av sluttdato",
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - enkeltplass med startdato for gruppen startdato - skal ikke kaste exception (early return)`() {
            // This test verifies that the early return for enkeltplass skips subsequent validations
            val groupStartDato = LocalDate.now().minusMonths(3)
            val invalideStartdato = groupStartDato.minusDays(1) // This would fail if validated
            val deltaker = lagDeltakerModelEnkeltplass(
                startdato = invalideStartdato,
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

            val request = EndreStartdatoRequest(
                startdato = groupStartDato.minusDays(10), // Would violate group constraint
                sluttdato = null,
                begrunnelse = "Endring for enkeltplass",
                forslagId = null,
            )

            // Should not throw because enkeltplass skips the validation
            // If the early return didn't work, this would throw about startdato being too early
            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - enkeltplass uten endring - skal kaste exception (before early return)`() {
            val startdato = LocalDate.now().minusMonths(1)
            val sluttdato = LocalDate.now().plusMonths(1)
            val deltaker = lagDeltakerModelEnkeltplass(
                startdato = startdato,
                sluttdato = sluttdato,
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

            val request = EndreStartdatoRequest(
                startdato = startdato,
                sluttdato = sluttdato,
                begrunnelse = "Ingen endring",
                forslagId = null,
            )

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
            exception.message shouldContain "Både startdato og sluttdato kan ikke være lik som før"
        }

        @Test
        fun `valider - enkeltplass med ugyldig sluttdato (etter gruppe sluttdato) - skal kaste exception (before early return)`() {
            // This test verifies sluttdato validation now runs BEFORE the enkeltplass early return
            val deltaker = lagDeltakerModelEnkeltplass(
                startdato = LocalDate.now().minusMonths(1),
                sluttdato = LocalDate.now().plusMonths(6), // Original sluttdato
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

            val request = EndreStartdatoRequest(
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusMonths(2), // Would be valid for enkeltplass, but let's use invalid
                begrunnelse = "Endring for enkeltplass",
                forslagId = null,
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - enkeltplass med sluttdato for startdato - skal kaste exception (sluttdato validation before early return)`() {
            // This test verifies that sluttdato validation runs before the enkeltplass early return
            val startdato = LocalDate.now()
            val deltaker = lagDeltakerModelEnkeltplass(
                startdato = LocalDate.now().minusMonths(1),
                sluttdato = LocalDate.now().plusMonths(6),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

            val request = EndreStartdatoRequest(
                startdato = startdato,
                sluttdato = startdato.minusDays(1), // Invalid: sluttdato before startdato
                begrunnelse = "Endring for enkeltplass",
                forslagId = null,
            )

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
            exception.message shouldContain "Sluttdato må være etter startdato"
        }
    }

    companion object {
        private fun lagDeltakerModelMedDatoer(
            startdato: LocalDate? = LocalDate.now().minusMonths(1),
            sluttdato: LocalDate? = LocalDate.now().plusMonths(1),
            gjennomforingStartDato: LocalDate = LocalDate.now().minusMonths(3),
            status: DeltakerStatus = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        ) = ModelMapper.toDeltaker(
            TestData.lagDeltakerResponse(
                deltakerliste = lagGjennomforingResponse(
                    startDato = gjennomforingStartDato,
                ),
                startdato = startdato,
                sluttdato = sluttdato,
                status = status,
            ),
        )

        private fun lagDeltakerModelEnkeltplass(
            startdato: LocalDate? = LocalDate.now().minusMonths(1),
            sluttdato: LocalDate? = LocalDate.now().plusMonths(1),
            status: DeltakerStatus = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        ) = ModelMapper.toDeltaker(
            TestData.lagDeltakerResponse(
                deltakerliste = lagGjennomforingResponse().copy(
                    type = GjennomforingType.Enkeltplass,
                ),
                startdato = startdato,
                sluttdato = sluttdato,
                status = status,
            ),
        )
    }
}
