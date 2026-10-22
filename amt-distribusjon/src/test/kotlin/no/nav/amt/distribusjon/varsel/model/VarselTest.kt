package no.nav.amt.distribusjon.varsel.model

import io.kotest.matchers.shouldBe
import no.nav.amt.distribusjon.utils.data.HendelseTypeData
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.distribusjon.utils.data.Varselsdata
import org.junit.jupiter.api.Test
import java.util.UUID

class VarselTest {
    @Test
    fun `nyOppgave - bruker ekstern varsel ved opprettelse av utkast`() {
        val hendelse = Hendelsesdata.hendelse(HendelseTypeData.opprettUtkast())

        val oppgave = Varsel.nyOppgave(hendelse)

        oppgave.erEksterntVarsel shouldBe true
        oppgave.toOppgaveDto().has("eksternVarsling") shouldBe true
    }

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
