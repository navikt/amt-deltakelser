package no.nav.amt.deltaker.bff.veileder.api.utils

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestData.input
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerModel
import no.nav.amt.deltaker.bff.utils.TestData.lagGjennomforingResponse
import no.nav.amt.deltaker.bff.veileder.api.request.EndreBakgrunnsinformasjonRequest
import no.nav.amt.deltaker.bff.veileder.api.request.EndreDeltakelsesmengdeRequest
import no.nav.amt.internapi.deltaker.annetInnholdselement
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengdeResponse
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengderResponse
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.utils.TestData.lagDeltakerRegistreringInnhold
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class InputvalideringTest {
    @Test
    fun testValiderBakgrunnsinformasjon() {
        val forLang = input(MAX_BAKGRUNNSINFORMASJON_LENGDE + 1)
        val ok = input(MAX_BAKGRUNNSINFORMASJON_LENGDE - 1)

        shouldThrow<IllegalArgumentException> {
            validerBakgrunnsinformasjon(forLang)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerBakgrunnsinformasjon(ok)
        }
    }

    @Test
    fun testValiderAnnetInnhold() {
        val forLang = input(MAX_ANNET_INNHOLD_LENGDE + 1)
        val ok = input(MAX_ANNET_INNHOLD_LENGDE - 1)

        shouldThrow<IllegalArgumentException> {
            validerAnnetInnhold(forLang, Tiltakskode.ARBEIDSFORBEREDENDE_TRENING)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerAnnetInnhold(ok, Tiltakskode.ARBEIDSFORBEREDENDE_TRENING)
        }
        shouldThrow<IllegalArgumentException> {
            validerAnnetInnhold(null, Tiltakskode.ARBEIDSFORBEREDENDE_TRENING)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerAnnetInnhold(null, Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerAnnetInnhold(null, Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER)
        }
    }

    @Test
    fun testValiderAarsaksBeskrivelse() {
        val forLang = input(MAX_AARSAK_BESKRIVELSE_LENGDE + 1)
        val ok = input(MAX_AARSAK_BESKRIVELSE_LENGDE - 1)

        shouldThrow<IllegalArgumentException> {
            validerAarsaksBeskrivelse(forLang)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerAarsaksBeskrivelse(ok)
        }
    }

    @Test
    fun testValiderDagerPerUke() {
        shouldThrow<IllegalArgumentException> {
            validerDagerPerUke(MIN_DAGER_PER_UKE - 1)
        }
        shouldThrow<IllegalArgumentException> {
            validerDagerPerUke(MAX_DAGER_PER_UKE + 1)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDagerPerUke(MIN_DAGER_PER_UKE)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDagerPerUke(MAX_DAGER_PER_UKE)
        }
    }

    @Test
    fun testValiderDeltakelsesProsent() {
        shouldThrow<IllegalArgumentException> {
            validerDeltakelsesProsent(MIN_DELTAKELSESPROSENT - 1)
        }
        shouldThrow<IllegalArgumentException> {
            validerDeltakelsesProsent(MAX_DELTAKELSESPROSENT + 1)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakelsesProsent(MIN_DELTAKELSESPROSENT)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakelsesProsent(MAX_DELTAKELSESPROSENT)
        }
    }

    @Test
    fun testValiderDeltakelsesinnhold() {
        val tiltaksinnhold = lagDeltakerRegistreringInnhold(
            innholdselementer = listOf(
                Innholdselement("Type", "type"),
            ),
        )
        val tiltakstype = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING

        shouldThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(listOf(InnholdsElementRequest("type", null)), null, tiltakstype)
        }
        shouldThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(
                listOf(InnholdsElementRequest("type", null)),
                lagDeltakerRegistreringInnhold(innholdselementer = emptyList()),
                tiltakstype,
            )
        }
        shouldThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(emptyList(), tiltaksinnhold, tiltakstype)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(emptyList(), null, tiltakstype)
        }
        shouldThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(listOf(InnholdsElementRequest("foo", null)), tiltaksinnhold, tiltakstype)
        }
        shouldThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(listOf(InnholdsElementRequest(annetInnholdselement.innholdskode, null)), tiltaksinnhold, tiltakstype)
        }
        shouldThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(listOf(InnholdsElementRequest(annetInnholdselement.innholdskode, "")), tiltaksinnhold, tiltakstype)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(
                listOf(InnholdsElementRequest(annetInnholdselement.innholdskode, "annet innhold må ha beskrivelse")),
                tiltaksinnhold,
                tiltakstype,
            )
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(listOf(InnholdsElementRequest("type", null)), tiltaksinnhold, tiltakstype)
        }
        shouldThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(
                listOf(InnholdsElementRequest("type", "andre typer enn annet skal ikke ha beskrivelse")),
                tiltaksinnhold,
                tiltakstype,
            )
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(
                listOf(InnholdsElementRequest(annetInnholdselement.innholdskode, "annet er tillatt for VTA")),
                DeltakerRegistreringInnhold(emptyList(), "Ledetekst"),
                Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
            )
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(
                emptyList(),
                DeltakerRegistreringInnhold(emptyList(), "Ledetekst"),
                Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
            )
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(
                emptyList(),
                DeltakerRegistreringInnhold(emptyList(), "Ledetekst"),
                Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK,
            )
        }
    }

    @Test
    fun testValiderDeltakelsesInnhold() {
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(
                listOf(InnholdsElementRequest(annetInnholdselement.innholdskode, "annet er tillatt for VTA")),
                DeltakerRegistreringInnhold(emptyList(), "Ledetekst"),
                Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
            )
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(
                emptyList(),
                DeltakerRegistreringInnhold(emptyList(), "Ledetekst"),
                Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
            )
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(
                emptyList(),
                DeltakerRegistreringInnhold(emptyList(), "Ledetekst"),
                Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK,
            )
        }
    }

    @Test
    fun testValiderDeltakerKanEndres() {
        val deltakerDeltar = TestData.lagDeltakerOld(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusMonths(6),
        )
        val deltakerSluttetFireUkerSiden = TestData.lagDeltakerOld(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = LocalDate.now().minusWeeks(4),
        )
        val deltakerSluttetFireMndSiden = TestData.lagDeltakerOld(
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusMonths(4),
            ),
            sluttdato = LocalDate.now().minusMonths(4),
        )
        val deltakerIkkeAktuellFireMndSiden = TestData.lagDeltakerOld(
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.IKKE_AKTUELL,
                gyldigFra = LocalDateTime.now().minusMonths(4),
            ),
            sluttdato = null,
        )
        // Låst pga. nyere deltakelse på samme tiltak, men avsluttet nylig – skal være tillatt
        val deltakerLaastSluttetFireUkerSiden = deltakerSluttetFireUkerSiden.copy(kanEndres = false)
        // Låst OG avsluttet for mer enn 2 måneder siden – skal fortsatt feile
        val deltakerLaastSluttetFireMndSiden = deltakerSluttetFireMndSiden.copy(kanEndres = false)

        shouldNotThrow<IllegalArgumentException> {
            validerDeltakerKanEndres(deltakerDeltar)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakerKanEndres(deltakerSluttetFireUkerSiden)
        }
        shouldThrow<IllegalArgumentException> {
            validerDeltakerKanEndres(deltakerSluttetFireMndSiden)
        }
        shouldThrow<IllegalArgumentException> {
            validerDeltakerKanEndres(deltakerIkkeAktuellFireMndSiden)
        }
        // Låst + nylig avsluttet skal IKKE lenger kaste feil
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakerKanEndres(deltakerLaastSluttetFireUkerSiden)
        }
        // Låst + gammel avsluttet skal fortsatt kaste feil
        shouldThrow<IllegalArgumentException> {
            validerDeltakerKanEndres(deltakerLaastSluttetFireMndSiden)
        }
    }

    @Test
    fun testValiderSluttdatoForDeltaker() {
        val deltaker = TestData.lagDeltakerOld(
            deltakerliste = TestData.lagDeltakerliste(
                startDato = LocalDate.now().minusYears(2),
                sluttDato = LocalDate.now().plusYears(1),
            ),
        )

        shouldNotThrow<IllegalArgumentException> {
            validerSluttdatoForDeltaker(
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now(),
                opprinneligDeltaker = deltaker,
            )
        }
        shouldNotThrow<IllegalArgumentException> {
            validerSluttdatoForDeltaker(startdato = null, sluttdato = LocalDate.now(), opprinneligDeltaker = deltaker)
        }
        shouldThrow<IllegalArgumentException> {
            validerSluttdatoForDeltaker(
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().minusDays(12),
                opprinneligDeltaker = deltaker,
            )
        }
        shouldThrow<IllegalArgumentException> {
            validerSluttdatoForDeltaker(
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().plusYears(2),
                opprinneligDeltaker = deltaker,
            )
        }
    }

    @Test
    fun `validerSluttdato - skal feile hvis sluttdato er utenfor max varighet`() {
        val deltaker = TestData.lagDeltakerOld(
            deltakerliste = TestData.lagDeltakerliste(
                tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK),
            ),
        )

        shouldNotThrow<IllegalArgumentException> {
            validerSluttdatoForDeltaker(
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusWeeks(13),
                opprinneligDeltaker = deltaker,
            )
        }

        shouldThrow<IllegalArgumentException> {
            validerSluttdatoForDeltaker(
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusWeeks(13).plusDays(1),
                opprinneligDeltaker = deltaker,
            )
        }
    }

    @Test
    fun `EndreDeltakelsesmengdeRequest valider - gyldigFra er lik startdato - kaster ikke exception`() {
        val startdato = LocalDate.now().plusDays(5)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = startdato,
            sluttdato = LocalDate.now().plusMonths(3),
        )
        val request = EndreDeltakelsesmengdeRequest(
            deltakelsesprosent = 50,
            dagerPerUke = null,
            begrunnelse = "begrunnelse",
            gyldigFra = startdato,
            forslagId = null,
        )

        shouldNotThrow<IllegalArgumentException> { request.valider(deltaker) }
    }

    @Test
    fun `EndreDeltakelsesmengdeRequest valider - gyldigFra er foer startdato, status VENTER_PA_OPPSTART - kaster exception`() {
        val startdato = LocalDate.now().plusDays(10)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = startdato,
            sluttdato = LocalDate.now().plusMonths(3),
        )
        val request = EndreDeltakelsesmengdeRequest(
            deltakelsesprosent = 50,
            dagerPerUke = null,
            begrunnelse = "begrunnelse",
            gyldigFra = startdato.minusDays(1),
            forslagId = null,
        )

        shouldThrow<IllegalArgumentException> { request.valider(deltaker) }
    }

    @Test
    fun `EndreDeltakelsesmengdeRequest valider - startdato null - validerer ikke gyldigFra mot startdato`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
        )
        val request = EndreDeltakelsesmengdeRequest(
            deltakelsesprosent = 50,
            dagerPerUke = null,
            begrunnelse = "begrunnelse",
            gyldigFra = LocalDate.now().minusMonths(2),
            forslagId = null,
        )

        shouldNotThrow<IllegalArgumentException> { request.valider(deltaker) }
    }

    @Test
    fun `EndreDeltakelsesmengdeRequest valider - gyldigFra etter sluttdato - kaster exception`() {
        val sluttdato = LocalDate.now().plusMonths(1)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusMonths(1),
            sluttdato = sluttdato,
        )
        val request = EndreDeltakelsesmengdeRequest(
            deltakelsesprosent = 50,
            dagerPerUke = null,
            begrunnelse = "begrunnelse",
            gyldigFra = sluttdato.plusDays(1),
            forslagId = null,
        )

        shouldThrow<IllegalArgumentException> { request.valider(deltaker) }
    }

    @Nested
    inner class ValiderAktivGjennomforingTest {
        @Test
        fun `validerAktivGjennomforing - status GJENNOMFORES - kaster ikke exception`() {
            val gjennomforing = ModelMapper.toGjennomforing(lagGjennomforingResponse(status = GjennomforingStatusType.GJENNOMFORES))
            shouldNotThrow<IllegalArgumentException> {
                validerAktivGjennomforing(gjennomforing)
            }
        }

        @Test
        fun `validerAktivGjennomforing - status AVSLUTTET - kaster exception`() {
            val gjennomforing = ModelMapper.toGjennomforing(lagGjennomforingResponse(status = GjennomforingStatusType.AVSLUTTET))
            shouldThrow<IllegalArgumentException> {
                validerAktivGjennomforing(gjennomforing)
            }
        }
    }

    @Nested
    inner class ValiderNyDeltakelsesmengdeTest {
        @Test
        fun `validerNyDeltakelsesmengde - ingen eksisterende mengde - returnerer true`() {
            val ny = Deltakelsesmengde(
                deltakelsesprosent = 50F,
                dagerPerUke = 3F,
                gyldigFra = LocalDate.now(),
                opprettet = LocalDateTime.now(),
            )
            validerNyDeltakelsesmengde(null, ny) shouldBe true
        }

        @Test
        fun `validerNyDeltakelsesmengde - endret prosent - returnerer true`() {
            val eksisterende = DeltakelsesmengderResponse(
                sisteDeltakelsesmengde = DeltakelsesmengdeResponse(
                    deltakelsesprosent = 100F,
                    dagerPerUke = null,
                    gyldigFra = LocalDate.now().minusDays(10),
                ),
                nesteDeltakelsesmengde = null,
            )
            val ny = Deltakelsesmengde(
                deltakelsesprosent = 50F,
                dagerPerUke = null,
                gyldigFra = LocalDate.now(),
                opprettet = LocalDateTime.now(),
            )
            validerNyDeltakelsesmengde(eksisterende, ny) shouldBe true
        }

        @Test
        fun `validerNyDeltakelsesmengde - endret dager per uke - returnerer true`() {
            val eksisterende = DeltakelsesmengderResponse(
                sisteDeltakelsesmengde = DeltakelsesmengdeResponse(
                    deltakelsesprosent = 50F,
                    dagerPerUke = 3F,
                    gyldigFra = LocalDate.now().minusDays(10),
                ),
                nesteDeltakelsesmengde = null,
            )
            val ny = Deltakelsesmengde(
                deltakelsesprosent = 50F,
                dagerPerUke = 4F,
                gyldigFra = LocalDate.now(),
                opprettet = LocalDateTime.now(),
            )
            validerNyDeltakelsesmengde(eksisterende, ny) shouldBe true
        }

        @Test
        fun `validerNyDeltakelsesmengde - samme verdier men tidligere gyldigFra - returnerer true`() {
            val eksisterende = DeltakelsesmengderResponse(
                sisteDeltakelsesmengde = DeltakelsesmengdeResponse(
                    deltakelsesprosent = 50F,
                    dagerPerUke = 3F,
                    gyldigFra = LocalDate.now(),
                ),
                nesteDeltakelsesmengde = null,
            )
            val ny = Deltakelsesmengde(
                deltakelsesprosent = 50F,
                dagerPerUke = 3F,
                gyldigFra = LocalDate.now().minusDays(1),
                opprettet = LocalDateTime.now(),
            )
            validerNyDeltakelsesmengde(eksisterende, ny) shouldBe true
        }

        @Test
        fun `validerNyDeltakelsesmengde - samme verdier og senere eller lik gyldigFra - returnerer false`() {
            val eksisterende = DeltakelsesmengderResponse(
                sisteDeltakelsesmengde = DeltakelsesmengdeResponse(
                    deltakelsesprosent = 50F,
                    dagerPerUke = 3F,
                    gyldigFra = LocalDate.now().minusDays(5),
                ),
                nesteDeltakelsesmengde = null,
            )
            val ny = Deltakelsesmengde(
                deltakelsesprosent = 50F,
                dagerPerUke = 3F,
                gyldigFra = LocalDate.now(),
                opprettet = LocalDateTime.now(),
            )
            validerNyDeltakelsesmengde(eksisterende, ny) shouldBe false
        }

        @Test
        fun `validerNyDeltakelsesmengde - dagerPerUke begge null, samme prosent og senere gyldigFra - returnerer false`() {
            val eksisterende = DeltakelsesmengderResponse(
                sisteDeltakelsesmengde = DeltakelsesmengdeResponse(
                    deltakelsesprosent = 100F,
                    dagerPerUke = null,
                    gyldigFra = LocalDate.now().minusDays(5),
                ),
                nesteDeltakelsesmengde = null,
            )
            val ny = Deltakelsesmengde(
                deltakelsesprosent = 100F,
                dagerPerUke = null,
                gyldigFra = LocalDate.now(),
                opprettet = LocalDateTime.now(),
            )
            validerNyDeltakelsesmengde(eksisterende, ny) shouldBe false
        }

        @Test
        fun `validerNyDeltakelsesmengde - eksisterende har dagerPerUke, ny har null - returnerer true`() {
            val eksisterende = DeltakelsesmengderResponse(
                sisteDeltakelsesmengde = DeltakelsesmengdeResponse(
                    deltakelsesprosent = 100F,
                    dagerPerUke = 5F,
                    gyldigFra = LocalDate.now().minusDays(5),
                ),
                nesteDeltakelsesmengde = null,
            )
            val ny = Deltakelsesmengde(
                deltakelsesprosent = 100F,
                dagerPerUke = null,
                gyldigFra = LocalDate.now(),
                opprettet = LocalDateTime.now(),
            )
            validerNyDeltakelsesmengde(eksisterende, ny) shouldBe true
        }
    }

    @Nested
    inner class ValiderDeltakerKanReaktiveresTest {
        @Test
        fun `validerDeltakerKanReaktiveres - status IKKE_AKTUELL nylig - kaster ikke exception`() {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.IKKE_AKTUELL,
                    gyldigFra = LocalDateTime.now().minusWeeks(2),
                ),
                sluttdato = null,
            )
            shouldNotThrow<IllegalArgumentException> {
                validerDeltakerKanReaktiveres(deltaker)
            }
        }

        @Test
        fun `validerDeltakerKanReaktiveres - status DELTAR - kaster exception`() {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusMonths(3),
            )
            shouldThrow<IllegalArgumentException> {
                validerDeltakerKanReaktiveres(deltaker)
            }
        }

        @Test
        fun `validerDeltakerKanReaktiveres DeltakerModel - status IKKE_AKTUELL - kaster ikke exception`() {
            val deltaker = lagDeltakerModel(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
            )
            shouldNotThrow<IllegalArgumentException> {
                validerDeltakerKanReaktiveres(deltaker)
            }
        }

        @Test
        fun `validerDeltakerKanReaktiveres DeltakerModel - status DELTAR - kaster exception`() {
            val deltaker = lagDeltakerModel(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            shouldThrow<IllegalArgumentException> {
                validerDeltakerKanReaktiveres(deltaker)
            }
        }
    }

    @Nested
    inner class StatusForMindreEnn15DagerSidenTest {
        @Test
        fun `statusForMindreEnn15DagerSiden - status gyldig fra 10 dager siden - returnerer true`() {
            val status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.DELTAR,
                gyldigFra = LocalDateTime.now().minusDays(10),
            )
            statusForMindreEnn15DagerSiden(status) shouldBe true
        }

        @Test
        fun `statusForMindreEnn15DagerSiden - status gyldig fra 20 dager siden - returnerer false`() {
            val status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.DELTAR,
                gyldigFra = LocalDateTime.now().minusDays(20),
            )
            statusForMindreEnn15DagerSiden(status) shouldBe false
        }

        @Test
        fun `statusForMindreEnn15DagerSiden - status gyldig fra akkurat 15 dager siden - returnerer false`() {
            val status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.DELTAR,
                gyldigFra = LocalDateTime.now().minusDays(15),
            )
            statusForMindreEnn15DagerSiden(status) shouldBe false
        }

        @Test
        fun `statusForMindreEnn15DagerSiden - status gyldig fra i dag - returnerer true`() {
            val status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.DELTAR,
                gyldigFra = LocalDateTime.now(),
            )
            statusForMindreEnn15DagerSiden(status) shouldBe true
        }
    }

    @Nested
    inner class ValiderForslagEllerBegrunnelseTest {
        @Test
        fun `validerForslagEllerBegrunnelse - forslagId satt - kaster ikke exception`() {
            shouldNotThrow<IllegalArgumentException> {
                validerForslagEllerBegrunnelse(UUID.randomUUID(), null)
            }
        }

        @Test
        fun `validerForslagEllerBegrunnelse - begrunnelse satt - kaster ikke exception`() {
            shouldNotThrow<IllegalArgumentException> {
                validerForslagEllerBegrunnelse(null, "En begrunnelse")
            }
        }

        @Test
        fun `validerForslagEllerBegrunnelse - begge satt - kaster ikke exception`() {
            shouldNotThrow<IllegalArgumentException> {
                validerForslagEllerBegrunnelse(UUID.randomUUID(), "En begrunnelse")
            }
        }

        @Test
        fun `validerForslagEllerBegrunnelse - ingen forslagId eller begrunnelse - kaster exception`() {
            shouldThrow<IllegalArgumentException> {
                validerForslagEllerBegrunnelse(null, null)
            }
        }

        @Test
        fun `validerForslagEllerBegrunnelse - tom begrunnelse uten forslagId - kaster exception`() {
            shouldThrow<IllegalArgumentException> {
                validerForslagEllerBegrunnelse(null, "")
            }
        }
    }

    @Nested
    inner class ValiderBegrunnelseTest {
        @Test
        fun `validerBegrunnelse - null - kaster ikke exception`() {
            shouldNotThrow<IllegalArgumentException> {
                validerBegrunnelse(null)
            }
        }

        @Test
        fun `validerBegrunnelse - innenfor maks lengde - kaster ikke exception`() {
            shouldNotThrow<IllegalArgumentException> {
                validerBegrunnelse("En kort begrunnelse")
            }
        }

        @Test
        fun `validerBegrunnelse - akkurat maks lengde - kaster ikke exception`() {
            shouldNotThrow<IllegalArgumentException> {
                validerBegrunnelse(input(MAX_BEGRUNNELSE_LENGDE))
            }
        }

        @Test
        fun `validerBegrunnelse - over maks lengde - kaster exception`() {
            shouldThrow<IllegalArgumentException> {
                validerBegrunnelse(input(MAX_BEGRUNNELSE_LENGDE + 1))
            }
        }
    }

    @Nested
    inner class HarEndretSluttaarsakTest {
        @Test
        fun `harEndretSluttaarsak - ulike aarsaker - returnerer true`() {
            val opprinnelig = DeltakerStatus.Aarsak(DeltakerStatus.Aarsak.Type.FATT_JOBB, null)
            val ny = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.SYK, null)
            harEndretSluttaarsak(opprinnelig, ny) shouldBe true
        }

        @Test
        fun `harEndretSluttaarsak - like aarsaker - returnerer false`() {
            val opprinnelig = DeltakerStatus.Aarsak(DeltakerStatus.Aarsak.Type.FATT_JOBB, null)
            val ny = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null)
            harEndretSluttaarsak(opprinnelig, ny) shouldBe false
        }

        @Test
        fun `harEndretSluttaarsak - ny aarsak er null, opprinnelig finnes - returnerer true`() {
            val opprinnelig = DeltakerStatus.Aarsak(DeltakerStatus.Aarsak.Type.FATT_JOBB, null)
            harEndretSluttaarsak(opprinnelig, null) shouldBe true
        }

        @Test
        fun `harEndretSluttaarsak - begge null - returnerer false`() {
            harEndretSluttaarsak(null, null) shouldBe false
        }

        @Test
        fun `harEndretSluttaarsak - opprinnelig null, ny finnes - returnerer true`() {
            val ny = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null)
            harEndretSluttaarsak(null, ny) shouldBe true
        }

        @Test
        fun `harEndretSluttaarsak - like aarsaker med ulik beskrivelse - returnerer true`() {
            val opprinnelig = DeltakerStatus.Aarsak(DeltakerStatus.Aarsak.Type.ANNET, "beskrivelse1")
            val ny = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET, "beskrivelse2")
            harEndretSluttaarsak(opprinnelig, ny) shouldBe true
        }

        @Test
        fun `harEndretSluttaarsak - like aarsaker med lik beskrivelse - returnerer false`() {
            val opprinnelig = DeltakerStatus.Aarsak(DeltakerStatus.Aarsak.Type.ANNET, "beskrivelse")
            val ny = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET, "beskrivelse")
            harEndretSluttaarsak(opprinnelig, ny) shouldBe false
        }
    }

    @Nested
    inner class ValiderDeltakerKanEndresDeltakerModelTest {
        @Test
        fun `validerDeltakerKanEndres DeltakerModel - feilregistrert - kaster exception`() {
            val deltaker = lagDeltakerModel(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.FEILREGISTRERT),
            )
            val request = EndreBakgrunnsinformasjonRequest(bakgrunnsinformasjon = "ny info")
            shouldThrow<IllegalArgumentException> {
                validerDeltakerKanEndres(request, deltaker)
            }
        }

        @Test
        fun `validerDeltakerKanEndres DeltakerModel - aktiv deltaker - kaster ikke exception`() {
            val deltaker = lagDeltakerModel(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            val request = EndreBakgrunnsinformasjonRequest(bakgrunnsinformasjon = "ny info")
            shouldNotThrow<IllegalArgumentException> {
                validerDeltakerKanEndres(request, deltaker)
            }
        }

        @Test
        fun `validerDeltakerKanEndres DeltakerModel - har sluttet for mer enn to mnd siden - kaster exception`() {
            val deltaker = ModelMapper.toDeltaker(
                TestData.lagDeltakerResponse(
                    status = TestData.lagDeltakerStatus(
                        statusType = DeltakerStatus.Type.HAR_SLUTTET,
                        gyldigFra = LocalDateTime.now().minusMonths(4),
                    ),
                    sluttdato = LocalDate.now().minusMonths(4),
                ),
            )
            val request = EndreBakgrunnsinformasjonRequest(bakgrunnsinformasjon = "ny info")
            shouldThrow<IllegalArgumentException> {
                validerDeltakerKanEndres(request, deltaker)
            }
        }
    }
}
