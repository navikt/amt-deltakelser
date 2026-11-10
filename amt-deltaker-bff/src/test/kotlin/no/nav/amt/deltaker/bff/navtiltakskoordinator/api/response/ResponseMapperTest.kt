@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.ResponseMapper.getDeltakelsesinnholdAnnet
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.internapi.deltaker.annetInnholdselement
import no.nav.amt.internapi.deltaker.response.DeltakerStatusResponse.Aarsak
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
import no.nav.amt.internapi.deltaker.response.VurderingResponse
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ResponseMapperTest {
    @Nested
    inner class BuildDeltakerDetaljerResponse {
        @Test
        fun `skal bruke delte typer for veileder, vurdering og statusarsak`() {
            val veileder = NavVeilederResponse("Veileder", "12345678", "veileder@nav.no")
            val vurdering = VurderingResponse(Vurderingstype.OPPFYLLER_IKKE_KRAVENE, "Begrunnelse")
            val deltaker = TestData
                .lagDeltakerModel(
                    status = TestData.lagDeltakerStatus(
                        DeltakerStatus.Type.HAR_SLUTTET,
                        DeltakerStatus.Aarsak(DeltakerStatus.Aarsak.Type.ANNET, "Beskrivelse"),
                    ),
                ).let { it.copy(navBruker = it.navBruker.copy(navVeileder = veileder), sisteVurdering = vurdering) }

            val response = ResponseMapper.buildDeltakerDetaljerResponse(deltaker, true, emptyList())
            response.navVeileder shouldBe veileder
            response.vurdering shouldBe vurdering
            response.status shouldBe
                DeltakerStatusResponse(
                    DeltakerStatus.Type.HAR_SLUTTET,
                    Aarsak(DeltakerStatus.Aarsak.Type.ANNET, "Beskrivelse"),
                )
        }

        @Test
        fun `skal gi tom veileder, ingen vurdering og ingen statusarsak nar de mangler`() {
            val deltaker = TestData
                .lagDeltakerModel()
                .let { it.copy(navBruker = it.navBruker.copy(navVeileder = null), sisteVurdering = null) }

            val response = ResponseMapper.buildDeltakerDetaljerResponse(deltaker, true, emptyList())
            response.navVeileder shouldBe NavVeilederResponse(null, null, null)
            response.vurdering shouldBe null
            response.status.aarsak shouldBe null
        }
    }

    @Nested
    inner class GetDeltakelsesinnholdAnnet {
        @Test
        fun `skal returnere null hvis harTilgangTilBruker er false`() {
            getDeltakelsesinnholdAnnet(false, GjennomforingPameldingType.TRENGER_GODKJENNING, null) shouldBe null
        }

        @Test
        fun `skal returnere null hvis pameldingstype er null`() {
            getDeltakelsesinnholdAnnet(true, null, null) shouldBe null
        }

        @Test
        fun `skal returnere null hvis pameldingstype er DIREKTE_VEDTAK`() {
            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.DIREKTE_VEDTAK, null) shouldBe null
        }

        @Test
        fun `skal returnere null hvis deltakelsesinnhold er null`() {
            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.TRENGER_GODKJENNING, null) shouldBe null
        }

        @Test
        fun `skal returnere null hvis deltakelsesinnhold er tomt`() {
            val deltakelsesinnhold = Deltakelsesinnhold(ledetekst = null, innhold = emptyList())
            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.TRENGER_GODKJENNING, deltakelsesinnhold) shouldBe null
        }

        @Test
        fun `skal returnere null hvis annetInnholdselement ikke finnes`() {
            val deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = null,
                innhold = listOf(Innhold(innholdskode = "IKKE_ANNET", beskrivelse = "beskrivelse", valgt = true, tekst = "IKKE annet")),
            )
            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.TRENGER_GODKJENNING, deltakelsesinnhold) shouldBe null
        }

        @Test
        fun `skal returnere beskrivelse hvis annetInnholdselement finnes`() {
            val deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = null,
                innhold = listOf(
                    Innhold(innholdskode = annetInnholdselement.innholdskode, beskrivelse = "beskrivelse", valgt = true, tekst = "Annet"),
                ),
            )

            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.TRENGER_GODKJENNING, deltakelsesinnhold) shouldBe "beskrivelse"
        }

        @Test
        fun `skal returnere null hvis beskrivelse er tom eller whitespace`() {
            val deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = null,
                innhold = listOf(
                    Innhold(innholdskode = annetInnholdselement.innholdskode, beskrivelse = "   ", valgt = true, tekst = "Annet"),
                ),
            )
            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.TRENGER_GODKJENNING, deltakelsesinnhold) shouldBe null
        }

        @Test
        fun `skal returnere null hvis beskrivelse er null`() {
            val deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = null,
                innhold = listOf(
                    Innhold(innholdskode = annetInnholdselement.innholdskode, beskrivelse = null, valgt = true, tekst = "Annet"),
                ),
            )
            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.TRENGER_GODKJENNING, deltakelsesinnhold) shouldBe null
        }

        @Test
        fun `skal returnere beskrivelse for riktig element selv om flere finnes`() {
            val deltakelsesinnhold = Deltakelsesinnhold(
                ledetekst = null,
                innhold = listOf(
                    Innhold(innholdskode = "IKKE_ANNET", beskrivelse = "feil", valgt = true, tekst = "Feil"),
                    Innhold(innholdskode = annetInnholdselement.innholdskode, beskrivelse = "riktig", valgt = true, tekst = "Annet"),
                ),
            )
            getDeltakelsesinnholdAnnet(true, GjennomforingPameldingType.TRENGER_GODKJENNING, deltakelsesinnhold) shouldBe "riktig"
        }
    }
}
