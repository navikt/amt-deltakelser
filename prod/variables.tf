variable "gcp_project" {
  description = "GCP project and region defaults."
  type        = map(string)
  default = {
    region  = "europe-north1",
    zone    = "europe-north1-c",
    project = "amt-prod-d4dd"
  }
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

variable "metabase_service_account" {
  description = "The Metabase Service Account used when accessing data. Must have access to tables underlying views."
  type        = string
  default     = "nada-metabase@nada-prod-6977.iam.gserviceaccount.com"
}