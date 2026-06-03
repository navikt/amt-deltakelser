package sharedui

import kotlinx.html.*

private data class SimNavMenuItem(
    val label: String,
    val path: String,
)

private val simNavMenuItems = listOf(
    SimNavMenuItem("Home", "/"),
    SimNavMenuItem("Valp", "/valp"),
    SimNavMenuItem("Veilarboppfolging", "/veilarboppfolging"),
    SimNavMenuItem("Veilarbvedtaksstotte", "/veilarbvedtaksstotte"),
    SimNavMenuItem("Nom", "/nom"),
    SimNavMenuItem("AO oppfolgingskontor", "/ao-oppfolgingskontor"),
    SimNavMenuItem("Dokdistkanal", "/dokdistkanal"),
    SimNavMenuItem("Nav-veileders-flate", "/nav-veileders-flate"),
)

fun HEAD.simNavHeaderStyles() {
    style {
        unsafe {
            +"""
            .sim-nav-header {
                border: 1px solid #d8d8d8;
                border-radius: 6px;
                padding: 0.75rem;
                margin-bottom: 1rem;
                background: #fafafa;
            }
            .sim-nav-header__title {
                font-size: 0.85rem;
                font-weight: 600;
                margin: 0 0 0.5rem 0;
                color: #444;
            }
            .sim-nav-nav {
                display: flex;
                flex-wrap: wrap;
                gap: 0.5rem;
            }
            .sim-nav-nav a {
                text-decoration: none;
                border: 1px solid #cfd4dc;
                border-radius: 999px;
                padding: 0.25rem 0.6rem;
                color: #1f2937;
                background: #fff;
                font-size: 0.9rem;
            }
            .sim-nav-nav a.active {
                background: #e7f1ff;
                border-color: #0d6efd;
                color: #0b5ed7;
                font-weight: 600;
            }
            """.trimIndent()
        }
    }
}

fun FlowContent.simNavHeader(activePathPrefix: String) {
    header(classes = "sim-nav-header") {
        p(classes = "sim-nav-header__title") { +"Sim-nav features" }
        nav(classes = "sim-nav-nav") {
            simNavMenuItems.forEach { item ->
                a(
                    href = item.path,
                    classes = if (item.path == activePathPrefix) "active" else null,
                ) {
                    +item.label
                }
            }
        }
    }
}


