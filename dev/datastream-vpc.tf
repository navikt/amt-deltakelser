resource "google_compute_network" "amt_datastream_private_vpc" {
  name    = "amt-datastream-vpc"
  project = var.gcp_project["project"]
}

// The IP-range in the VPC used for the Datastream VPC peering. If a Cloud SQL instance is assigned a private
// IP address, this is the range it will be assigned from.
resource "google_compute_global_address" "amt_datastream_vpc_ip_range" {
  name          = "amt-datastream-vpc-ip-range"
  project       = var.gcp_project["project"]
  address_type  = "INTERNAL"
  purpose       = "VPC_PEERING"
  network       = google_compute_network.amt_datastream_private_vpc.id
  prefix_length = 20
}

// Private connectivity lets you create a peered configuration between your VPC and Datastream’s private network.
// A single configuration can be used by all streams and connection profiles within a single region.
resource "google_datastream_private_connection" "amt_datastream_private_connection" {
  location              = var.gcp_project["region"]
  display_name          = "amt-datastream-private-connection"
  private_connection_id = "amt-datastream-private-connection"

  vpc_peering_config {
    vpc    = google_compute_network.amt_datastream_private_vpc.id
    subnet = "10.1.0.0/29"
  }
}

// VPC Firewall rules control incoming or outgoing traffic to an instance. By default, incoming traffic from outside
// your network is blocked. Since we are using a Cloud SQL reverse proxy, we need to then create an ingress firewall
// rule that allows traffic on the source database port.
resource "google_compute_firewall" "allow_datastream_to_cloud_sql" {
  project = var.gcp_project["project"]
  name    = "allow-datastream-to-cloud-sql"
  network = google_compute_network.amt_datastream_private_vpc.name

  allow {
    protocol = "tcp"
    ports = [
      var.amttiltak_cloud_sql_port,
      var.amt_deltaker_cloud_sql_port
    ]
  }

  source_ranges = [google_datastream_private_connection.amt_datastream_private_connection.vpc_peering_config.0.subnet]
}

// Datastream connection profile for BigQuery target. Can be used by multiple streams.
resource "google_datastream_connection_profile" "datastream_bigquery_connection_profile" {
  display_name          = "datastream-bigquery-connection-profile"
  location              = var.gcp_project["region"]
  connection_profile_id = "datastream-bigquery-connection-profile"

  bigquery_profile {}
}
