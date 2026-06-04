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
    SimNavMenuItem("Innbyggers-flate", "/innbyggers-flate"),
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

fun HEAD.simNavCrudPageStyles() {
    style {
        unsafe {
            +"""
            body { font-family: Arial, sans-serif; margin: 20px; }
            h1 { color: #333; }
            h2 { color: #666; margin-top: 30px; border-bottom: 2px solid #ddd; padding-bottom: 10px; display: flex; align-items: center; justify-content: space-between; }
            .empty { color: #999; font-style: italic; padding: 20px; }
            .section { margin: 30px 0; }
            .message { padding: 0.75rem; border-radius: 6px; margin-bottom: 1rem; }
            .message--ok { background: #ebfbee; border: 1px solid #b2f2bb; }
            .message--error { background: #fff5f5; border: 1px solid #ffc9c9; }
            .add-button {
                display: inline-flex;
                align-items: center;
                justify-content: center;
                border-radius: 999px;
                border: 1px solid #0d6efd;
                color: #0d6efd;
                text-decoration: none;
                font-size: 20px;
                line-height: 1;
                padding: 0.1rem 0.45rem;
            }
            table { border-collapse: collapse; width: 100%; margin-top: 10px; }
            th { background-color: #f0f0f0; border: 1px solid #ddd; padding: 10px; text-align: left; font-weight: bold; }
            td { border: 1px solid #ddd; padding: 10px; }
            tr:nth-child(even) { background-color: #f9f9f9; }
            .id { font-family: monospace; font-size: 0.9em; color: #666; }
            .actions { display: flex; gap: 0.5rem; }
            .inline-form { margin: 0; }
            .danger-link { color: #b42318; background: none; border: none; padding: 0; cursor: pointer; font: inherit; text-decoration: underline; }
            """.trimIndent()
        }
    }
}

fun HEAD.simNavFormPageStyles(
    fieldSelector: String = "input, select",
    monospaceFields: Boolean = false,
) {
    val fieldFontFamily = if (monospaceFields) "font-family: monospace;" else ""
    style {
        unsafe {
            +"""
            body { font-family: sans-serif; margin: 2rem; }
            form { border: 1px solid #d8d8d8; padding: 1rem; border-radius: 6px; }
            .field { margin-bottom: 0.75rem; display: flex; flex-direction: column; gap: 0.25rem; }
            $fieldSelector { padding: 0.4rem; $fieldFontFamily }
            button { padding: 0.5rem 0.8rem; }
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


