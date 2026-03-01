package no.nav.amt.deltaker.kafka.utils

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerEksternV1Dto
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerV1Dto
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerV2Dto
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.hendelse.Hendelse
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID
import kotlin.reflect.KClass

suspend fun assertProduced(deltakerId: UUID) {
    val cache = mutableMapOf<UUID, DeltakerV2Dto>()

    val consumer = stringStringConsumer(Environment.DELTAKER_V2_TOPIC) { k, v ->
        cache[UUID.fromString(k)] = objectMapper.readValue(v)
    }

    consumer.start()

    eventually {
        val cachedDeltaker = cache[deltakerId].shouldNotBeNull()
        cachedDeltaker.id shouldBe deltakerId
    }

    consumer.close()
}

suspend fun assertProducedDeltakerV1(deltakerId: UUID) {
    val cache = mutableMapOf<UUID, DeltakerV1Dto>()

    val consumer = stringStringConsumer(Environment.DELTAKER_V1_TOPIC) { k, v ->
        cache[UUID.fromString(k)] = objectMapper.readValue(v)
    }

    consumer.start()

    eventually {
        val cachedDeltaker = cache[deltakerId].shouldNotBeNull()
        cachedDeltaker.id shouldBe deltakerId
    }

    consumer.close()
}

suspend fun assertProducedDeltakerEksternV1(deltakerId: UUID) {
    val cache = mutableMapOf<UUID, DeltakerEksternV1Dto>()

    val consumer = stringStringConsumer(Environment.DELTAKER_EKSTERN_V1_TOPIC) { k, v ->
        cache[UUID.fromString(k)] = objectMapper.readValue(v)
    }

    consumer.start()

    eventually {
        val cachedDeltaker = cache[deltakerId].shouldNotBeNull()
        cachedDeltaker.id shouldBe deltakerId
    }

    consumer.close()
}

suspend fun assertProducedFeilregistrert(deltakerId: UUID) {
    val cache = mutableMapOf<UUID, DeltakerV2Dto>()

    val consumer = stringStringConsumer(Environment.DELTAKER_V2_TOPIC) { k, v ->
        cache[UUID.fromString(k)] = objectMapper.readValue(v)
    }

    consumer.start()

    eventually {
        assertSoftly(cache[deltakerId].shouldNotBeNull()) {
            id shouldBe deltakerId
            status.type shouldBe DeltakerStatus.Type.FEILREGISTRERT
            dagerPerUke shouldBe null
            prosentStilling shouldBe null
            oppstartsdato shouldBe null
            sluttdato shouldBe null
            bestillingTekst shouldBe null
            innhold shouldBe null
            historikk?.filterIsInstance<DeltakerHistorikk.Endring>() shouldBe emptyList()
        }
    }

    consumer.close()
}

suspend fun <T : HendelseType> assertProducedHendelse(deltakerId: UUID, hendelsetype: KClass<T>) {
    val cache = mutableMapOf<UUID, Hendelse>()

    val consumer = stringStringConsumer(Environment.DELTAKER_HENDELSE_TOPIC) { k, v ->
        cache[UUID.fromString(k)] = objectMapper.readValue(v)
    }

    consumer.start()

    eventually {
        assertSoftly(cache[deltakerId].shouldNotBeNull()) {
            deltaker.id shouldBe deltakerId
            payload::class shouldBe hendelsetype
        }
    }

    consumer.close()
}

suspend fun assertProducedForslag(forslag: Forslag) {
    val cache = mutableMapOf<UUID, Forslag>()

    val consumer = stringStringConsumer(Environment.ARRANGOR_MELDING_TOPIC) { k, v ->
        cache[UUID.fromString(k)] = objectMapper.readValue(v)
    }

    consumer.start()

    eventually {
        val cachedForslag = cache[forslag.id]!!
        cachedForslag.id shouldBe forslag.id
        cachedForslag.deltakerId shouldBe forslag.deltakerId
        cachedForslag.endring shouldBe forslag.endring
        cachedForslag.begrunnelse shouldBe forslag.begrunnelse
        cachedForslag.opprettet shouldBeCloseTo forslag.opprettet
        cachedForslag.opprettetAvArrangorAnsattId shouldBe forslag.opprettetAvArrangorAnsattId
        sammenlignForslagStatus(cachedForslag.status, forslag.status)
    }

    consumer.close()
}

fun sammenlignForslagStatus(a: Forslag.Status, b: Forslag.Status) {
    when (a) {
        is Forslag.Status.VenterPaSvar -> {
            b as Forslag.Status.VenterPaSvar
            a shouldBe b
        }

        is Forslag.Status.Avvist -> {
            b as Forslag.Status.Avvist
            a.avvist shouldBeCloseTo b.avvist
            a.avvistAv shouldBe b.avvistAv
            a.begrunnelseFraNav shouldBe b.begrunnelseFraNav
        }

        is Forslag.Status.Godkjent -> {
            b as Forslag.Status.Godkjent
            a.godkjent shouldBeCloseTo b.godkjent
            a.godkjentAv shouldBe b.godkjentAv
        }

        is Forslag.Status.Tilbakekalt -> {
            b as Forslag.Status.Tilbakekalt
            a.tilbakekalt shouldBeCloseTo b.tilbakekalt
            a.tilbakekaltAvArrangorAnsattId shouldBe b.tilbakekaltAvArrangorAnsattId
        }

        is Forslag.Status.Erstattet -> {
            b as Forslag.Status.Erstattet
            a.erstattetMedForslagId shouldBe b.erstattetMedForslagId
            a.erstattet shouldBeCloseTo b.erstattet
        }
    }
}
