package no.nav.tiltaksarrangor.repositories

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.tiltaksarrangor.RepositoryTestBase
import no.nav.tiltaksarrangor.repositories.model.TiltakstypeDbo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@SpringBootTest(classes = [TiltakstypeRepository::class])
class TiltakstypeRepositoryTest(
    private val sut: TiltakstypeRepository,
) : RepositoryTestBase() {
    @Test
    fun `skal oppdatere eksisterende tiltakstype`() {
        sut.upsert(tiltakstypeInTest)

        val expectedTiltakstype = tiltakstypeInTest.copy(
            navn = "Oppdatert navn",
            tiltakskode = Tiltakskode.JOBBKLUBB,
        )
        sut.upsert(expectedTiltakstype)

        val inDb = jdbcTemplate.queryForMap(
            "SELECT navn, tiltakskode FROM tiltakstype WHERE id = ?",
            expectedTiltakstype.id,
        )

        inDb["navn"] shouldBe expectedTiltakstype.navn
        inDb["tiltakskode"] shouldBe expectedTiltakstype.tiltakskode.name
    }

    companion object {
        private val tiltakstypeInTest = TiltakstypeDbo(
            id = UUID.randomUUID(),
            navn = "~navn~",
            tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
        )
    }
}
