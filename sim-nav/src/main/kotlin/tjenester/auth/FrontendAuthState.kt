package tjenester.auth

object FrontendAuthState {
    @Volatile
    private var currentFrontendNavIdent: String? = null

    fun getNavIdent(): String? = currentFrontendNavIdent

    fun updateNavIdent(navIdent: String) {
        currentFrontendNavIdent = navIdent
    }
}

