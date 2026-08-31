package no.nav.amt.deltaker.veileder.endring.extensions

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.veileder.endring.VellykketEndring
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.Innhold

fun DeltakerEndring.Endring.EndreOpplaringKategorisering.hasChanges(deltaker: Deltaker): Boolean =
    deltaker.deltakelsesinnhold?.getAnnetFritekstBeskrivelse() != this.beskrivelse

fun DeltakerEndring.Endring.EndreOpplaringKategorisering.endreOpplaringKategorisering(deltaker: Deltaker) = VellykketEndring(
    deltaker.copy(
        deltakelsesinnhold = Deltakelsesinnhold(
            ledetekst = deltaker.deltakerliste.tiltakstype.innhold
                ?.ledetekst,
            innhold = listOf(Innhold.createFritekstInnhold(this.beskrivelse)),
        ),
    ),
)
