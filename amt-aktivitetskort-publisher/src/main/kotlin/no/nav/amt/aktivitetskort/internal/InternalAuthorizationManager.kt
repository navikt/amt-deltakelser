package no.nav.amt.aktivitetskort.internal

import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.core.Authentication
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.stereotype.Component
import java.util.function.Supplier

@Component
class InternalAuthorizationManager : AuthorizationManager<RequestAuthorizationContext> {
    override fun authorize(
        authentication: Supplier<out Authentication>,
        context: RequestAuthorizationContext,
    ) = AuthorizationDecision(context.request.remoteAddr == "127.0.0.1")
}
