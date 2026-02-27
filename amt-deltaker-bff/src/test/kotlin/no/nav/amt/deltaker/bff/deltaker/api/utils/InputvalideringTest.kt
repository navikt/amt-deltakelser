package no.nav.amt.deltaker.bff.deltaker.api.utils

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import no.nav.amt.deltaker.bff.deltaker.api.model.InnholdRequest
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.annetInnholdselement
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.data.TestData.input
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

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
        val tiltaksinnhold = TestData.lagDeltakerRegistreringInnhold(
            innholdselementer = listOf(
                Innholdselement("Type", "type"),
            ),
        )
        val tiltakstype = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING

        shouldThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(listOf(InnholdRequest("type", null)), null, tiltakstype)
        }
        shouldThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(
                listOf(InnholdRequest("type", null)),
                TestData.lagDeltakerRegistreringInnhold(innholdselementer = emptyList()),
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
            validerDeltakelsesinnhold(listOf(InnholdRequest("foo", null)), tiltaksinnhold, tiltakstype)
        }
        shouldThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(listOf(InnholdRequest(annetInnholdselement.innholdskode, null)), tiltaksinnhold, tiltakstype)
        }
        shouldThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(listOf(InnholdRequest(annetInnholdselement.innholdskode, "")), tiltaksinnhold, tiltakstype)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(
                listOf(InnholdRequest(annetInnholdselement.innholdskode, "annet innhold må ha beskrivelse")),
                tiltaksinnhold,
                tiltakstype,
            )
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(listOf(InnholdRequest("type", null)), tiltaksinnhold, tiltakstype)
        }
        shouldThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(
                listOf(InnholdRequest("type", "andre typer enn annet skal ikke ha beskrivelse")),
                tiltaksinnhold,
                tiltakstype,
            )
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(
                listOf(InnholdRequest(annetInnholdselement.innholdskode, "annet er tillatt for VTA")),
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
    fun testValiderKladdInnhold() {
        val tiltaksinnhold = TestData.lagDeltakerRegistreringInnhold(
            innholdselementer = listOf(
                Innholdselement("Type", "type"),
                annetInnholdselement,
            ),
        )
        val tiltakstype = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING

        shouldThrow<IllegalArgumentException> {
            validerKladdInnhold(listOf(InnholdRequest("type", null)), null, tiltakstype)
        }
        shouldThrow<IllegalArgumentException> {
            validerKladdInnhold(
                listOf(InnholdRequest("type", null)),
                TestData.lagDeltakerRegistreringInnhold(innholdselementer = emptyList()),
                tiltakstype,
            )
        }
        shouldNotThrow<IllegalArgumentException> {
            validerKladdInnhold(emptyList(), tiltaksinnhold, tiltakstype)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerKladdInnhold(emptyList(), null, tiltakstype)
        }
        shouldThrow<IllegalArgumentException> {
            validerKladdInnhold(listOf(InnholdRequest("foo", null)), tiltaksinnhold, tiltakstype)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerKladdInnhold(listOf(InnholdRequest(annetInnholdselement.innholdskode, null)), tiltaksinnhold, tiltakstype)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerKladdInnhold(listOf(InnholdRequest(annetInnholdselement.innholdskode, "")), tiltaksinnhold, tiltakstype)
        }
        shouldNotThrow<IllegalArgumentException> {
            validerKladdInnhold(
                listOf(InnholdRequest(annetInnholdselement.innholdskode, "annet innhold må ha beskrivelse")),
                tiltaksinnhold,
                tiltakstype,
            )
        }
        shouldNotThrow<IllegalArgumentException> {
            validerKladdInnhold(listOf(InnholdRequest("type", null)), tiltaksinnhold, tiltakstype)
        }
        shouldThrow<IllegalArgumentException> {
            validerKladdInnhold(
                listOf(InnholdRequest("type", "andre typer enn annet skal ikke ha beskrivelse")),
                tiltaksinnhold,
                tiltakstype,
            )
        }
        shouldNotThrow<IllegalArgumentException> {
            validerDeltakelsesinnhold(
                listOf(InnholdRequest(annetInnholdselement.innholdskode, "annet er tillatt for VTA")),
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
        val deltakerDeltar = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            sluttdato = LocalDate.now().plusMonths(6),
        )
        val deltakerSluttetFireUkerSiden = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            sluttdato = LocalDate.now().minusWeeks(4),
        )
        val deltakerSluttetFireMndSiden = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusMonths(4),
            ),
            sluttdato = LocalDate.now().minusMonths(4),
        )
        val deltakerIkkeAktuellFireMndSiden = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.IKKE_AKTUELL,
                gyldigFra = LocalDateTime.now().minusMonths(4),
            ),
            sluttdato = null,
        )

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
        shouldThrow<IllegalArgumentException> {
            validerDeltakerKanEndres(deltakerSluttetFireUkerSiden.copy(kanEndres = false))
        }
    }

    @Test
    fun testValiderSluttdatoForDeltaker() {
        val deltaker = TestData.lagDeltaker(
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
        val deltaker = TestData.lagDeltaker(
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
}
