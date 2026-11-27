package no.nav.amt.aktivitetskort.repositories

import no.nav.amt.aktivitetskort.domain.Oppfolgingsperiode
import no.nav.amt.aktivitetskort.utils.sqlParameters
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Periodenes start- og sluttdato brukes per 2026-11-27 ikke i applikasjonen, men er nyttige å kunne trekke ut ved feilsøking.
 * La stå inn til videre!
 * Se https://github.com/navikt/amt-deltakelser/pull/782#issuecomment-7212766883
 */
@Repository
class OppfolgingsperiodeRepository(
    private val template: NamedParameterJdbcTemplate,
) {
    private val rowMapper = RowMapper { rs, _ ->
        Oppfolgingsperiode(
            id = UUID.fromString(rs.getString("id")),
            startDato = rs.getTimestamp("start_dato").toLocalDateTime(),
            sluttDato = rs.getTimestamp("slutt_dato")?.toLocalDateTime(),
        )
    }

    fun upsert(oppfolgingsperiode: Oppfolgingsperiode): Oppfolgingsperiode = template
        .query(
            """
            INSERT INTO oppfolgingsperiode(id, start_dato, slutt_dato)
            VALUES (:id,
            		:start_dato,
            		:slutt_dato)
            ON CONFLICT (id) DO UPDATE SET start_dato = :start_dato, slutt_dato = :slutt_dato
            returning *
            """.trimIndent(),
            sqlParameters(
                "id" to oppfolgingsperiode.id,
                "start_dato" to oppfolgingsperiode.startDato,
                "slutt_dato" to oppfolgingsperiode.sluttDato,
            ),
            rowMapper,
        ).first()
}
