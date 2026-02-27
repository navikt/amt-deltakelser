package no.nav.amt.lib.models.deltaker

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDateTime
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface DeltakerHistorikk {
    val sistEndret: LocalDateTime

    fun navAnsatte(): List<UUID> = emptyList()

    fun navEnheter(): List<UUID> = emptyList()

    data class VurderingFraArrangor(
        val data: VurderingFraArrangorData,
    ) : DeltakerHistorikk {
        override val sistEndret = data.opprettet
    }

    data class ImportertFraArena(
        val importertFraArena: no.nav.amt.lib.models.deltaker.ImportertFraArena,
    ) : DeltakerHistorikk {
        override val sistEndret = importertFraArena.importertDato
    }

    data class Endring(
        val endring: DeltakerEndring,
    ) : DeltakerHistorikk {
        override val sistEndret = endring.endret

        override fun navAnsatte() = listOf(endring.endretAv)

        override fun navEnheter() = listOf(endring.endretAvEnhet)
    }

    data class Vedtak(
        val vedtak: no.nav.amt.lib.models.deltaker.Vedtak,
    ) : DeltakerHistorikk {
        override val sistEndret = vedtak.sistEndret

        override fun navAnsatte() = listOf(vedtak.sistEndretAv, vedtak.opprettetAv)

        override fun navEnheter() = listOf(vedtak.sistEndretAvEnhet, vedtak.opprettetAvEnhet)
    }

    data class InnsokPaaFellesOppstart(
        val data: no.nav.amt.lib.models.deltaker.InnsokPaaFellesOppstart,
    ) : DeltakerHistorikk {
        override val sistEndret = data.innsokt

        override fun navAnsatte() = listOf(data.innsoktAv)

        override fun navEnheter() = listOf(data.innsoktAvEnhet)
    }

    data class Forslag(
        val forslag: no.nav.amt.lib.models.arrangor.melding.Forslag,
    ) : DeltakerHistorikk {
        override val sistEndret = forslag.sistEndret

        override fun navAnsatte() = listOfNotNull(forslag.getNavAnsatt()?.id)

        override fun navEnheter() = listOfNotNull(forslag.getNavAnsatt()?.enhetId)
    }

    data class EndringFraArrangor(
        val endringFraArrangor: no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor,
    ) : DeltakerHistorikk {
        override val sistEndret = endringFraArrangor.opprettet
    }

    data class EndringFraTiltakskoordinator(
        val endringFraTiltakskoordinator: no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator,
    ) : DeltakerHistorikk {
        override val sistEndret = endringFraTiltakskoordinator.endret

        override fun navAnsatte() = listOf(endringFraTiltakskoordinator.endretAv)

        override fun navEnheter() = listOf(endringFraTiltakskoordinator.endretAvEnhet)
    }
}
