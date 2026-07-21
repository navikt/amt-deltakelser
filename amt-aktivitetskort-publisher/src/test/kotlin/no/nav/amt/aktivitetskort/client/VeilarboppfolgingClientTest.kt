package no.nav.amt.aktivitetskort.client

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.TestUtils.staticObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpStatus
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

class VeilarboppfolgingClientTest : ClientTestBase() {
    private lateinit var client: VeilarboppfolgingClient

    @BeforeEach
    fun createClient() {
        client = VeilarboppfolgingClient(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            veilarboppfolgingTokenProvider = tokenProvider,
            objectMapper = staticObjectMapper,
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `hentOppfolgingperiode - returnerer gyldig oppfolgingsperiode`(useEndDate: Boolean) {
        val expected = createOppfolgingPeriodeDTO(useEndDate)
        val expectedAsJson = staticObjectMapper.writeValueAsString(expected)
        enqueueJson(expectedAsJson)

        val oppfolgingsperiode = client.hentOppfolgingperiode("123456789")

        assertSoftly(oppfolgingsperiode.shouldNotBeNull()) {
            id shouldBe expected.uuid
            startDato shouldBe nowAsLocalDateTime

            if (sluttDato != null) {
                sluttDato shouldBe nowAsLocalDateTime.plusDays(1)
            } else {
                sluttDato.shouldBeNull()
            }
        }
    }

    @Test
    fun `hentOppfolgingperiode - returnerer null ved 204 No Content`() {
        enqueueHttpStatus(HttpStatus.NO_CONTENT)

        val result = client.hentOppfolgingperiode("12345678910")

        result.shouldBeNull()
    }

    @Test
    fun `hentOppfolgingperiode - kaster feil ved 500 status`() {
        enqueueHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR)

        val thrown = shouldThrow<RuntimeException> {
            client.hentOppfolgingperiode("12345678910")
        }

        thrown.message shouldBe "Uventet status ved hent status-kall mot veilarboppfolging 500"
    }

    companion object {
        private val nowAsZonedDateTimeUtc: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)
        private val nowAsLocalDateTime: LocalDateTime = nowAsZonedDateTimeUtc
            .withZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()

        private fun createOppfolgingPeriodeDTO(useEndDate: Boolean) = VeilarboppfolgingClient.OppfolgingPeriodeDTO(
            uuid = UUID.randomUUID(),
            startDato = nowAsZonedDateTimeUtc,
            sluttDato = if (useEndDate) nowAsZonedDateTimeUtc.plusDays(1) else null,
        )
    }
}
