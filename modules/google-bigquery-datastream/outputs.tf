output "dataset_id" {
  description = "The ID of the BigQuery Dataset created by the module."
  value       = google_bigquery_dataset.datastream_dataset.dataset_id
}