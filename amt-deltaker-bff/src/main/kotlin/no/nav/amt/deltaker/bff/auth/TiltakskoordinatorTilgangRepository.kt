package no.nav.amt.deltaker.bff.auth

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteService
import no.nav.amt.deltaker.bff.tiltakskoordinator.model.Tiltakskoordinator
import no.nav.amt.lib.utils.database.Database
import java.time.LocalDate
import java.util.UUID

class TiltakskoordinatorTilgangRepository {
    fun upsert(tilgang: TiltakskoordinatorDeltakerlisteTilgang): Result<TiltakskoordinatorDeltakerlisteTilgang> =
        runCatching {
            val sql =
                """
                INSERT INTO tiltakskoordinator_deltakerliste_tilgang (
                    id, 
                    nav_ansatt_id, 
                    deltakerliste_id, 
                    gyldig_fra, 
                    gyldig_til
                )
                VALUES (
                    :id, 
                    :nav_ansatt_id, 
                    :deltakerliste_id, 
                    :gyldig_fra, 
                    :gyldig_til
                )
                ON CONFLICT (id) DO UPDATE SET 
                    id               = :id,
                    nav_ansatt_id    = :nav_ansatt_id,
                    deltakerliste_id = :deltakerliste_id,
                    gyldig_fra       = :gyldig_fra,
                    gyldig_til       = :gyldig_til,
                    modified_at      = CURRENT_TIMESTAMP
                RETURNING *
                """.trimIndent()

            val params =
                mapOf(
                    "id" to tilgang.id,
                    "nav_ansatt_id" to tilgang.navAnsattId,
                    "deltakerliste_id" to tilgang.deltakerlisteId,
                    "gyldig_fra" to tilgang.gyldigFra,
                    "gyldig_til" to tilgang.gyldigTil,
                )

            Database.query { session ->
                session.run(queryOf(sql, params).map(::rowMapper).asSingle)
                    ?: throw IllegalStateException("Noe gikk galt med upsert av tiltakskoordinator tilgang med id ${tilgang.id}")
            }
        }

    fun hentAktivTilgang(
        navAnsattId: UUID,
        deltakerlisteId: UUID,
    ): Result<TiltakskoordinatorDeltakerlisteTilgang> =
        runCatching {
            val sql =
                """
                SELECT *
                FROM tiltakskoordinator_deltakerliste_tilgang
                WHERE 
                    nav_ansatt_id = :nav_ansatt_id
                    AND deltakerliste_id = :deltakerliste_id
                    AND gyldig_til IS NULL
                """.trimIndent()

            val params =
                mapOf(
                    "nav_ansatt_id" to navAnsattId,
                    "deltakerliste_id" to deltakerlisteId,
                )

            Database.query { session ->
                session.run(queryOf(sql, params).map(::rowMapper).asSingle)
                    ?: throw NoSuchElementException(
                        "Fant ikke aktiv tilgang for Nav-ansatt: $navAnsattId og deltakerliste: $deltakerlisteId",
                    )
            }
        }

    /**
     * Henter alle tiltakskoordinatorer som er knyttet til en gitt deltakerliste.
     *
     * Metoden slår opp i databasen etter koordinator-tilganger for deltakerlisten,
     * og returnerer en liste med distinkte koordinatorer. Hver koordinator får
     * et flagg som angir om tilgangen er aktiv, samt om vedkommende kan fjernes
     * (satt dersom koordinatoren er den samme som den påloggede Nav-ansatte).
     *
     * @param deltakerlisteId ID til deltakerlisten koordinatorene skal hentes for
     * @param paaloggetNavAnsattId ID til Nav-ansatt som er pålogget og gjør spørringen
     * @return en liste av [Tiltakskoordinator]-objekter med tilhørende statusinformasjon
     */
    fun hentKoordinatorer(
        deltakerlisteId: UUID,
        paaloggetNavAnsattId: UUID,
    ): List<Tiltakskoordinator> {
        val sql =
            """
            SELECT DISTINCT ON (nav_ansatt.id)
                nav_ansatt.id,
                nav_ansatt.navn,
                (tilgang.gyldig_til IS NULL) AS er_aktiv
            FROM 
                tiltakskoordinator_deltakerliste_tilgang tilgang
                JOIN nav_ansatt ON nav_ansatt.id = tilgang.nav_ansatt_id
            WHERE tilgang.deltakerliste_id = :deltakerliste_id
            ORDER BY 
                nav_ansatt.id, 
                tilgang.gyldig_til DESC NULLS FIRST
            """.trimIndent()

        val query =
            queryOf(
                statement = sql,
                paramMap = mapOf("deltakerliste_id" to deltakerlisteId),
            ).map {
                val navAnsattIdFraDb = it.uuid("id")

                Tiltakskoordinator(
                    id = navAnsattIdFraDb,
                    navn = it.string("navn"),
                    erAktiv = it.boolean("er_aktiv"),
                    kanFjernes = (paaloggetNavAnsattId == navAnsattIdFraDb),
                )
            }.asList

        return Database.query { session -> session.run(query) }
    }

    fun get(id: UUID): Result<TiltakskoordinatorDeltakerlisteTilgang> =
        runCatching {
            Database.query { session ->
                session.run(
                    queryOf(
                        "SELECT * FROM tiltakskoordinator_deltakerliste_tilgang WHERE id = :id",
                        mapOf("id" to id),
                    ).map(::rowMapper).asSingle,
                ) ?: throw NoSuchElementException("Fant ikke tilgang med id $id")
            }
        }

    fun hentUtdaterteTilganger(): List<TiltakskoordinatorDeltakerlisteTilgang> {
        val grense = LocalDate.now().minus(DeltakerlisteService.tiltakskoordinatorGraceperiode)
        val sql =
            """
            SELECT
                t.id,
                t.nav_ansatt_id,
                t.deltakerliste_id,
                t.gyldig_fra,
                t.gyldig_til
            FROM 
                tiltakskoordinator_deltakerliste_tilgang t 
                JOIN deltakerliste dl ON t.deltakerliste_id = dl.id
            WHERE 
                t.gyldig_til IS NULL 
                AND dl.slutt_dato < :grense 
            """.trimIndent()

        return Database.query { session ->
            session.run(queryOf(sql, mapOf("grense" to grense)).map(::rowMapper).asList)
        }
    }

    fun hentAktiveForDeltakerliste(deltakerlisteId: UUID): List<TiltakskoordinatorDeltakerlisteTilgang> {
        val sql =
            """
            SELECT
                t.id,
                t.nav_ansatt_id,
                t.deltakerliste_id,
                t.gyldig_fra,
                t.gyldig_til
            FROM 
                tiltakskoordinator_deltakerliste_tilgang t 
                JOIN deltakerliste dl ON t.deltakerliste_id = dl.id
            WHERE 
                dl.id = :deltakerliste_id 
                AND t.gyldig_til IS NULL 
            """

        return Database.query { session ->
            session.run(queryOf(sql, mapOf("deltakerliste_id" to deltakerlisteId)).map(::rowMapper).asList)
        }
    }

    companion object {
        private fun rowMapper(row: Row): TiltakskoordinatorDeltakerlisteTilgang =
            TiltakskoordinatorDeltakerlisteTilgang(
                id = row.uuid("id"),
                navAnsattId = row.uuid("nav_ansatt_id"),
                deltakerlisteId = row.uuid("deltakerliste_id"),
                gyldigFra = row.localDateTime("gyldig_fra"),
                gyldigTil = row.localDateTimeOrNull("gyldig_til"),
            )
    }
}
