variable "location" {
  description = "The resource location (region)."
  type        = string
}

variable "dataset_id" {
  description = "The dataset the module resources belongs to."
  type        = string
}

variable "table_id" {
  description = "The name of the table to create."
  type        = string
}

variable "table_schema" {
  description = "The table schema."
  type        = string
}

variable "deletion_protection" {
  description = "If the table or view can be deleted even when data is present."
  type        = bool
  default     = true
}

variable "data_transfer_display_name" {
  description = "The data transfer identifier"
  type        = string
}

variable "data_transfer_data_source_id" {
  description = "The documentation states that this value can be changed, but any other value than the default causes failure."
  type        = string
  default     = "scheduled_query"
}

variable "data_transfer_schedule" {
  description = "When the data transfer should run."
  type        = string
}

variable "data_transfer_service_account" {
  description = "The service account used to run the data transfer."
  type        = string
}

variable "data_transfer_start_time" {
  description = "The date and time scheduling of the data transfer can start."
  type        = string
}

variable "data_transfer_destination_table" {
  description = "The table the data transfer should write to"
  type        = string
}

variable "data_transfer_mode" {
  description = "The data transfere write mode, witch is either WRITE_APPEND or WRITE_TRUNCATE (overwrite)."
  type        = string
  default     = "WRITE_APPEND"
}

variable "data_transfer_query" {
  description = "The SQL used to fetch data from the external resource."
  type        = string
}

