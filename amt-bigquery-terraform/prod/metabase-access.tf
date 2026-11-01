locals {
  metabase_deltaker_view_readers = {
    deltaker_view                                    = "nada-sf1uv9kb3zvkhxlsmhnkw5@nada-prod-6977.iam.gserviceaccount.com"
    deltaker_status_view                             = "nada-pnef98ewrfbxodxsvpycs@nada-prod-6977.iam.gserviceaccount.com"
    deltaker_underoppfolging_view                    = "nada-qsmwicfezhwqdcvc8f64as@nada-prod-6977.iam.gserviceaccount.com"
    vedtak_view                                      = "nada-j2wbhxrcibhunmzfpdxoly@nada-prod-6977.iam.gserviceaccount.com"
    forlengelse_forslag_behandlet_for_sluttdato_view = "nada-963b83say3trzbt8weqobw@nada-prod-6977.iam.gserviceaccount.com"
    forlengelse_forslag_sendt_ift_sluttdato          = "nada-jbbdusbj9afwuuyowg1idm@nada-prod-6977.iam.gserviceaccount.com"
    forslag_view                                     = "nada-36yazh3bdgsukbcscxkgpt@nada-prod-6977.iam.gserviceaccount.com"
    endring_fra_arrangor_view                        = "nada-ukrnzxqne9we77pw7qtghv@nada-prod-6977.iam.gserviceaccount.com"
    endring_fra_tiltakskoordinator_view              = "nada-71nveyjfjbhiurgxtqdwmh@nada-prod-6977.iam.gserviceaccount.com"
    deltaker_endring_view                            = "nada-twanrdv9nslmtczsnnejwp@nada-prod-6977.iam.gserviceaccount.com"
  }

  metabase_distribusjon_view_readers = {
    public_tiltakshendelse = "nada-3sgijmeoyzgr2456p356ze@nada-prod-6977.iam.gserviceaccount.com"
    public_varsel          = "nada-gt8cmjmbfennd9dgje21nh@nada-prod-6977.iam.gserviceaccount.com"
    hendelse               = "nada-a7cu3xj98axe4erqkrjsdq@nada-prod-6977.iam.gserviceaccount.com"
    journaforing           = "nada-u3y4sntzmbjgs4mzdutkz1@nada-prod-6977.iam.gserviceaccount.com"
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
