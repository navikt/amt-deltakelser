package no.nav.amt.aktivitetskort.database

import no.nav.amt.aktivitetskort.database.TestData.lagArrangor
import no.nav.amt.aktivitetskort.database.TestData.lagDeltaker
import no.nav.amt.aktivitetskort.database.TestData.lagDeltakerliste
import no.nav.amt.aktivitetskort.database.TestData.oppfolgingsperiode
import no.nav.amt.aktivitetskort.domain.Arrangor
import no.nav.amt.aktivitetskort.domain.Deltaker
import no.nav.amt.aktivitetskort.domain.Deltakerliste
import no.nav.amt.aktivitetskort.domain.Oppfolgingsperiode
import no.nav.amt.aktivitetskort.repositories.ArrangorRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerlisteRepository
import no.nav.amt.aktivitetskort.repositories.OppfolgingsperiodeRepository
import no.nav.amt.aktivitetskort.utils.RepositoryResult
import no.nav.amt.aktivitetskort.utils.sqlParameters
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TestDatabaseService(
    private val arrangorRepository: ArrangorRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val deltakerRepository: DeltakerRepository,
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
    private val oppfolgingsperiodeRepository: OppfolgingsperiodeRepository,
) {
    fun insertDeltaker(
        deltaker: Deltaker = lagDeltaker(),
        offset: Long = 0,
    ): Deltaker {
        insertDeltakerliste(lagDeltakerliste(id = deltaker.deltakerlisteId))
        return when (val result = deltakerRepository.upsert(deltaker, offset)) {
            is RepositoryResult.Created -> result.data
            is RepositoryResult.Modified -> result.data
            is RepositoryResult.NoChange -> deltaker
        }
    }

    fun insertAktivOppfolgingsperiode(id: UUID = UUID.randomUUID()): Oppfolgingsperiode =
        oppfolgingsperiodeRepository.upsert(oppfolgingsperiode(id))

    fun insertArrangor(arrangor: Arrangor = lagArrangor()) = when (val result = arrangorRepository.upsert(arrangor)) {
        is RepositoryResult.Created -> result.data
        is RepositoryResult.Modified -> result.data
        is RepositoryResult.NoChange -> arrangor
    }

    fun insertDeltakerliste(deltakerliste: Deltakerliste = lagDeltakerliste()): Deltakerliste {
        arrangorRepository.upsert(lagArrangor(id = deltakerliste.arrangorId))

        return when (val result = deltakerlisteRepository.upsert(deltakerliste)) {
            is RepositoryResult.Created -> result.data
            is RepositoryResult.Modified -> result.data
            is RepositoryResult.NoChange -> deltakerliste
        }
    }

    fun feilmeldingErLagret(key: UUID): Boolean = namedParameterJdbcTemplate.queryForObject(
        "SELECT exists(SELECT 1 from feilmelding where key = :key)",
        sqlParameters("key" to key),
        Boolean::class.java,
    ) ?: false
}
