package tjenester.auth

object FrontendAuthState {
    @Volatile
    private var currentFrontendNavIdent: String? = null

    @Volatile
    private var currentFrontendPid: String? = null

    fun getNavIdent(): String? = currentFrontendNavIdent

    fun updateNavIdent(navIdent: String) {
        currentFrontendNavIdent = navIdent
    }

    fun getPid(): String? = currentFrontendPid

    fun updatePid(pid: String) {
        currentFrontendPid = pid
    }
}

