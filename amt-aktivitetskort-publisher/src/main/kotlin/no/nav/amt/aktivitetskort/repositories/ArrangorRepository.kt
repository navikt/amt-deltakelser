package no.nav.amt.aktivitetskort.repositories

import no.nav.amt.aktivitetskort.domain.Arrangor
import no.nav.amt.aktivitetskort.utils.RepositoryResult
import no.nav.amt.aktivitetskort.utils.sqlParameters
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class ArrangorRepository(
    private val template: NamedParameterJdbcTemplate,
) {
    private val rowMapper = RowMapper { rs, _ ->
        Arrangor(
            id = UUID.fromString(rs.getString("id")),
            organisasjonsnummer = rs.getString("organisasjonsnummer"),
            navn = rs.getString("navn"),
            overordnetArrangorId = rs.getString("overordnet_arrangor_id")?.let { UUID.fromString(it) },
        )
    }

    fun upsert(arrangor: Arrangor): RepositoryResult<Arrangor> {
        val old = get(arrangor.id)

        if (old == arrangor) return RepositoryResult.NoChange()

        val new = template
            .query(
                """
                INSERT INTO arrangor(id, organisasjonsnummer, navn, overordnet_arrangor_id)
                VALUES (:id,
                		:organisasjonsnummer,
                		:navn,
                		:overordnet_arrangor_id)
                ON CONFLICT (id) DO UPDATE SET
                	navn = :navn,
                	organisasjonsnummer = :organisasjonsnummer,
                	overordnet_arrangor_id = :overordnet_arrangor_id
                RETURNING *
                """.trimIndent(),
                sqlParameters(
                    "id" to arrangor.id,
                    "navn" to arrangor.navn,
                    "organisasjonsnummer" to arrangor.organisasjonsnummer,
                    "overordnet_arrangor_id" to arrangor.overordnetArrangorId,
                ),
                rowMapper,
            ).first()

        if (old == null) return RepositoryResult.Created(new)

        return RepositoryResult.Modified(new)
    }

    fun get(id: UUID): Arrangor? = template
        .query(
            "SELECT * from arrangor where id = :id",
            sqlParameters("id" to id),
            rowMapper,
        ).firstOrNull()

    fun getUnderordnedeArrangorer(overordnetArrangorId: UUID): List<Arrangor> = template.query(
        "SELECT * from arrangor where overordnet_arrangor_id = :overordnet_arrangor_id",
        sqlParameters("overordnet_arrangor_id" to overordnetArrangorId),
        rowMapper,
    )

    fun get(organisasjonsnummer: String) = template
        .query(
            "SELECT * from arrangor where organisasjonsnummer = :organisasjonsnummer",
            sqlParameters("organisasjonsnummer" to organisasjonsnummer),
            rowMapper,
        ).firstOrNull()
}
