package no.nav.amt.deltaker.utils.data

import kotliquery.queryOf
import no.nav.amt.deltaker.enkeltplass.EnkeltplassGjennomforingUpdateDbo
import no.nav.amt.deltaker.innbygger.NavBrukerRepository
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navtiltakskoordinator.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.DeltakerStatusRepository
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.repository.ImportertFraArenaRepository
import no.nav.amt.deltaker.repository.OpplaringKategoriseringRepoAdapter
import no.nav.amt.deltaker.repository.VedtakRepository
import no.nav.amt.deltaker.repository.dbo.GjennomforingInsertDbo
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorRepository
import no.nav.amt.deltaker.tiltaksarrangor.endring.EndringFraArrangorRepository
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.veileder.InnsokRepository
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringRepository
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Innsok
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

object TestRepository {
    fun insert(navAnsatt: NavAnsatt) {
        navAnsatt.navEnhetId?.let { navEnhetId ->
            NavEnhetRepository().upsert(lagNavEnhet(navEnhetId))
        }

        NavAnsattRepository().upsert(navAnsatt)
    }

    fun insert(bruker: NavBruker) {
        bruker.navEnhetId?.let { NavEnhetRepository().upsert(lagNavEnhet(it)) }
        bruker.navVeilederId?.let { NavAnsattRepository().upsert(lagNavAnsatt(id = it, navEnhetId = bruker.navEnhetId)) }

        NavBrukerRepository().upsert(bruker)
    }

    fun insert(
        deltakerliste: Deltakerliste,
        overordnetArrangor: Arrangor? = null,
    ) {
        TiltakRepository().upsert(deltakerliste.tiltakstype)
        overordnetArrangor?.let { ArrangorRepository().upsert(it) }
        ArrangorRepository().upsert(deltakerliste.arrangor!!)

        if (deltakerliste.gjennomforingstype == GjennomforingType.Enkeltplass) {
            DeltakerlisteRepository().upsert(deltakerliste.toGjennomforingInsertDbo())
            DeltakerlisteRepository().update(deltakerliste.toEnkeltplassUpdateDbo())
        } else {
            DeltakerlisteRepository().upsert(deltakerliste)
        }
    }

    fun insert(vedtak: Vedtak) {
        VedtakRepository().upsert(vedtak)

        Database.query { session ->
            session.update(
                queryOf(
                    "UPDATE vedtak SET modified_at = :sist_endret, created_at = :opprettet WHERE id = :id",
                    mapOf(
                        "id" to vedtak.id,
                        "sist_endret" to vedtak.sistEndret,
                        "opprettet" to vedtak.opprettet,
                    ),
                ),
            )
        }
    }

    fun insertKategoriseringer(
        deltakerlisteId: UUID,
        kategoriseringValg: OpplaringKategoriseringValg?,
    ) {
        OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
            gjennomforingId = deltakerlisteId,
            valgteVerdier = kategoriseringValg?.valgteKategoriseringer,
            valgteSertifiseringer = kategoriseringValg?.valgteSertifiseringer,
        )
    }

    fun insert(
        deltaker: Deltaker,
        vedtak: Vedtak? = null,
    ) {
        insert(deltaker.navBruker)
        insert(deltaker.deltakerliste)
        insertKategoriseringer(deltaker.deltakerliste.id, deltaker.deltakerliste.opplaringKategorisering)

        DeltakerRepository().upsert(deltaker)
        DeltakerStatusRepository.lagreStatus(deltaker.id, deltaker.status)
        vedtak?.let { insert(vedtak) }
    }

    fun <T> insertAll(vararg values: T) {
        values.forEach {
            when (it) {
                is NavAnsatt -> insert(it)
                is NavBruker -> insert(it)
                is NavEnhet -> NavEnhetRepository().upsert(it)
                is Arrangor -> ArrangorRepository().upsert(it)
                is Tiltakstype -> TiltakRepository().upsert(it)
                is Deltakerliste -> insert(it)
                is Deltaker -> insert(it)
                is Vedtak -> insert(it)
                is Forslag -> ForslagRepository().upsert(it)
                is DeltakerEndring -> DeltakerEndringRepository().upsert(it)
                is EndringFraArrangor -> EndringFraArrangorRepository().insert(it)
                is ImportertFraArena -> ImportertFraArenaRepository().upsert(it)
                is Innsok -> InnsokRepository().insert(it)
                is EndringFraTiltakskoordinator -> EndringFraTiltakskoordinatorRepository().insert(listOf(it))
                else -> NotImplementedError("insertAll for type ${it!!::class} er ikke implementert")
            }
        }
    }

    private fun Deltakerliste.toGjennomforingInsertDbo(): GjennomforingInsertDbo = GjennomforingInsertDbo(
        id = this.id,
        type = this.gjennomforingstype,
        tiltakId = this.tiltakstype.id,
        navn = this.navn,
        status = this.status,
        oppstart = this.oppstart,
        apentForPamelding = this.apentForPamelding,
        pameldingstype = this.pameldingstype,
    )

    fun getFremtidigeDeltakerStatuser(deltakerId: UUID): List<DeltakerStatus> = Database.query { session ->
        session.run(
            queryOf(
                """
                SELECT 
                    id, 
                    type, 
                    aarsak, 
                    gyldig_fra, 
                    gyldig_til, 
                    created_at 
                FROM deltaker_status 
                WHERE deltaker_id = ? AND gyldig_fra > CURRENT_TIMESTAMP
                """.trimIndent(),
                deltakerId,
            ).map { row ->
                DeltakerStatus(
                    id = row.uuid("id"),
                    type = DeltakerStatus.Type.valueOf(row.string("type")),
                    aarsak = row.stringOrNull("aarsak")?.let { aarsak -> objectMapper.readValue(aarsak) },
                    gyldigFra = row.localDateTime("gyldig_fra"),
                    gyldigTil = row.localDateTimeOrNull("gyldig_til"),
                    opprettet = row.localDateTime("created_at"),
                )
            }.asList,
        )
    }

    fun getNavAnsattByNavIdent(veilederIdenter: Set<String>): List<NavAnsatt> = if (veilederIdenter.isEmpty()) {
        emptyList()
    } else {
        Database.query { session ->
            session.run(
                queryOf(
                    """
                    SELECT
                        id, 
                        nav_ident, 
                        navn, 
                        telefonnummer, 
                        epost, 
                        nav_enhet_id 
                    FROM nav_ansatt 
                    WHERE nav_ident = ANY(:ider)
                    """.trimIndent(),
                    mapOf("ider" to veilederIdenter.toTypedArray()),
                ).map { row ->
                    NavAnsatt(
                        id = row.uuid("id"),
                        navIdent = row.string("nav_ident"),
                        navn = row.string("navn"),
                        epost = row.stringOrNull("epost"),
                        telefon = row.stringOrNull("telefonnummer"),
                        navEnhetId = row.uuidOrNull("nav_enhet_id"),
                    )
                }.asList,
            )
        }
    }
}

private fun Deltakerliste.toEnkeltplassUpdateDbo() = EnkeltplassGjennomforingUpdateDbo(
    id = this.id,
    arrangorId = this.arrangor?.id,
)
