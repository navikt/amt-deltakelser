package no.nav.amt.deltaker.bff.tiltakskoordinator.ulesthendelse.model

import java.time.LocalDateTime
import java.util.UUID

data class UlestHendelse(
    val id: UUID,
    val opprettet: LocalDateTime,
    val deltakerId: UUID,
    val ansvarlig: AnsvarligNavnOgEnhet?,
    val hendelse: UlestHendelseType,
)
