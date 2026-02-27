package no.nav.amt.deltaker.deltaker.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Query
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.deltaker.db.DbUtils.nullWhenNearNow
import no.nav.amt.deltaker.deltaker.db.DbUtils.sqlPlaceholders
import no.nav.amt.deltaker.deltaker.model.IKKE_AVSLUTTENDE_STATUSER
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

object DeltakerStatusRepository {
    /**
     * Setter inn en ny rad i tabellen `deltaker_status`.
     *
     * - Feltet `gyldig_fra` settes enten til verdien fra [DeltakerStatus.gyldigFra], eller til
     *   `CURRENT_TIMESTAMP` i databasen dersom tidspunktet er "nær nok" nåværende tidspunkt
     *   (se [nullWhenNearNow]).
     *
     * - Feltet `created_at` settes tilsvarende, basert på [DeltakerStatus.opprettet].
     *
     * - Konflikter på primærnøkkelen `id` ignoreres (`ON CONFLICT (id) DO NOTHING`).
     *
     * @param deltakerStatus status-objektet som skal lagres i databasen.
     * @param deltakerId ID-en til deltakeren statusen tilhører.
     * @return en ferdig parametrisert [Query] som kan kjøres mot databasen.
     */
    fun lagreStatus(deltakerId: UUID, deltakerStatus: DeltakerStatus) {
        val sql =
            """
            INSERT INTO deltaker_status (
                id, 
                deltaker_id, 
                type, 
                aarsak, 
                gyldig_til,
                gyldig_fra, 
                created_at
            )
            VALUES (
                :id, 
                :deltaker_id, 
                :type, 
                :aarsak,
                :gyldig_til,
                COALESCE(:gyldig_fra, CURRENT_TIMESTAMP), 
                COALESCE(:created_at, CURRENT_TIMESTAMP)
            )
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()

        val params = mapOf(
            "id" to deltakerStatus.id,
            "deltaker_id" to deltakerId,
            "type" to deltakerStatus.type.name,
            "aarsak" to toPGObject(deltakerStatus.aarsak),
            "gyldig_til" to deltakerStatus.gyldigTil,
            "gyldig_fra" to nullWhenNearNow(deltakerStatus.gyldigFra),
            "created_at" to nullWhenNearNow(deltakerStatus.opprettet),
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    /**
     * Deaktiverer (setter `gyldig_til` til nåværende tidspunkt) alle aktive statuser for en deltaker,
     * med unntak av én spesifisert status.
     *
     * En status regnes som aktiv dersom `gyldig_til` er `NULL`. Metoden oppdaterer alle slike statuser
     * for gitt [deltakerId], bortsett fra statusen med [excludeStatusId], og setter:
     * - `gyldig_til` = CURRENT_TIMESTAMP
     * - `modified_at` = CURRENT_TIMESTAMP
     *
     * En status blir kun deaktivert dersom ett av følgende vilkår er oppfylt:
     * - [erDeltakerSluttdatoEndret] er `true`, eller
     * - statusens `gyldig_fra` er tidligere enn nåværende tidspunkt, eller
     * - statusens `type` ikke er blant de avsluttende statusene.
     *
     * Dette sikrer at vi lukker tidligere relevante statuser når en ny status opprettes
     * eller når deltakerens sluttdato endres, samtidig som vi unngår å deaktivere
     * den eksplisitt angitte statusen.
     *
     * @param deltakerId ID til deltakeren statusene tilhører.
     * @param excludeStatusId ID til statusen som ikke skal deaktiveres.
     * @param erDeltakerSluttdatoEndret Angir om deltakerens sluttdato er endret,
     * og dermed om alle aktive statuser skal deaktiveres uavhengig av øvrige vilkår.
     */
    fun deaktiverTidligereStatuser(
        deltakerId: UUID,
        excludeStatusId: UUID,
        erDeltakerSluttdatoEndret: Boolean,
    ) {
        val sql =
            """
            UPDATE deltaker_status
            SET 
                gyldig_til = CURRENT_TIMESTAMP,
                modified_at = CURRENT_TIMESTAMP
            WHERE 
                deltaker_id = :deltaker_id
                AND id != :exclude_id
                AND gyldig_til IS NULL
                AND (
                    :er_deltaker_sluttdato_endret IS TRUE     
                    OR                                      
                    gyldig_fra < CURRENT_TIMESTAMP
                    OR
                    type IN ($IKKE_AVSLUTTENDE_STATUSER_AS_DELIMITED_STRING)
                )
            """.trimIndent()

        return Database.query { session ->
            session.update(
                queryOf(
                    sql,
                    mapOf(
                        "deltaker_id" to deltakerId,
                        "exclude_id" to excludeStatusId,
                        "er_deltaker_sluttdato_endret" to erDeltakerSluttdatoEndret,
                    ),
                ),
            )
        }
    }

    private val IKKE_AVSLUTTENDE_STATUSER_AS_DELIMITED_STRING = IKKE_AVSLUTTENDE_STATUSER
        .joinToString { "'${it.name}'" }

    fun slettTidligereFremtidigeStatuser(deltakerId: UUID, excludeStatusId: UUID) {
        val sql =
            """
            DELETE FROM deltaker_status
            WHERE 
                deltaker_id = :deltaker_id 
                AND id != :exclude_id 
                AND gyldig_til IS NULL
                AND gyldig_fra > CURRENT_TIMESTAMP
            """.trimIndent()

        return Database.query { session ->
            session.update(
                queryOf(
                    sql,
                    mapOf(
                        "exclude_id" to excludeStatusId,
                        "deltaker_id" to deltakerId,
                    ),
                ),
            )
        }
    }

    fun slettStatus(deltakerId: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM deltaker_status WHERE deltaker_id = :deltaker_id",
                    mapOf("deltaker_id" to deltakerId),
                ),
            )
        }
    }

    /**
     * Henter alle deltakerstatuser som representerer en avslutning
     * (f.eks. `AVBRUTT`, `FULLFORT` eller `HAR_SLUTTET`) og som er gyldige for oppdatering.
     *
     * Spørringen fungerer slik:
     * - Først finner vi alle deltakere som har en aktiv status av typen `DELTAR` (dvs. uten `gyldig_til`).
     * - Deretter henter vi alle statuser knyttet til disse deltakerne som:
     *   - ikke har en avslutningsdato (`gyldig_til IS NULL`),
     *   - har startet (`gyldig_fra <= dagens dato`),
     *   - og er en av de avsluttende statusene (`AVBRUTT`, `FULLFORT`, `HAR_SLUTTET`).
     *
     * @return en liste av [DeltakerStatusMedDeltakerId] som inneholder både deltaker-id og
     *         den tilhørende avsluttende statusen som bør oppdateres.
     */
    fun getAvsluttendeDeltakerStatuserForOppdatering(deltakerIder: Set<UUID>): List<DeltakerStatusMedDeltakerId> {
        if (deltakerIder.isEmpty()) return emptyList()

        val sql =
            """
            SELECT ds.* 
            FROM deltaker_status ds 
            WHERE 
                ds.deltaker_id IN (${sqlPlaceholders(deltakerIder.size)})
                AND ds.gyldig_til IS NULL 
                AND ds.gyldig_fra < current_date + interval '1 day' 
                AND ds.type IN ('AVBRUTT', 'FULLFORT', 'HAR_SLUTTET') 
                AND EXISTS ( 
                    SELECT 1 
                    FROM deltaker_status AS inner_ds 
                    WHERE 
                        inner_ds.deltaker_id = ds.deltaker_id 
                        AND inner_ds.gyldig_til IS NULL 
                        AND inner_ds.type = 'DELTAR'
                )
            """.trimIndent()

        val query = queryOf(
            sql,
            *deltakerIder.toTypedArray(),
        ).map {
            DeltakerStatusMedDeltakerId(
                deltakerId = it.uuid("deltaker_id"),
                deltakerStatus = deltakerStatusRowMapper(it),
            )
        }.asList

        return Database.query { session -> session.run(query) }
    }

    // benyttes kun i tester
    internal fun get(deltakerStatusId: UUID): DeltakerStatus = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM deltaker_status WHERE id = ?",
                deltakerStatusId,
            ).map(::deltakerStatusRowMapper).asSingle,
        ) ?: throw NoSuchElementException("Fant ikke deltakerstatus med id $deltakerStatusId")
    }

    // benyttes kun i tester
    internal fun getFremtidige(deltakerId: UUID): List<DeltakerStatus> = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM deltaker_status WHERE deltaker_id = ? AND gyldig_fra > CURRENT_TIMESTAMP",
                deltakerId,
            ).map(::deltakerStatusRowMapper).asList,
        )
    }

    private fun deltakerStatusRowMapper(row: Row) = DeltakerStatus(
        id = row.uuid("id"),
        type = row.string("type").let { t -> DeltakerStatus.Type.valueOf(t) },
        aarsak = row.stringOrNull("aarsak")?.let { aarsak -> objectMapper.readValue(aarsak) },
        gyldigFra = row.localDateTime("gyldig_fra"),
        gyldigTil = row.localDateTimeOrNull("gyldig_til"),
        opprettet = row.localDateTime("created_at"),
    )
}
