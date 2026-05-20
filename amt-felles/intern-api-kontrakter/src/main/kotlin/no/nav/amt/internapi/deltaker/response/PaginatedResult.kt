package no.nav.amt.internapi.deltaker.response

/**
 * Representerer et paginert resultatsett fra et listeendepunkt.
 *
 * @property totalCount Totalt antall tilgjengelige elementer uavhengig av paging.
 * @property pageSize Antall elementer per side.
 * @property data Elementene i gjeldende side.
 */
data class PaginatedResult<T : Any>(
    val totalCount: Int = 0,
    val pageSize: Int = 0,
    val data: List<T>,
) {
    init {
        require(totalCount >= 0) { "Total count must be greater than or equal to 0" }
        require(pageSize >= 0) { "Page size must be greater than or equal to 0" }
        require(totalCount == 0 || pageSize > 0) { "Page size must be greater than 0 when total count is greater than 0" }
        require(totalCount > 0 || data.isEmpty()) { "Total count must be greater than 0 when data is not empty" }
    }

    /**
     * Totalt antall tilgjengelige sider basert på totalCount og pageSize.
     */
    val totalPages = if (totalCount == 0) 0 else (totalCount + pageSize - 1) / pageSize
}
