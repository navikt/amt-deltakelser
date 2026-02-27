package no.nav.amt.deltaker.bff.deltaker.api.model

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.deltaker.api.utils.MAX_ANNET_INNHOLD_LENGDE
import no.nav.amt.deltaker.bff.deltaker.api.utils.MAX_BAKGRUNNSINFORMASJON_LENGDE
import no.nav.amt.deltaker.bff.deltaker.api.utils.MAX_DAGER_PER_UKE
import no.nav.amt.deltaker.bff.deltaker.api.utils.MAX_DELTAKELSESPROSENT
import no.nav.amt.deltaker.bff.deltaker.api.utils.MIN_DAGER_PER_UKE
import no.nav.amt.deltaker.bff.deltaker.api.utils.MIN_DELTAKELSESPROSENT
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.annetInnholdselement
import no.nav.amt.deltaker.bff.utils.data.TestData.input
import org.junit.jupiter.api.Test

class KladdRequestTest {
    @Test
    fun `sanitize - bakgrunnsinformasjon er lengre enn max - avkorter teksten`() {
        val request = KladdRequest(
            innhold = emptyList(),
            bakgrunnsinformasjon = input(MAX_BAKGRUNNSINFORMASJON_LENGDE * 2 + 1),
            deltakelsesprosent = null,
            dagerPerUke = null,
        )

        request.sanitize().bakgrunnsinformasjon!!.length shouldBe MAX_BAKGRUNNSINFORMASJON_LENGDE * 2
    }

    @Test
    fun `sanitize - annet beskrivelse er lengre enn max - avkorter teksten`() {
        val request = KladdRequest(
            innhold = listOf(
                InnholdRequest(
                    innholdskode = annetInnholdselement.innholdskode,
                    beskrivelse = input(MAX_ANNET_INNHOLD_LENGDE * 2 + 1),
                ),
            ),
            bakgrunnsinformasjon = null,
            deltakelsesprosent = null,
            dagerPerUke = null,
        )

        request
            .sanitize()
            .innhold[0]
            .beskrivelse!!
            .length shouldBe MAX_ANNET_INNHOLD_LENGDE * 2
    }

    @Test
    fun `sanitize - deltakelsesprosent er større enn max - runder ned til nærmest gyldige verdi`() {
        val request = KladdRequest(
            innhold = emptyList(),
            bakgrunnsinformasjon = null,
            deltakelsesprosent = MAX_DELTAKELSESPROSENT + 1,
            dagerPerUke = null,
        )

        request.sanitize().deltakelsesprosent shouldBe MAX_DELTAKELSESPROSENT
    }

    @Test
    fun `sanitize - deltakelsesprosent er mindre enn min - runder opp til nærmest gyldige verdi`() {
        val request = KladdRequest(
            innhold = emptyList(),
            bakgrunnsinformasjon = null,
            deltakelsesprosent = MIN_DELTAKELSESPROSENT - 1,
            dagerPerUke = null,
        )

        request.sanitize().deltakelsesprosent shouldBe MIN_DELTAKELSESPROSENT
    }

    @Test
    fun `sanitize - dagerPerUke er større enn max - runder ned til nærmest gyldige verdi`() {
        val request = KladdRequest(
            innhold = emptyList(),
            bakgrunnsinformasjon = null,
            deltakelsesprosent = null,
            dagerPerUke = MAX_DAGER_PER_UKE + 1,
        )

        request.sanitize().dagerPerUke shouldBe MAX_DAGER_PER_UKE
    }

    @Test
    fun `sanitize - dagerPerUke er mindre enn min - runder opp til nærmest gyldige verdi`() {
        val request = KladdRequest(
            innhold = emptyList(),
            bakgrunnsinformasjon = null,
            deltakelsesprosent = null,
            dagerPerUke = MIN_DAGER_PER_UKE - 1,
        )

        request.sanitize().dagerPerUke shouldBe MIN_DAGER_PER_UKE
    }
}
