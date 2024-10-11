locals {
  datastream_id                       = "${var.application_name}-datastream"
  dataset_id                          = replace(local.datastream_id, "-", "_")
  cloud_sql_instance_underscore_name  = replace(var.cloud_sql_instance_name, "-", "_")
  cloud_sql_instance_publication_name = coalesce(var.cloud_sql_instance_publication_name, "${local.cloud_sql_instance_underscore_name}_publication")
  cloud_sql_instance_replication_name = coalesce(var.cloud_sql_instance_replication_name, "${local.cloud_sql_instance_underscore_name}_replication")
  cloud_sql_proxy_vm_name             = coalesce(var.cloud_sql_proxy_vm_name, "${var.application_name}-cloud-sql-auth-proxy")
  postgres_connection_profile_id      = coalesce(var.postgres_connection_profile_id, "${var.application_name}-postgresql-connection-profile")
}

locals {
  default_access_roles = [
    {
      role          = "OWNER"
      special_group = "projectOwners"
    },
    {
      role          = "READER"
      special_group = "projectReaders"
    },
    {
      role          = "WRITER"
      special_group = "projectWriters"
    },
  ]
}

variable "gcp_project" {
  type = map(string)
}

variable "datastream_vpc_resources" {
  description = "Common resources defined by the VPC and used in each individual Datastream."
  type        = map(string)

  validation {
    condition = alltrue([
      contains(keys(var.datastream_vpc_resources), "vpc_name"),
      contains(keys(var.datastream_vpc_resources), "private_connection_id"),
      contains(keys(var.datastream_vpc_resources), "bigquery_connection_profile_id"),
    ])
    error_message = "The map in the variable datastream_vpc_resources must contain the keys: datastream_vpc_name, datastream_private_connection_id, and bigquery_connection_profile_id."
  }
}

variable "application_name" {
  description = "The name of the application that ownes the source Cloud SQL instance."
  type        = string
}

variable "cloud_sql_instance_name" {
  description = "The name of the Cloud SQL instance owning the source database"
  type        = string
}
variable "cloud_sql_instance_db_name" {
  description = "The name of the source Cloud SQL instance database schema."
  type        = string
}

variable "cloud_sql_instance_db_credentials" {
  description = "The Datastream users credentials (username and password) used to access the source database."
  type        = map(string)
}

variable "datastream_desired_state" {
  description = "The state the Datastream should have after creation. Either PAUSED or RUNNING."
  type        = string
  default     = "RUNNING"
}

variable "cloud_sql_instance_publication_name" {
  description = "The name of the 'publiction' configured in the Cloud SQL instance database."
  type        = string
  default     = null
}

variable "cloud_sql_instance_replication_name" {
  description = "The name of the 'replicaton slot' configured in the Cloud SQL instance database."
  type        = string
  default     = null
}

variable "bigquery_table_freshness" {
  description = "The maximum time it takes for streamed data to be available in BigQuery. Shorter time increases cost."
  type        = string
  default     = "3600s"
}

variable "cloud_sql_proxy_vm_name" {
  description = "The name of the Cloud Auth SQL Auth Proxy VM to used to access the Cloud SQL instance."
  type        = string
  default     = null
}

variable "postgres_connection_profile_id" {
  description = "The name of the Datastream source connection profile used to access the Cloud SQL instance."
  type        = string
  default     = null
}

variable "cloud_sql_proxy_vm_machine_type" {
  description = "The Google Compute Engine machine type to use for the Cloud SQL Auth Proxy."
  type        = string
  default     = "e2-small"
}

variable "cloud_sql_proxy_logging_enabled" {
  description = "Enables GCP to collect logs from the Cloud SQL Auth Proxy."
  type        = bool
  default     = false
}

variable "cloud_sql_proxy_monitoring_enabled" {
  description = "Enables GCP monitoring of the Cloud SQL Auth Proxy."
  type        = bool
  default     = false
}

variable "big_query_dataset_delete_contents_on_destroy" {
  description = "Allows deleting BigQuery tables when the dataset is deleted."
  type        = bool
  default     = false
}

variable "access_roles" {
  description = "Custom roles that will be able to access the dataset created by the Datastream. Will be merged with default roles."
  type = list(object({
    role           = string
    special_group  = optional(string)
    group_by_email = optional(string)
    user_by_email  = optional(string)
  }))
  default = []
}

variable "authorized_views" {
  description = "Views that can access data in the dataset created by the Datastream."
  type = list(object({
    view = object({
      dataset_id = string
      project_id = string
      table_id   = string
    })
  }))
  default = []
}

variable "postgresql_include_schemas" {
  description = "A list of PostgreSQL schemas to include, each containing an optional list of tables and columns to include."
  type = list(object({
    schema = string
    tables = optional(list(object({
      table   = string
      columns = optional(list(string))
    })))
  }))
  default = [{ schema = "public" }]
}

variable "postgresql_exclude_schemas" {
  description = "A list of PostgreSQL schemas to exclude, each containing an optional list of tables and columns to exclude."
  type = list(object({
    schema = string
    tables = optional(list(object({
      table   = string
      columns = optional(list(string))
    })))
  }))
  default = [{ schema = "public", tables = [{ table = "flyway_schema_history" }] }]
}