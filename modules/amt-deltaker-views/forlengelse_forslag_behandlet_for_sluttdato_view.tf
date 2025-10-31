module "forlengelse_forslag_behandlet_for_sluttdato_view" {
  source              = "../google-bigquery-view"
  deletion_protection = false
  dataset_id          = var.dataset_id
  view_id             = "forlengelse_forslag_behandlet_for_sluttdato_view"

  view_schema = jsonencode([
    { name = "percentage", type = "FLOAT", mode = "NULLABLE", description = "Prosentandel forslag behandlet for sluttdato." }
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
         JSON_EXTRACT_SCALAR(f.status, '$.godkjent') AS forslag_godkjent,
         s.created_at AS forrige_sluttdato_created_at,
         s.sluttdato AS forrige_sluttdato,
         JSON_EXTRACT_SCALAR(f.endring, '$.sluttdato') AS ny_sluttdato,
         ROW_NUMBER() OVER (PARTITION BY f.id ORDER BY s.created_at DESC) AS rn_per_forslag
  FROM latest_sluttdato s
  JOIN `${var.gcp_project["project"]}.${var.dataset_id}.public_forslag` f
    ON s.deltaker_id = f.deltaker_id AND s.created_at < f.created_at
  WHERE JSON_EXTRACT_SCALAR(f.endring, '$.type') = 'ForlengDeltakelse'
    AND JSON_EXTRACT_SCALAR(f.status, '$.godkjent') IS NOT NULL
)
SELECT (COUNT(CASE WHEN forslag_godkjent < forrige_sluttdato THEN 1 END)/COUNT(*)*100) AS percentage
FROM ranked_results
WHERE rn_per_forslag = 1
EOF
}
