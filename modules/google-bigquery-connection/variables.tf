variable "location" {
  description = "The resource location."
  type        = string
}

variable "connection_id" {
  description = ""
  type        = string
}

variable "instance_id" {
  description = ""
  type        = string
}

variable "database" {
  description = ""
  type        = string
}

variable "database_type" {
  description = ""
  type        = string
  default     = "POSTGRES"
}

variable "username" {
  description = ""
  type        = string
}

variable "password" {
  description = ""
  type        = string
}
