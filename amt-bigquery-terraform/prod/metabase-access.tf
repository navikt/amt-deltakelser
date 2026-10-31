locals {
  metabase_deltaker_view_readers = {
    forslag_view                        = "nada-36yazh3bdgsukbcscxkgpt@nada-prod-6977.iam.gserviceaccount.com"
    endring_fra_arrangor_view           = "nada-ukrnzxqne9we77pw7qtghv@nada-prod-6977.iam.gserviceaccount.com"
    endring_fra_tiltakskoordinator_view = "nada-71nveyjfjbhiurgxtqdwmh@nada-prod-6977.iam.gserviceaccount.com"
    deltaker_endring_view               = "nada-twanrdv9nslmtczsnnejwp@nada-prod-6977.iam.gserviceaccount.com"
  }

  metabase_distribusjon_view_readers = {
    journaforing = "nada-u3y4sntzmbjgs4mzdutkz1@nada-prod-6977.iam.gserviceaccount.com"
  }
}

resource "google_bigquery_table_iam_member" "metabase_deltaker" {
  for_each = var.enable_amt_deltaker_datastream ? local.metabase_deltaker_view_readers : {}

  project    = var.gcp_project["project"]
  dataset_id = module.amt_deltaker_datastream[0].dataset_id
  table_id   = each.key
  role       = "roles/bigquery.dataViewer"
  member     = "serviceAccount:${each.value}"

  depends_on = [module.amt_deltaker_views]
}

resource "google_bigquery_table_iam_member" "metabase_distribusjon" {
  for_each = var.enable_amt_distribusjon_datastream ? local.metabase_distribusjon_view_readers : {}

  project    = var.gcp_project["project"]
  dataset_id = module.amt_distribusjon_datastream[0].dataset_id
  table_id   = each.key
  role       = "roles/bigquery.dataViewer"
  member     = "serviceAccount:${each.value}"

  depends_on = [module.amt_distribusjon_views]
}
