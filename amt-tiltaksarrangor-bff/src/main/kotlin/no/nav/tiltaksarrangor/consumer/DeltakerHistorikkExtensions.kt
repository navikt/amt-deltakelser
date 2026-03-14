package no.nav.tiltaksarrangor.consumer

import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator

fun List<DeltakerHistorikk.EndringFraTiltakskoordinator>.getNyDeltakerEndringFraTiltakskoordinator() = this
	.filter { endring ->
		endring.endringFraTiltakskoordinator.endring is EndringFraTiltakskoordinator.TildelPlass ||
			endring.endringFraTiltakskoordinator.endring is EndringFraTiltakskoordinator.DelMedArrangor
	}.maxByOrNull { it.endringFraTiltakskoordinator.endret }
