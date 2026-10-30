variable "gcp_project" {
  description = "GCP project and region defaults."
  type        = map(string)
  default = {
    region  = "europe-north1",
    zone    = "europe-north1-c",
    project = "amt-prod-d4dd"
  }
}

variable "enable_amt_deltaker_datastream" {
  description = "Enable datastream for amt-deltaker"
  type        = bool
  default     = true
}

variable "enable_amt_distribusjon_datastream" {
  description = "Enable datastream for amt-distribusjon"
  type        = bool
  default     = true
}

variable "amt_deltaker_datastream_secret" {
  description = "The key of the GCP secret that provides the amt-deltaker datastream credentials."
  type        = string
  default     = "amt-deltaker-datastream-credentials"
}

variable "amt_distribusjon_datastream_secret" {
  description = "The key of the GCP secret that provides the amt-distribusjon datastream credentials."
  type        = string
  default     = "amt-distribusjon-datastream-credentials"
}