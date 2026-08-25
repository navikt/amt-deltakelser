package no.nav.amt.internapi.hendelse

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.utils.TestData.randomOrgnr
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class HendelseDeltakerTest {
    @Nested
    inner class ArrangorVisningsnavnTests {
        @Test
        fun `arrangorVisningsnavn - enkeltplass bruker arrangorens navn`() {
            // Arrange
            val deltakerliste = lagDeltakerliste(
                arrangor = lagArrangor(navn = "Arrangor As"),
            ).copy(erEnkeltplass = true)

            // Act
            val resultat = deltakerliste.arrangorVisningsnavn()

            // Assert
            resultat shouldBe "Arrangor AS"
        }

        @Test
        fun `arrangorVisningsnavn - gruppe bruker overordnet arrangor nar den finnes`() {
            // Arrange
            val deltakerliste = lagDeltakerliste(
                navn = "deltakerlistenavn",
                arrangor = lagArrangor(
                    navn = "Underordnet Arrangor AS",
                    overordnetArrangor = lagArrangor(
                        navn = "Overordnet Arrangor AS",
                    ),
                ),
            ).copy(erEnkeltplass = false)

            // Act
            val resultat = deltakerliste.arrangorVisningsnavn()

            // Assert
            resultat shouldBe "Overordnet Arrangor AS"
        }

        @Test
        fun `arrangorVisningsnavn - gruppe bruker deltakerlistens navn nar overordnet mangler`() {
            // Arrange
            val deltakerliste = lagDeltakerliste(
                navn = "deltakerlistenavn",
                arrangor = lagArrangor(
                    navn = "Arrangor AS",
                    overordnetArrangor = null,
                ),
            ).copy(erEnkeltplass = false)

            // Act
            val resultat = deltakerliste.arrangorVisningsnavn()

            // Assert
            resultat shouldBe "Arrangor AS"
        }

        @Test
        fun `arrangorVisningsnavn - gruppe bruker deltakerlistens navn nar overordnet er ukjent virksomhet`() {
            // Arrange
            val deltakerliste = lagDeltakerliste(
                navn = "deltakerlistenavn",
                arrangor = lagArrangor(
                    navn = "Arrangor AS",
                    overordnetArrangor = lagArrangor(navn = "Ukjent Virksomhet"),
                ),
            ).copy(erEnkeltplass = false)

            // Act
            val resultat = deltakerliste.arrangorVisningsnavn()

            // Assert
            resultat shouldBe "Arrangor AS"
        }
    }

    companion object {
        private fun lagDeltakerliste(
            id: UUID = UUID.randomUUID(),
            navn: String = "Deltakerlistenavn",
            arrangor: HendelseDeltaker.Deltakerliste.Arrangor = lagArrangor(),
            tiltak: HendelseDeltaker.Deltakerliste.Tiltak = lagTiltak(),
            startdato: LocalDate = LocalDate.now(),
            sluttdato: LocalDate? = LocalDate.now().plusDays(1),
            oppstartstype: Oppstartstype = Oppstartstype.LOPENDE,
            pameldingType: GjennomforingPameldingType = if (oppstartstype ==
                Oppstartstype.LOPENDE
            ) {
                GjennomforingPameldingType.DIREKTE_VEDTAK
            } else {
                GjennomforingPameldingType.TRENGER_GODKJENNING
            },
            opplaringKategoriseringValg: OpplaringKategoriseringValg? = null,
            prisinformasjon: PrisinformasjonDto? = null,
        ) = HendelseDeltaker.Deltakerliste(
            id = id,
            navn = navn,
            arrangor = arrangor,
            tiltak = tiltak,
            startdato = startdato,
            sluttdato = sluttdato,
            oppstartstype = oppstartstype,
            pameldingstype = pameldingType,
            opplaringKategoriseringValg = opplaringKategoriseringValg,
            prisinformasjon = prisinformasjon,
        )

        private fun lagArrangor(
            id: UUID = UUID.randomUUID(),
            organisasjonsnummer: String = randomOrgnr(),
            navn: String = "Arrangornavn",
            overordnetArrangor: HendelseDeltaker.Deltakerliste.Arrangor? = null,
        ) = HendelseDeltaker.Deltakerliste.Arrangor(id, organisasjonsnummer, navn, overordnetArrangor)

        private fun lagTiltak(
            navn: String = "Tiltaksnavn",
            tiltakskode: Tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            ledetekst: String = "Beskrivelse av hva tiltaket går ut på",
        ) = HendelseDeltaker.Deltakerliste.Tiltak(
            navn = navn,
            ledetekst = ledetekst,
            tiltakskode = tiltakskode,
        )
    }
}
