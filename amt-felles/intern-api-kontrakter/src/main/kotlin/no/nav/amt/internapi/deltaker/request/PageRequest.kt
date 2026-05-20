package no.nav.amt.internapi.deltaker.request

import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Representerer paging- og sorteringsparametere for listeendepunkter og databaseoppslag.
 *
 * @param T Typen som representerer gyldige sorteringsfelt.
 *
 * @property sort Feltet det skal sorteres på.
 * @property order Sorteringsretning.
 * @property page 1-indeksert sidenummer.
 * @property pageSize Antall elementer per side.
 */
data class PageRequest<T>(
    val sort: T? = null,
    val order: SortDirection = SortDirection.ASC,
    val page: Int = 1,
    val pageSize: Int = 200,
) {
    init {
        require(page > 0) { "Page must be greater than 0" }
        require(pageSize > 0) { "Page size must be greater than 0" }
    }

    /**
     * Antall elementer som skal hoppes over i underliggende spørring.
     */
    @get:JsonIgnore
    val offset: Int get() = (page - 1) * pageSize

    enum class SortDirection {
        ASC,
        DESC,
    }
}
