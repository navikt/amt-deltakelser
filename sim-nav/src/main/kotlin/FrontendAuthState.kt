object FrontendAuthState {
    @Volatile
    private var currentFrontendNavIdent: String? = null

    fun requireNavIdent(): String = currentFrontendNavIdent!!
    fun getNavIdent(): String? = currentFrontendNavIdent

    fun updateNavIdent(navIdent: String) {
        currentFrontendNavIdent = navIdent
    }
}

