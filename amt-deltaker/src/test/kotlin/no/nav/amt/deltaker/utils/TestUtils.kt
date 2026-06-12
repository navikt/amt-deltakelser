package no.nav.amt.deltaker.utils

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.model.Deltaker
import java.time.LocalDateTime

infix fun Deltaker.shouldBeComparableWith(expected: Deltaker) {
    val statusOpprettetDay = this.status.opprettet
        .toLocalDate()
        .atStartOfDay()
    val gyldigFra = this.status.gyldigFra
        .toLocalDate()
        .atStartOfDay()
    val sistEndret = this.sistEndret.toLocalDate().atStartOfDay()

    fun LocalDateTime.atStartOfDay() = this.toLocalDate().atStartOfDay()

    val now = LocalDateTime.now()
    this.copy(
        sistEndret = sistEndret,
        status = status.copy(id = expected.status.id, opprettet = statusOpprettetDay, gyldigFra = gyldigFra),
        opprettet = now,
        vedtaksinformasjon = vedtaksinformasjon?.copy(
            fattet = this.vedtaksinformasjon.fattet?.atStartOfDay(),
            sistEndret = this.vedtaksinformasjon.sistEndret.atStartOfDay(),
        ),
    ) shouldBe expected.copy(
        sistEndret = expected.sistEndret.atStartOfDay(),
        status = expected.status.copy(
            id = expected.status.id,
            opprettet = expected.status.opprettet.atStartOfDay(),
            gyldigFra = expected.status.gyldigFra.atStartOfDay(),
        ),
        opprettet = now,
        vedtaksinformasjon = expected.vedtaksinformasjon?.let { ev ->
            vedtaksinformasjon?.copy(
                fattet = ev.fattet?.atStartOfDay(),
                sistEndret = ev.sistEndret.atStartOfDay(),
            )
        },
    )
}
