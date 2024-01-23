terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "4.55.0"
    }
  }

  backend "gcs" {
    bucket = "amt-terraform-state-dev"
  }
}

provider "google" {
  project = var.gcp_project["project"]
  region  = var.gcp_project["region"]
}

data "google_project" "project" {}

module "google_storage_bucket" {
  source = "../modules/google-cloud-storage"

  name     = "amt-testbucket"
  location = var.gcp_project["region"]
}
