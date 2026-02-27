package no.nav.amt.deltaker.bff.deltaker.api.model

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.deltaker.model.months
import no.nav.amt.deltaker.bff.deltaker.model.weeks
import no.nav.amt.deltaker.bff.deltaker.model.years
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeltakerTest {
    @Test
    fun `maxVarighetDato - skal kalkulere riktig max varighet basert tiltakstype`() {
        val deltakere = Tiltakskode.entries.map {
            TestData.lagDeltaker(
                startdato = LocalDate.now(),
                deltakerliste = TestData.lagDeltakerliste(tiltakstype = TestData.lagTiltakstype(tiltakskode = it)),
            )
        }

        deltakere.forEach {
            when (it.deltakerliste.tiltak.tiltakskode) {
                Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
                Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
                Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                -> it.maxVarighet shouldBe years(3)

                Tiltakskode.AVKLARING,
                Tiltakskode.ARBEIDSRETTET_REHABILITERING,
                -> it.maxVarighet shouldBe weeks(17)

                Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK -> it.maxVarighet shouldBe weeks(13)

                Tiltakskode.HOYERE_UTDANNING,
                Tiltakskode.STUDIESPESIALISERING,
                Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
                Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING,
                Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                -> it.maxVarighet shouldBe years(4)

                Tiltakskode.FAG_OG_YRKESOPPLAERING,
                -> years(5)

                Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
                Tiltakskode.JOBBKLUBB,
                Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
                Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
                -> it.maxVarighet shouldBe null

                Tiltakskode.OPPFOLGING -> when (it.navBruker.innsatsgruppe) {
                    Innsatsgruppe.SITUASJONSBESTEMT_INNSATS -> it.maxVarighet shouldBe years(1)

                    Innsatsgruppe.SPESIELT_TILPASSET_INNSATS,
                    Innsatsgruppe.GRADERT_VARIG_TILPASSET_INNSATS,
                    Innsatsgruppe.VARIG_TILPASSET_INNSATS,
                    -> it.maxVarighet shouldBe years(6).plus(months(6))

                    else -> it.maxVarighet shouldBe null
                }
            }
        }
    }

    @Test
    fun `maxVarighetDato - skal kalkulere riktig max varighet basert pa innsatsgruppe for oppfolging`() {
        val deltakerStandardInnsats = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.OPPFOLGING)),
            navBruker = TestData.lagNavBruker(innsatsgruppe = Innsatsgruppe.STANDARD_INNSATS),
        )
        val deltakerSituasjonsbestemt = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.OPPFOLGING)),
            navBruker = TestData.lagNavBruker(innsatsgruppe = Innsatsgruppe.SITUASJONSBESTEMT_INNSATS),
        )
        val andreInnsatsgrupper = listOf(
            Innsatsgruppe.SPESIELT_TILPASSET_INNSATS,
            Innsatsgruppe.GRADERT_VARIG_TILPASSET_INNSATS,
            Innsatsgruppe.VARIG_TILPASSET_INNSATS,
        ).map {
            TestData.lagDeltaker(
                deltakerliste = TestData.lagDeltakerliste(
                    tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.OPPFOLGING),
                ),
                navBruker = TestData.lagNavBruker(innsatsgruppe = it),
            )
        }

        deltakerStandardInnsats.maxVarighet shouldBe null
        deltakerSituasjonsbestemt.maxVarighet shouldBe years(1)
        andreInnsatsgrupper.forEach { it.maxVarighet shouldBe years(3).plus(months(6)) }
    }

    @Test
    fun `softVarighetDato - skal kalkulere riktig varighet basert pa innsatsgruppe for oppfolging`() {
        val deltakerStandardInnsats = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.OPPFOLGING)),
            navBruker = TestData.lagNavBruker(innsatsgruppe = Innsatsgruppe.STANDARD_INNSATS),
        )
        val deltakerSituasjonsbestemt = TestData.lagDeltaker(
            deltakerliste = TestData.lagDeltakerliste(tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.OPPFOLGING)),
            navBruker = TestData.lagNavBruker(innsatsgruppe = Innsatsgruppe.SITUASJONSBESTEMT_INNSATS),
        )
        val andreInnsatsgrupper = listOf(
            Innsatsgruppe.SPESIELT_TILPASSET_INNSATS,
            Innsatsgruppe.GRADERT_VARIG_TILPASSET_INNSATS,
            Innsatsgruppe.VARIG_TILPASSET_INNSATS,
        ).map {
            TestData.lagDeltaker(
                deltakerliste = TestData.lagDeltakerliste(
                    tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.OPPFOLGING),
                ),
                navBruker = TestData.lagNavBruker(innsatsgruppe = it),
            )
        }

        deltakerStandardInnsats.softMaxVarighet shouldBe null
        deltakerSituasjonsbestemt.softMaxVarighet shouldBe null
        andreInnsatsgrupper.forEach { it.softMaxVarighet shouldBe years(3) }
    }

    @Test
    fun `softMaxVarighetDato - skal kalkulere riktig varighet basert tiltakstype`() {
        val deltakere = Tiltakskode.entries.map {
            TestData.lagDeltaker(
                startdato = LocalDate.now(),
                deltakerliste = TestData.lagDeltakerliste(tiltakstype = TestData.lagTiltakstype(tiltakskode = it)),
            )
        }

        deltakere.forEach {
            when (it.deltakerliste.tiltak.tiltakskode) {
                Tiltakskode.ARBEIDSFORBEREDENDE_TRENING -> it.softMaxVarighet shouldBe years(2)

                Tiltakskode.OPPFOLGING -> when (it.navBruker.innsatsgruppe) {
                    Innsatsgruppe.SPESIELT_TILPASSET_INNSATS,
                    Innsatsgruppe.GRADERT_VARIG_TILPASSET_INNSATS,
                    Innsatsgruppe.VARIG_TILPASSET_INNSATS,
                    -> it.maxVarighet shouldBe years(3)

                    else -> it.maxVarighet shouldBe null
                }

                Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK,
                -> it.softMaxVarighet shouldBe weeks(8)

                Tiltakskode.AVKLARING,
                Tiltakskode.ARBEIDSRETTET_REHABILITERING,
                -> it.softMaxVarighet shouldBe weeks(12)

                Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
                Tiltakskode.JOBBKLUBB,
                Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
                Tiltakskode.HOYERE_UTDANNING,
                Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
                Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
                Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
                Tiltakskode.STUDIESPESIALISERING,
                Tiltakskode.FAG_OG_YRKESOPPLAERING,
                Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING,
                -> it.softMaxVarighet shouldBe null
            }
        }
    }
}
