package no.nav.amt.lib.models.person.dto

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.testing.testdata.person.PersonDtoTestsData.ansattDtoInTest
import org.junit.jupiter.api.Test

class NavAnsattDtoTest {
    @Test
    fun `Skal kunne mappe en NavAnsattDto til NavAnsatt nar alle verdier er satt`() {
        val navAnsatt = ansattDtoInTest.toModel()

        assertSoftly(navAnsatt) {
            id shouldBe ansattDtoInTest.id
            navIdent shouldBe ansattDtoInTest.navident
            navn shouldBe ansattDtoInTest.navn
            epost shouldBe ansattDtoInTest.epost
            telefon shouldBe ansattDtoInTest.telefon
            navEnhetId shouldBe ansattDtoInTest.navEnhetId
        }
    }

    @Test
    fun `Skal kunne mappe en NavAnsattDto til NavAnsatt nar epost og telefon er null`() {
        val navAnsatt = ansattDtoInTest
            .copy(
                epost = null,
                telefon = null,
            ).toModel()

        assertSoftly(navAnsatt) {
            epost.shouldBeNull()
            telefon.shouldBeNull()
        }
    }
}
