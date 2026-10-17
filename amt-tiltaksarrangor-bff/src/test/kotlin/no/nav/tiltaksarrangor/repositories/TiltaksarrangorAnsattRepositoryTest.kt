package no.nav.tiltaksarrangor.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import org.junit.jupiter.api.Test
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.SqlParameterSource
import java.util.UUID

class TiltaksarrangorAnsattRepositoryTest {
    @Test
    fun `databasefeil ved lagring av ansatt kastes videre med original type`() {
        // Arrange
        val databaseFeil = PessimisticLockingFailureException("Database er utilgjengelig")
        val template = mockk<NamedParameterJdbcTemplate>()
        every {
            template.update(any<String>(), any<SqlParameterSource>())
        } throws databaseFeil
        val repository = TiltaksarrangorAnsattRepository(template)
        val ansatt = AnsattDbo(
            id = UUID.randomUUID(),
            personIdent = "test-ident",
            fornavn = "Test",
            mellomnavn = null,
            etternavn = "Ansatt",
            roller = emptyList(),
            deltakerlister = emptyList(),
            veilederDeltakere = emptyList(),
        )

        // Act
        val kastetFeil = shouldThrow<PessimisticLockingFailureException> {
            repository.insertOrUpdateAnsatt(ansatt)
        }

        // Assert
        kastetFeil shouldBe databaseFeil
    }
}
