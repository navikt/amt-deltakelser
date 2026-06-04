package sharedui

import kotlinx.html.FlowContent
import kotlinx.html.HEAD
import kotlinx.html.p
import kotlinx.html.style
import kotlinx.html.unsafe

fun HEAD.simNavLauncherUiStyles() {
    style {
        unsafe {
            +"""
            .message { padding: 0.75rem; border-radius: 6px; margin-bottom: 1rem; }
            .message--ok { background: #ebfbee; border: 1px solid #b2f2bb; }
            .message--error { background: #fff5f5; border: 1px solid #ffc9c9; }
            .frontend-auth-panel { border: 1px solid #d8d8d8; border-radius: 6px; padding: 1rem; margin-bottom: 1rem; background: #fafafa; }
            .frontend-auth-panel__current { font-weight: 600; margin-bottom: 0.75rem; }
            .frontend-auth-panel__hint { margin-bottom: 0.75rem; color: #595959; }
            .frontend-auth-panel form { border: 0; padding: 0; border-radius: 0; background: transparent; }
            p { margin-top: 0; }
            """.trimIndent()
        }
    }
}

fun FlowContent.simNavLauncherMessage(
    message: String?,
    isError: Boolean,
) {
    if (message != null) {
        p(classes = "message ${if (isError) "message--error" else "message--ok"}") {
            +message
        }
    }
}

