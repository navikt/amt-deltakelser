package no.nav.tiltaksarrangor.consumer.model

import java.util.UUID

data class TilknyttetArrangorDto(
	val arrangorId: UUID,
	val roller: List<AnsattRolle>,
	val veileder: List<VeilederDto>,
	val koordinator: List<UUID>,
)
