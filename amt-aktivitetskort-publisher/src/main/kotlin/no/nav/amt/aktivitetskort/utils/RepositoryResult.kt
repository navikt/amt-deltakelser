package no.nav.amt.aktivitetskort.utils

sealed class RepositoryResult<T> {
    data class Created<T>(
        val data: T,
    ) : RepositoryResult<T>()

    data class Modified<T>(
        val data: T,
    ) : RepositoryResult<T>()

    class NoChange<T> : RepositoryResult<T>()
}
