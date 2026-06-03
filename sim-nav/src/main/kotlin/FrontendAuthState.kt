object FrontendAuthState {
    private const val DEFAULT_NAV_IDENT = "Z123456"

    @Volatile
    private var currentFrontendNavIdent: String = DEFAULT_NAV_IDENT

    fun currentNavIdent(): String = currentFrontendNavIdent

    fun updateNavIdent(navIdent: String) {
        currentFrontendNavIdent = navIdent
    }
}

