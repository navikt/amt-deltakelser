package no.nav.amt.deltaker.deltakerliste

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.repository.DeltakerlisteStengtException
import no.nav.amt.deltaker.repository.dbo.GjennomforingInsertDbo
import no.nav.amt.deltaker.tiltak.TiltakRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class DeltakerlisteRepositoryTest {
    private val deltakerlisteRepository = DeltakerlisteRepository()
    private val arrangorRepository = ArrangorRepository()
    private val tiltakRepository = TiltakRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class Upsert {
        @Test
        fun `ny minimal deltakerliste - inserter`() {
            val arrangor = lagArrangor()
            arrangorRepository.upsert(arrangor)

            val tiltakstype = lagTiltakstype()
            tiltakRepository.upsert(tiltakstype)

            val deltakerliste = lagDeltakerliste(
                arrangor = arrangor,
                tiltakstype = tiltakstype,
            ).copy(
                startDato = null,
                sluttDato = null,
            )

            deltakerlisteRepository.upsert(deltakerliste)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe deltakerliste
        }

        @Test
        fun `ny deltakerliste - inserter`() {
            val arrangor = lagArrangor()
            val tiltakstype = lagTiltakstype()
            val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)

            arrangorRepository.upsert(arrangor)
            tiltakRepository.upsert(tiltakstype)

            deltakerlisteRepository.upsert(deltakerliste)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe deltakerliste
        }

        @Test
        fun `ny deltakerliste enkeltplass kladd - inserter`() {
            val arrangor = lagArrangor()
            val tiltakstype = lagTiltakstype()
            val deltakerliste = lagDeltakerliste(
                arrangor = null,
                tiltakstype = tiltakstype,
                status = GjennomforingStatusType.KLADD,
                oppstart = Oppstartstype.LOPENDE,
                gjennomforingstype = GjennomforingType.Enkeltplass,
                startDato = null,
                sluttDato = null,
                oppmoteSted = null,
                opplaringKategorisering = OpplaringKategoriseringValg(
                    valgteKategoriseringer = emptySet(),
                    valgteSertifiseringer = emptySet(),
                ),
            )

            val gjennomforingdbo = GjennomforingInsertDbo(
                id = deltakerliste.id,
                type = deltakerliste.gjennomforingstype,
                tiltakId = tiltakstype.id,
                navn = deltakerliste.navn,
                status = deltakerliste.status,
                oppstart = deltakerliste.oppstart,
                apentForPamelding = deltakerliste.apentForPamelding,
                pameldingstype = deltakerliste.pameldingstype,
            )

            arrangorRepository.upsert(arrangor)
            tiltakRepository.upsert(tiltakstype)

            deltakerlisteRepository.upsert(gjennomforingdbo)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe deltakerliste
        }

        @Test
        fun `ny deltakerliste kladd - inserter`() {
            val arrangor = lagArrangor()
            val tiltakstype = lagTiltakstype()
            val deltakerliste = lagDeltakerliste(
                arrangor = null,
                tiltakstype = tiltakstype,
                status = GjennomforingStatusType.KLADD,
                oppstart = Oppstartstype.LOPENDE,
                gjennomforingstype = GjennomforingType.Gruppe,
                startDato = null,
                sluttDato = null,
                oppmoteSted = null,
                opplaringKategorisering = null,
            )

            val gjennomforingdbo = GjennomforingInsertDbo(
                id = deltakerliste.id,
                type = deltakerliste.gjennomforingstype,
                tiltakId = tiltakstype.id,
                navn = deltakerliste.navn,
                status = deltakerliste.status,
                oppstart = deltakerliste.oppstart,
                apentForPamelding = deltakerliste.apentForPamelding,
                pameldingstype = deltakerliste.pameldingstype,
            )

            arrangorRepository.upsert(arrangor)
            tiltakRepository.upsert(tiltakstype)

            deltakerlisteRepository.upsert(gjennomforingdbo)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe deltakerliste
        }

        @Test
        fun `deltakerliste ny sluttdato - oppdaterer`() {
            val arrangor = lagArrangor()
            val tiltakstype = lagTiltakstype()
            val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)

            arrangorRepository.upsert(arrangor)
            tiltakRepository.upsert(tiltakstype)
            deltakerlisteRepository.upsert(deltakerliste)

            val oppdatertListe = deltakerliste.copy(sluttDato = LocalDate.now())

            deltakerlisteRepository.upsert(oppdatertListe)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe oppdatertListe
        }
    }

    @Test
    fun `delete - sletter deltakerliste`() {
        val arrangor = lagArrangor()
        val tiltakstype = lagTiltakstype()
        val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)
        arrangorRepository.upsert(arrangor)

        tiltakRepository.upsert(tiltakstype)
        deltakerlisteRepository.upsert(deltakerliste)

        deltakerlisteRepository.delete(deltakerliste.id)

        deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe null
    }

    @Test
    fun `get - deltakerliste og arrangor finnes - henter deltakerliste`() {
        val arrangor = lagArrangor()
        val tiltakstype = lagTiltakstype()
        val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)

        arrangorRepository.upsert(arrangor)
        tiltakRepository.upsert(tiltakstype)
        deltakerlisteRepository.upsert(deltakerliste)

        val deltakerlisteMedArrangor = deltakerlisteRepository.get(deltakerliste.id).getOrThrow()

        deltakerlisteMedArrangor.navn shouldBe deltakerliste.navn
        deltakerlisteMedArrangor.arrangor.shouldNotBeNull().navn shouldBe arrangor.navn
    }

    @Nested
    inner class VerifiserTilgjengeligDeltakerliste {
        @Test
        fun `deltakerliste er åpen - returnerer deltakerliste`() {
            val arrangor = lagArrangor()
            val tiltakstype = lagTiltakstype()
            val deltakerliste = lagDeltakerliste(arrangor = arrangor, tiltakstype = tiltakstype)

            arrangorRepository.upsert(arrangor)
            tiltakRepository.upsert(tiltakstype)
            deltakerlisteRepository.upsert(deltakerliste)

            deltakerlisteRepository.verifiserTilgjengeligDeltakerliste(deltakerliste.id) shouldBe deltakerliste
        }

        @Test
        fun `deltakerliste er stengt - kaster exception`() {
            val arrangor = lagArrangor()
            val tiltakstype = lagTiltakstype()
            val deltakerliste = lagDeltakerliste(
                arrangor = arrangor,
                tiltakstype = tiltakstype,
                sluttDato = LocalDate.now().minus(DeltakerlisteRepository.tiltakskoordinatorGraceperiode).minusDays(1),
            )

            arrangorRepository.upsert(arrangor)
            tiltakRepository.upsert(tiltakstype)
            deltakerlisteRepository.upsert(deltakerliste)

            shouldThrow<DeltakerlisteStengtException> {
                deltakerlisteRepository.verifiserTilgjengeligDeltakerliste(deltakerliste.id)
            }
        }
    }
}
