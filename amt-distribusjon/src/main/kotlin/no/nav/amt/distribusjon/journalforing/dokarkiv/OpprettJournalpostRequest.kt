package no.nav.amt.distribusjon.journalforing.dokarkiv

data class OpprettJournalpostRequest(
    val avsenderMottaker: AvsenderMottaker,
    val bruker: Bruker,
    val dokumenter: List<Dokument>,
    val journalfoerendeEnhet: String,
    val journalposttype: String = "UTGAAENDE",
    val kanal: String = "NAV_NO_UTEN_VARSLING",
    val sak: Sak,
    val tema: String,
    val tittel: String,
    val eksternReferanseId: String,
)

data class AvsenderMottaker(
    val id: String,
    val idType: String = "FNR",
)

data class Bruker(
    val id: String,
    val idType: String = "FNR",
)

data class Dokument(
    val brevkode: String,
    val dokumentvarianter: List<DokumentVariant>,
    val tittel: String,
)

data class DokumentVariant(
    val filtype: String = "PDFA",
    val fysiskDokument: ByteArray,
    val variantformat: String = "ARKIV",
)

data class Sak(
    val fagsakId: String,
    val fagsaksystem: String,
    val sakstype: String = "FAGSAK",
)
