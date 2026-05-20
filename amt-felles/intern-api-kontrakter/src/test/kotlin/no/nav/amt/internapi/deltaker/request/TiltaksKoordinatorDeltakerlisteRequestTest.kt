package no.nav.amt.internapi.deltaker.request

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.junit.jupiter.api.Test
import java.util.UUID

class TiltaksKoordinatorDeltakerlisteRequestTest {
    @Test
    fun `skal ha default sortering paa sokt inn dato synkende`() {
        val request = TiltaksKoordinatorDeltakerlisteRequest(gjennomforingId = UUID.randomUUID())

        request.pageRequest.sort shouldBe TiltaksKoordinatorDeltakerlisteRequest.SortColumn.SOKT_INN_DATO
        request.pageRequest.order shouldBe PageRequest.SortDirection.DESC
    }

    @Test
    fun `itemCountCacheKey skal vaere stabil uavhengig av statusrekkefolge`() {
        val gjennomforingId = UUID.randomUUID()

        val request = TiltaksKoordinatorDeltakerlisteRequest(
            gjennomforingId = gjennomforingId,
            statuser = setOf(DeltakerStatus.Type.HAR_SLUTTET, DeltakerStatus.Type.DELTAR),
        )
        val sameRequestWithDifferentStatusOrder = TiltaksKoordinatorDeltakerlisteRequest(
            gjennomforingId = gjennomforingId,
            statuser = setOf(DeltakerStatus.Type.DELTAR, DeltakerStatus.Type.HAR_SLUTTET),
        )

        request.itemCountCacheKey() shouldBe sameRequestWithDifferentStatusOrder.itemCountCacheKey()
    }

    @Test
    fun `itemCountCacheKey skal bruke ALL naar statusfilter er tomt`() {
        val gjennomforingId = UUID.randomUUID()

        val request = TiltaksKoordinatorDeltakerlisteRequest(gjennomforingId = gjennomforingId)

        request.itemCountCacheKey() shouldBe "$gjennomforingId:false:[ALL]"
    }

    @Test
    fun `itemCountCacheKey skal inkludere filtre som paavirker antall deltakere`() {
        val gjennomforingId = UUID.randomUUID()
        val baseRequest = TiltaksKoordinatorDeltakerlisteRequest(gjennomforingId = gjennomforingId)

        val requestWithForslagFilter = baseRequest.copy(harForslagFraArrangor = true)
        val requestWithStatusFilter = baseRequest.copy(statuser = setOf(DeltakerStatus.Type.DELTAR))
        val requestWithDifferentGjennomforing = baseRequest.copy(gjennomforingId = UUID.randomUUID())

        baseRequest.itemCountCacheKey() shouldNotBe requestWithForslagFilter.itemCountCacheKey()
        baseRequest.itemCountCacheKey() shouldNotBe requestWithStatusFilter.itemCountCacheKey()
        baseRequest.itemCountCacheKey() shouldNotBe requestWithDifferentGjennomforing.itemCountCacheKey()
    }

    @Test
    fun `itemCountCacheKey skal ignorere paging og sortering`() {
        val gjennomforingId = UUID.randomUUID()
        val baseRequest = TiltaksKoordinatorDeltakerlisteRequest(
            gjennomforingId = gjennomforingId,
            statuser = setOf(DeltakerStatus.Type.DELTAR),
        )
        val requestWithDifferentPagingAndSorting = baseRequest.copy(
            pageRequest = PageRequest(
                sort = TiltaksKoordinatorDeltakerlisteRequest.SortColumn.NAVN,
                order = PageRequest.SortDirection.DESC,
                page = 3,
                pageSize = 50,
            ),
        )

        baseRequest.itemCountCacheKey() shouldBe requestWithDifferentPagingAndSorting.itemCountCacheKey()
    }
}
