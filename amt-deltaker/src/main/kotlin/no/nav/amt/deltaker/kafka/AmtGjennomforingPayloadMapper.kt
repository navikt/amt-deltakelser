package no.nav.amt.deltaker.kafka

import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.kafka.AmtGjennomforingPayload

/**
 * Mapper en lagret [Deltakerliste] til payloaden for det interne `amt.gjennomforing-intern`-topicet.
 *
 * Bruker domenemodellen som eneste kilde, slik at både konsument-stien (ny/endret gjennomføring) og
 * reload-stien (når tiltakstypen endres) produserer identisk payload.
 *
 * Gruppe-spesifikke felt settes kun for [GjennomforingType.Gruppe]; for en Enkeltplass er de null
 * (konsumentene bruker tiltakstype.navn som visningsnavn).
 *
 * @throws IllegalArgumentException dersom arrangør mangler – en gjennomføring uten arrangør er ikke
 * komplett nok til å deles.
 */
fun Deltakerliste.toAmtGjennomforingPayload(): AmtGjennomforingPayload {
    val erGruppe = gjennomforingstype == GjennomforingType.Gruppe

    return AmtGjennomforingPayload(
        id = id,
        type = gjennomforingstype,
        tiltak = AmtGjennomforingPayload.Tiltak(
            id = tiltakstype.id,
            navn = tiltakstype.navn,
            tiltakskode = tiltakstype.tiltakskode,
            innhold = tiltakstype.innhold,
        ),
        arrangor = AmtGjennomforingPayload.Arrangor(
            organisasjonsnummer = requireNotNull(arrangor?.organisasjonsnummer) {
                "Kan ikke produsere gjennomføring $id uten arrangør"
            },
        ),
        status = status,
        oppstart = oppstart,
        pameldingstype = pameldingstype,
        navn = if (erGruppe) navn else null,
        lopenummer = lopenummer,
        startDato = startDato,
        sluttDato = sluttDato,
        tilgjengeligForArrangorFraOgMedDato = tilgjengeligForArrangorFraOgMedDato,
        apentForPamelding = if (erGruppe) apentForPamelding else null,
        antallPlasser = antallPlasser,
        oppmoteSted = oppmoteSted,
    )
}
