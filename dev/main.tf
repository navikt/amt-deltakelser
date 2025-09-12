terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.44.0"
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
