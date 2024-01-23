variable "name" {
  description = "The name of the bucket."
  type        = string
}

variable "location" {
  description = "The bucket location."
  type        = string
}

variable "storage_class" {
  description = "The bucket storage class."
  type        = string
  default     = "STANDARD"
}

variable "versioning" {
  description = "If the bucket content should be versioned or not."
  type        = bool
  default     = true
}
variable "force_destroy" {
  description = "If the bucket can be deleted if it contains content."
  type        = bool
  default     = false
}