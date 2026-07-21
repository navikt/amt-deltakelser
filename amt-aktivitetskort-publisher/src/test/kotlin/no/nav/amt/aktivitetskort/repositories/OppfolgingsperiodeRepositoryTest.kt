package no.nav.amt.aktivitetskort.repositories

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.domain.Oppfolgingsperiode
import no.nav.amt.aktivitetskort.utils.shouldBeCloseTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime
import java.util.UUID

class OppfolgingsperiodeRepositoryTest(
    private val oppfolgingsperiodeRepository: OppfolgingsperiodeRepository,
) : RepositoryTestBase() {
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `upsert - ny periode med eller uten sluttdato gir korrekt resultat`(useEndDate: Boolean) {
        val expected = createExpected(useEndDate)

        val actual = oppfolgingsperiodeRepository.upsert(expected)

        assertSoftly(actual) {
            id shouldBe expected.id
            startDato.shouldBeCloseTo(now)

            if (useEndDate) {
                sluttDato.shouldNotBeNull()
                sluttDato.shouldBeCloseTo(tomorrow)
            } else {
                sluttDato.shouldBeNull()
            }
        }
    }

    @Test
    fun `upsert - eksisterende periode oppdateres med ny sluttdato`() {
        val expected = createExpected(true)

        var actual = oppfolgingsperiodeRepository.upsert(expected)
        assertSoftly(actual.sluttDato.shouldNotBeNull()) {
            it.shouldBeCloseTo(tomorrow)
        }

        actual = oppfolgingsperiodeRepository.upsert(expected.copy(sluttDato = nextWeek))
        assertSoftly(actual.sluttDato.shouldNotBeNull()) {
            it.shouldBeCloseTo(nextWeek)
        }
    }

    @Test
    fun `upsert - eksisterende periode kan fa fjernet sluttdato`() {
        val expected = createExpected(true)

        var actual = oppfolgingsperiodeRepository.upsert(expected)
        actual.sluttDato.shouldNotBeNull()

        actual = oppfolgingsperiodeRepository.upsert(expected.copy(sluttDato = null))
        actual.sluttDato.shouldBeNull()
    }

    companion object {
        private val now: LocalDateTime = LocalDateTime.now()
        private val tomorrow: LocalDateTime = now.plusDays(1)
        private val nextWeek: LocalDateTime = now.plusDays(7)

        private fun createExpected(useEndDate: Boolean) = Oppfolgingsperiode(
            id = UUID.randomUUID(),
            startDato = now,
            sluttDato = if (useEndDate) tomorrow else null,
        )
    }
}
