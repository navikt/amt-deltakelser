package no.nav.amt.deltaker.bff.clients

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import no.nav.poao_tilgang.api.dto.response.TilgangsattributterResponse
import no.nav.poao_tilgang.client.AdGruppe
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.NorskIdent
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.PoaoTilgangClient
import no.nav.poao_tilgang.client.PolicyInput
import no.nav.poao_tilgang.client.PolicyRequest
import no.nav.poao_tilgang.client.PolicyResult
import no.nav.poao_tilgang.client.api.ApiResult
import no.nav.poao_tilgang.client.utils.CacheUtils.tryCacheFirstNotNull
import java.time.Duration
import java.util.UUID
import kotlin.collections.forEach

// TODO: Lag pull request mot poao for å få med konfigurerbar CacheDuration
class ConfigurablePoaoTilgangCachedClient(

    private val client: PoaoTilgangClient,
    private val cacheDuration: Duration,
    private val policyInputToDecisionCache: Cache<PolicyInput, Decision> = Caffeine.newBuilder()
        .expireAfterWrite(cacheDuration)
        .build(),
    private val navAnsattIdToAzureAdGrupperCache: Cache<UUID, List<AdGruppe>> = Caffeine.newBuilder()
        .expireAfterWrite(cacheDuration)
        .build(),
    private val norskIdentToErSkjermetCache: Cache<NorskIdent, Boolean> = Caffeine.newBuilder()
        .expireAfterWrite(cacheDuration)
        .build(),
    private val tilgangsAttributterCache: Cache<NorskIdent, TilgangsattributterResponse> = Caffeine.newBuilder()
        .expireAfterWrite(cacheDuration)
        .build()
    ) : PoaoTilgangClient {

        companion object {
            @JvmStatic
            fun createDefaultCacheClient(client: PoaoTilgangClient, cacheDuration: Duration = Duration.ofMinutes(30)): PoaoTilgangCachedClient {
                return PoaoTilgangCachedClient(client)
            }
        }


        override fun evaluatePolicy(input: PolicyInput): ApiResult<Decision> {
            val decision = tryCacheFirstNotNull(policyInputToDecisionCache, input) {
                val resultat = client.evaluatePolicy(input)
                if (resultat.isFailure) return resultat

                return@tryCacheFirstNotNull resultat.get()!!
            }
            return ApiResult.success(decision)
        }

        override fun evaluatePolicies(requests: List<PolicyRequest>): ApiResult<List<PolicyResult>> {
            val uncachedRequests = mutableListOf<PolicyRequest>()
            val cachedResults = requests.map {
                val maybeResult = policyInputToDecisionCache.getIfPresent(it.policyInput)
                if (maybeResult == null) uncachedRequests.add(it)
                return@map if (maybeResult != null) PolicyResult(it.requestId, maybeResult) else null
            }.filterNotNull()

            val apiResult = client.evaluatePolicies(uncachedRequests)
            if (apiResult.isFailure) {
                return apiResult
            }
            val policyResults = apiResult.get()!!
            policyResults.forEach {
                val request = requests.find { r -> it.requestId == r.requestId }
                    ?: throw IllegalStateException("Fant ikke request med requestId=${it.requestId}")
                policyInputToDecisionCache.put(request.policyInput, it.decision)
            }

            return ApiResult.success(cachedResults.plus(policyResults))

        }

        override fun hentAdGrupper(navAnsattAzureId: UUID): ApiResult<List<AdGruppe>> {
            val adGrupper = tryCacheFirstNotNull(navAnsattIdToAzureAdGrupperCache, navAnsattAzureId) {
                val resultat = client.hentAdGrupper(navAnsattAzureId)
                if (resultat.isFailure) return resultat

                return@tryCacheFirstNotNull resultat.get()!!
            }
            return ApiResult.success(adGrupper)
        }

        override fun erSkjermetPerson(norskIdent: NorskIdent): ApiResult<Boolean> {
            val erSkjermet = tryCacheFirstNotNull(norskIdentToErSkjermetCache, norskIdent) {
                val resultat = client.erSkjermetPerson(norskIdent)
                if (resultat.isFailure) return resultat

                return@tryCacheFirstNotNull resultat.get()!!
            }
            return ApiResult.success(erSkjermet)
        }

        override fun erSkjermetPerson(norskeIdenter: List<NorskIdent>): ApiResult<Map<NorskIdent, Boolean>> {
            val uncachedIdenter = mutableListOf<NorskIdent>()
            val cachedResults = mutableMapOf<NorskIdent, Boolean>()

            norskeIdenter.forEach {
                val maybeResult = norskIdentToErSkjermetCache.getIfPresent(it)
                if (maybeResult == null) uncachedIdenter.add(it)
                else cachedResults[it] = maybeResult
            }

            // Hvis alle identer er cachet så trenger vi ikke hente med klienten
            if (uncachedIdenter.isEmpty()) {
                return ApiResult.success(cachedResults)
            }

            val apiResult = client.erSkjermetPerson(uncachedIdenter)

            if (apiResult.isFailure) {
                return apiResult
            }

            val erSkjermetPersonMap = apiResult.get()!!

            erSkjermetPersonMap.forEach {
                norskIdentToErSkjermetCache.put(it.key, it.value)
            }

            return ApiResult.success(cachedResults.plus(erSkjermetPersonMap))
        }

        override fun hentTilgangsAttributter(norskIdent: NorskIdent): ApiResult<TilgangsattributterResponse> {
            val tilgangsAttributter = tryCacheFirstNotNull(tilgangsAttributterCache, norskIdent) {
                val resultat = client.hentTilgangsAttributter(norskIdent)
                if (resultat.isFailure) return resultat
                return@tryCacheFirstNotNull resultat.get()!!
            }
            return ApiResult.success(tilgangsAttributter)
        }

    }