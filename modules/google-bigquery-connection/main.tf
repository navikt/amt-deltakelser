resource "google_bigquery_connection" "bigquery_connection" {
  connection_id = var.connection_id
  location      = var.location

  cloud_sql {
    instance_id = var.instance_id
    database    = var.database
    type        = var.database_type
    credential {
      username = var.username
      password = var.password
    }
  }
}