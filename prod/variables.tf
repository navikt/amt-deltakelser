variable "gcp_project" {
  description = "GCP project and region defaults."
  type        = map(string)
  default = {
    region  = "europe-north1",
    zone    = "europe-north1-c",
    project = "amt-prod-d4dd"
  }
}

variable "enable_amt_tiltak_datastream" {
  description = "Enable datastream for amt-tiltak"
  type        = bool
  default     = false
}

variable "enable_amt_deltaker_datastream" {
  description = "Enable datastream for amt-deltaker"
  type        = bool
  default     = false
}

variable "enable_amt_distribusjon_datastream" {
  description = "Enable datastream for amt-distribusjon"
  type        = bool
  default     = false
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

variable "amt_deltaker_datastream_secret" {
  description = "The key of the GCP secret that provides the amt-deltaker datastream credentials."
  type        = string
}

variable "amt_deltaker_cloud_sql_port" {
  description = "The port exposed by the amt-deltaker database Cloud SQL instance."
  type        = string
  default     = "5432"
}

variable "amt_distribusjon_datastream_secret" {
  description = "The key of the GCP secret that provides the amt-distribusjon datastream credentials."
  type        = string
}

variable "amt_distribusjon_cloud_sql_port" {
  description = "The port exposed by the amt-distribusjon database Cloud SQL instance."
  type        = string
  default     = "5432"
}
