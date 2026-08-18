package no.nav.tiltaksarrangor.melding.endring

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.verify
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.tiltaksarrangor.IntegrationTestBase
import no.nav.tiltaksarrangor.melding.MELDING_TOPIC
import no.nav.tiltaksarrangor.melding.endring.request.LeggTilOppstartsdatoRequest
import no.nav.tiltaksarrangor.testutils.DeltakerContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate

class EndringServiceTest(
    private val endringService: EndringService,
    @MockkBean private val unleashToggle: CommonUnleashToggle,
) : IntegrationTestBase() {
    @BeforeEach
    fun setup() {
        every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns false
    }

    @Test
    fun `endreDeltaker - ny endring - returnerer oppdaterer deltaker og produserer melding`() {
        with(DeltakerContext(applicationContext)) {
            setVenterPaOppstart()
            val request = LeggTilOppstartsdatoRequest(
                startdato = LocalDate.now().plusWeeks(7),
                sluttdato = LocalDate.now().plusWeeks(42),
            )

            val oppdatertDeltaker = endringService.endreDeltaker(
                deltaker = deltaker,
                deltakerliste = deltakerliste,
                ansatt = koordinator,
                request = request,
            )

            oppdatertDeltaker.startDato shouldBe request.startdato
            oppdatertDeltaker.sluttDato shouldBe request.sluttdato

            val keys = mutableListOf<String>()
            val values = mutableListOf<String>()
            verify { producer.produce(eq(MELDING_TOPIC), capture(keys), capture(values)) }

            val endring = objectMapper.readValue<EndringFraArrangor>(values.last())
            endring.deltakerId shouldBe deltaker.id
            endring.endring.shouldBeInstanceOf<EndringFraArrangor.LeggTilOppstartsdato>()
        }
    }

    @Test
    fun `endreDeltaker - ny endring - oppdaterer og lagrer deltaker`() {
        with(DeltakerContext(applicationContext)) {
            setVenterPaOppstart()
            val request = LeggTilOppstartsdatoRequest(
                startdato = LocalDate.now().plusWeeks(7),
                sluttdato = LocalDate.now().plusWeeks(42),
            )
            endringService.endreDeltaker(
                deltaker = deltaker,
                deltakerliste = deltakerliste,
                ansatt = koordinator,
                request = request,
            )

            val oppdatertDeltaker = deltakerRepository.getDeltaker(deltaker.id)
            oppdatertDeltaker?.startdato shouldBe request.startdato
            oppdatertDeltaker?.sluttdato shouldBe request.sluttdato
        }
    }
}
