package no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.utils.IntegrationTestBase
import no.nav.amt.internapi.hendelse.Hendelse
import no.nav.amt.internapi.hendelse.HendelseAnsvarlig
import no.nav.amt.internapi.hendelse.HendelseDeltaker
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.internapi.hendelse.UtkastDto
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerEndringHendelseConsumerTest : IntegrationTestBase() {
    private val service = mockk<UlestHendelseService>(relaxed = true)
    private val consumer = DeltakerEndringHendelseConsumer(service, ulestHendelseRepository)

    @Test
    fun `consume tombstone - sletter ulest hendelse`() = runTest {
        val key = UUID.randomUUID()
        every { ulestHendelseRepository.delete(key) } just runs

        consumer.consume(key, null)

        verify(exactly = 1) { ulestHendelseRepository.delete(key) }
        confirmVerified(service)
    }

    @Test
    fun `consume relevant hendelse - lagrer ulest hendelse`() = runTest {
        val hendelse = lagHendelse(HendelseType.NavGodkjennUtkast(lagUtkastDto()))

        consumer.consume(hendelse.id, objectMapper.writeValueAsString(hendelse))

        verify(exactly = 1) { service.lagreUlestHendelse(hendelse) }
        confirmVerified(ulestHendelseRepository)
    }

    @Test
    fun `consume direkte vedtak - ignorerer hendelsen`() = runTest {
        val hendelse = lagHendelse(
            payload = HendelseType.NavGodkjennUtkast(lagUtkastDto()),
            pameldingstype = GjennomforingPameldingType.DIREKTE_VEDTAK,
        )

        consumer.consume(hendelse.id, objectMapper.writeValueAsString(hendelse))

        confirmVerified(service, ulestHendelseRepository)
    }

    private fun lagHendelse(
        payload: HendelseType,
        pameldingstype: GjennomforingPameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
    ) = Hendelse(
        id = UUID.randomUUID(),
        opprettet = LocalDateTime.of(2026, 7, 8, 11, 0),
        deltaker = HendelseDeltaker(
            id = UUID.randomUUID(),
            personident = "12345678910",
            deltakerliste = HendelseDeltaker.Deltakerliste(
                id = UUID.randomUUID(),
                navn = "Tiltak",
                arrangor = HendelseDeltaker.Deltakerliste.Arrangor(
                    id = UUID.randomUUID(),
                    organisasjonsnummer = "999888777",
                    navn = "Arrangor",
                    overordnetArrangor = null,
                ),
                tiltak = HendelseDeltaker.Deltakerliste.Tiltak(
                    navn = "Tiltak",
                    ledetekst = null,
                    tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
                ),
                startdato = LocalDate.of(2026, 7, 1),
                sluttdato = LocalDate.of(2026, 12, 31),
                oppstartstype = Oppstartstype.LOPENDE,
                pameldingstype = pameldingstype,
                oppmoteSted = "Oslo",
                erEnkeltplass = false,
            ),
            forsteVedtakFattet = null,
            opprettetDato = null,
        ),
        ansvarlig = HendelseAnsvarlig.System,
        payload = payload,
    )

    private fun lagUtkastDto() = UtkastDto(
        startdato = LocalDate.of(2026, 7, 8),
        sluttdato = null,
        dagerPerUke = 3f,
        deltakelsesprosent = 50f,
        bakgrunnsinformasjon = "bakgrunn",
        innhold = emptyList(),
    )
}
