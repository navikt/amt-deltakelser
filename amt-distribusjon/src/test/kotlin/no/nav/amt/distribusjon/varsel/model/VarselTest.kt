package no.nav.amt.distribusjon.varsel.model

import io.kotest.matchers.shouldBe
import no.nav.amt.distribusjon.utils.data.Varselsdata
import org.junit.jupiter.api.Test
import java.util.UUID

class VarselTest {
    @Test
    fun `merge - bevarer revarseltilstand når eksternt revarsel slås sammen`() {
        // Arrange
        val originalVarselId = UUID.randomUUID()
        val ventendeVarsel = Varselsdata.beskjed()
        val revarsel = Varselsdata.beskjed(
            revarselForVarsel = originalVarselId,
            revarsles = null,
        )

        revarsel.erEksterntVarsel shouldBe true
        revarsel.erRevarsel shouldBe true

        // Act
        val varsel = ventendeVarsel.merge(revarsel)

        // Assert
        varsel.revarselForVarsel shouldBe originalVarselId
        varsel.revarsles shouldBe revarsel.revarsles
        varsel.erRevarsel shouldBe revarsel.erRevarsel
        varsel.kanRevarsles shouldBe false
    }

    @Test
    fun `merge - planlegger revarsling når ekstern beskjed slås sammen`() {
        // Arrange
        val ventendeVarsel = Varselsdata.beskjed()
        val nyttVarsel = Varselsdata.beskjed(revarsles = null)

        // Act
        val varsel = ventendeVarsel.merge(nyttVarsel)

        // Assert
        varsel.erRevarsel shouldBe false
        varsel.kanRevarsles shouldBe true
    }
}
