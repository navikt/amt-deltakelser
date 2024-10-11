resource "google_bigquery_dataset" "amttiltak_dataset" {
  dataset_id    = "amttiltak_dataset"
  location      = var.gcp_project["region"]
  friendly_name = "amttiltak_dataset"
  labels        = {}

  access {
    role          = "OWNER"
    special_group = "projectOwners"
  }
  access {
    role          = "READER"
    special_group = "projectReaders"
  }
  access {
    role           = "READER"
    group_by_email = "all-users@nav.no"
  }
  access {
    role          = "WRITER"
    special_group = "projectWriters"
  }

  timeouts {}
}

resource "google_bigquery_table_iam_binding" "amttiltak_deltaker_personident_view_iam_binding" {
  project    = var.gcp_project.project
  dataset_id = google_bigquery_dataset.amttiltak_dataset.dataset_id
  table_id   = module.amttiltak_deltaker_personident_view.bigquery_view_id
  role       = "roles/bigquery.dataViewer"
  members = [
    "group:all-users@nav.no"
  ]
}

resource "google_bigquery_table_iam_binding" "amttiltak_deltaker_siste_status_view_iam_binding" {
  project    = var.gcp_project.project
  dataset_id = google_bigquery_dataset.amttiltak_dataset.dataset_id
  table_id   = module.amttiltak_deltaker_siste_status_view.bigquery_view_id
  role       = "roles/bigquery.dataViewer"
  members = [
    "group:all-users@nav.no"
  ]
}

resource "google_bigquery_table_iam_binding" "amttiltak_deltaker_status_view_view_iam_binding" {
  project    = var.gcp_project.project
  dataset_id = google_bigquery_dataset.amttiltak_dataset.dataset_id
  table_id   = module.amttiltak_deltaker_status_view.bigquery_view_id
  role       = "roles/bigquery.dataViewer"
  members = [
    "group:all-users@nav.no"
  ]
}