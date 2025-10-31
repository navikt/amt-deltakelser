module "forlengelse_forslag_sendt_ift_sluttdato" {
  source              = "../google-bigquery-view"
  deletion_protection = false
  dataset_id          = var.dataset_id
  view_id             = "forlengelse_forslag_sendt_ift_sluttdato"

  view_schema = jsonencode([
    { name = "time_diff_bucket", type = "STRING", mode = "NULLABLE", description = "Tidsforskjell mellom sluttdato og forslag sendt." },
    { name = "count_per_bucket", type = "INT64", mode = "NULLABLE", description = "Antall forslag per tidsbucket." },
    { name = "bucket_order", type = "INT64", mode = "NULLABLE", description = "Rekkefølge for bucket." }
  ])

  view_query = <<EOF
WITH sluttdatoer AS (
  SELECT deltaker_id, JSON_EXTRACT_SCALAR(endring, '$.sluttdato') AS sluttdato, created_at
  FROM `${var.gcp_project["project"]}.${var.dataset_id}.public_deltaker_endring`
  WHERE JSON_EXTRACT_SCALAR(endring, '$.sluttdato') IS NOT NULL
  UNION ALL
  SELECT deltaker_id, JSON_EXTRACT_SCALAR(deltaker_ved_import, '$.sluttdato') AS sluttdato, importert_dato AS created_at
  FROM `${var.gcp_project["project"]}.${var.dataset_id}.public_importert_fra_arena`
  WHERE JSON_EXTRACT_SCALAR(deltaker_ved_import, '$.sluttdato') IS NOT NULL
  UNION ALL
  SELECT deltaker_id, JSON_EXTRACT_SCALAR(endring, '$.sluttdato') AS sluttdato, created_at
  FROM `${var.gcp_project["project"]}.${var.dataset_id}.public_endring_fra_arrangor`
  WHERE JSON_EXTRACT_SCALAR(endring, '$.sluttdato') IS NOT NULL
),
latest_sluttdato AS (
  SELECT s.deltaker_id, s.sluttdato, s.created_at,
         ROW_NUMBER() OVER (PARTITION BY s.deltaker_id ORDER BY s.created_at DESC) AS rn
  FROM sluttdatoer s
),
ranked_results AS (
  SELECT f.id AS forslag_id, f.created_at AS forslag_created_at,
         s.created_at AS forrige_sluttdato_created_at,
         s.sluttdato AS forrige_sluttdato,
         JSON_EXTRACT_SCALAR(f.endring, '$.sluttdato') AS ny_sluttdato,
         ROW_NUMBER() OVER (PARTITION BY f.id ORDER BY s.created_at DESC) AS rn_per_forslag
  FROM latest_sluttdato s
  JOIN `${var.gcp_project["project"]}.${var.dataset_id}.public_forslag` f
    ON s.deltaker_id = f.deltaker_id AND s.created_at < f.created_at
  WHERE JSON_EXTRACT_SCALAR(f.endring, '$.type') = 'ForlengDeltakelse'
),
time_differences AS (
  SELECT TIMESTAMP_DIFF(DATE(forrige_sluttdato), DATE(forslag_created_at), DAY) AS time_diff_days
  FROM ranked_results
  WHERE rn_per_forslag = 1
)
SELECT
  CASE
    WHEN time_diff_days < -5 THEN 'Mer enn 5 dager etter sluttdato'
    WHEN time_diff_days = -5 THEN '-5'
    WHEN time_diff_days = -4 THEN '-4'
    WHEN time_diff_days = -3 THEN '-3'
    WHEN time_diff_days = -2 THEN '-2'
    WHEN time_diff_days = -1 THEN '-1'
    WHEN time_diff_days = 0 THEN 'Samme dag som sluttdato'
    WHEN time_diff_days = 1 THEN '1'
    WHEN time_diff_days = 2 THEN '2'
    WHEN time_diff_days = 3 THEN '3'
    WHEN time_diff_days = 4 THEN '4'
    WHEN time_diff_days = 5 THEN '5'
    WHEN time_diff_days > 5 THEN 'Mer enn 5 dager før sluttdato'
  END AS time_diff_bucket,
  COUNT(*) AS count_per_bucket,
  CASE
    WHEN time_diff_days < -5 THEN 1
    WHEN time_diff_days = -5 THEN 2
    WHEN time_diff_days = -4 THEN 3
    WHEN time_diff_days = -3 THEN 4
    WHEN time_diff_days = -2 THEN 5
    WHEN time_diff_days = -1 THEN 6
    WHEN time_diff_days = 0 THEN 7
    WHEN time_diff_days = 1 THEN 8
    WHEN time_diff_days = 2 THEN 9
    WHEN time_diff_days = 3 THEN 10
    WHEN time_diff_days = 4 THEN 11
    WHEN time_diff_days = 5 THEN 12
    WHEN time_diff_days > 5 THEN 13
  END AS bucket_order
FROM time_differences
GROUP BY time_diff_bucket, bucket_order
ORDER BY bucket_order
EOF
}
