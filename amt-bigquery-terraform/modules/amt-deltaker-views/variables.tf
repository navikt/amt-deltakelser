variable "gcp_project" {
  description = "GCP prosjekt (map med minst 'project')"
  type        = map(string)
}

variable "dataset_id" {
  description = "Dataset-id hvor viewene skal opprettes"
  type        = string
}
