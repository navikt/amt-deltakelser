variable "gcp_project" {
  description = "GCP project and region defaults."
  type        = map(string)
  default = {
    region  = "europe-north1",
    project = "amt-dev-d98f"
  }
}

variable "amttiltak_bigquery_secret" {
  description = "The key of the GCP secret that provides the amt-tiltak database credentials."
  type        = string
}

variable "amttiltak_datastream_secret" {
  description = "The key of the GCP secret that provides the amt-tiltak datastream credentials."
  type        = string
}

variable "amttiltak_cloud_sql_port" {
  description = "The port exposed by the amt-tiltak database Cloud SQL instance."
  type        = string
  default     = "5432"
}