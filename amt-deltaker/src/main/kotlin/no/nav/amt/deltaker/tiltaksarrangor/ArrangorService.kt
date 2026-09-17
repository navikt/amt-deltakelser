package no.nav.amt.deltaker.tiltaksarrangor

import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.lib.ktor.clients.arrangor.AmtArrangorClient
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.utils.toTitleCase

class ArrangorService(
    private val arrangorRepository: ArrangorRepository,
    private val amtArrangorClient: AmtArrangorClient,
) {
    companion object {
        const val UKJENT_VIRKSOMHET = "Ukjent virksomhet"
    }

    suspend fun hentArrangor(orgnr: String): Arrangor = arrangorRepository.get(orgnr) ?: opprettArrangor(orgnr)

    private suspend fun opprettArrangor(orgnr: String): Arrangor {
        val arrangor = amtArrangorClient.hentArrangor(orgnr)

        arrangor.overordnetArrangor?.let { arrangorRepository.upsert(it) }
        arrangorRepository.upsert(arrangor.toModel())

        return arrangor.toModel()
    }

    /*
       Henter arrangør med korrekt navn i forhold til tiltakstypen.
     * Gruppetiltak skal bruke overordnet arrangørs navn der det er tilgjengelig
     * Enkeltplasser skal bruke enheten som er koblet til gjennomføringen("underordnet arrangør")
     * Enkeltplasser kan i visse tilfeller mangle arrangør(i kladd status), da brukes "Ukjent arrangør" som fallback
     */
    fun getFunksjonellArrangorForGjennomforing(gjennomforing: Deltakerliste): Arrangor? {
        val arrangor = if (gjennomforing.gjennomforingstype === GjennomforingType.Gruppe) {
            gjennomforing.arrangor
                ?.overordnetArrangorId
                ?.let { arrangorRepository.get(it) }
                ?.takeUnless { it.navn.equals(UKJENT_VIRKSOMHET, true) }
                ?: gjennomforing.arrangor
        } else {
            gjennomforing.arrangor
        }

        return arrangor?.copy(navn = arrangor.navn.toTitleCase())
    }
}
