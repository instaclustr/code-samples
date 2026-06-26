terraform {
  required_providers {
    instaclustr = {
      source  = "instaclustr/instaclustr"
      version = "~> 2.0"
    }
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# =============================================================================
# Variables
# =============================================================================

variable "instaclustr_terraform_key" {
  description = "Instaclustr API key"
  type        = string
  sensitive   = true
}

variable "my_ip_address" {
  description = "Your IP address for firewall rules (CIDR format)"
  type        = string
}

variable "aws_region" {
  description = "AWS region (must match Instaclustr region)"
  type        = string
  default     = "us-east-1"
}

variable "aws_profile" {
  description = "AWS CLI profile name after `aws configure sso` (e.g. my-company-sso). Leave null or \"\" to use the default credential chain (instance role, env vars, or `aws configure export-credentials`)."
  type        = string
  default     = null
  nullable    = true
}

variable "aws_vpc_cidr" {
  description = "CIDR block for AWS VPC"
  type        = string
  default     = "10.10.0.0/16"
}

variable "aws_public_subnet_cidr" {
  description = "CIDR for public subnet"
  type        = string
  default     = "10.10.1.0/24"
}

variable "aws_private_subnet_cidr" {
  description = "CIDR for private subnet"
  type        = string
  default     = "10.10.2.0/24"
}

variable "enable_test_instance" {
  description = "Create EC2 test instance"
  type        = bool
  default     = true
}

variable "provider_account_name" {
  description = "Instaclustr BYOC provider account name"
  type        = string
  default     = "<<< YOUR INSTACLUSTR ACCOUNT NAME HERE >>>"
}

# =============================================================================
# Providers
# =============================================================================

provider "instaclustr" {
  terraform_key = var.instaclustr_terraform_key
}

provider "aws" {
  region = var.aws_region
  # SSO / named profiles: set var.aws_profile. Otherwise omit (null) and use exported creds or env.
  profile = var.aws_profile != null && var.aws_profile != "" ? var.aws_profile : null
}

# =============================================================================
# Data Sources
# =============================================================================

data "aws_caller_identity" "current" {}

# Hardcoded AMI for Amazon Linux 2023 (us-east-1)
# Note: The SSM Parameter Store lookup requires ssm:GetParameter permission
# which may not be available in all accounts. Use a hardcoded AMI instead.
locals {
  amazon_linux_2023_ami = "ami-0c02fb55956c7d316"
  # Instaclustr exposes connection hostnames on node attributes; use for connector TLS/SNI (article: clickhouse_domain_names).
  clickhouse_connection_hosts = [for node in instaclustr_clickhouse_cluster_v2.clickhouse.data_centre[0].nodes : node.public_address]
}

# =============================================================================
# Kafka Cluster
# =============================================================================

resource "instaclustr_kafka_cluster_v3" "kafka" {
  name                         = "kafka"
  description                  = "Kafka with AWS VPC peering"
  kafka_version                = "4.1.1"
  sla_tier                     = "NON_PRODUCTION"
  auto_create_topics           = true
  allow_delete_topics          = true
  client_to_cluster_encryption = false
  private_network_cluster      = false
  pci_compliance_mode          = false
  default_number_of_partitions = 3
  default_replication_factor   = 3

  data_centre {
    cloud_provider        = "AWS_VPC"
    name                  = "AWS_VPC_US_EAST_1"
    network               = "10.0.0.0/16"
    region                = "US_EAST_1"
    number_of_nodes       = 3
    node_size             = "KFK-DEV-t4g.small-5"
    provider_account_name = var.provider_account_name
  }
}

# =============================================================================
# ClickHouse Cluster
# =============================================================================

resource "instaclustr_clickhouse_cluster_v2" "clickhouse" {
  name                    = "clickhouse"
  description             = "ClickHouse with AWS VPC peering"
  clickhouse_version      = "25.8.16"
  sla_tier                = "NON_PRODUCTION"
  private_network_cluster = false

  data_centre {
    cloud_provider        = "AWS_VPC"
    name                  = "AWS_VPC_US_EAST_1"
    network               = "10.6.0.0/16"
    region                = "US_EAST_1"
    node_size             = "CLK-DEV-m7i.large-50"
    shards                = 1
    replicas              = 3
    provider_account_name = var.provider_account_name
  }
}

# =============================================================================
# Kafka Connect Cluster
# =============================================================================

resource "instaclustr_kafka_connect_cluster_v2" "connect" {
  name                    = "connect"
  description             = "Kafka Connect with AWS VPC peering"
  kafka_connect_version   = "4.1.1"
  sla_tier                = "NON_PRODUCTION"
  private_network_cluster = false

  data_centre {
    cloud_provider        = "AWS_VPC"
    name                  = "AWS_VPC_US_EAST_1"
    network               = "10.2.0.0/16"
    region                = "US_EAST_1"
    number_of_nodes       = 3
    node_size             = "KCN-DEV-t4g.medium-30"
    replication_factor    = 3
    provider_account_name = var.provider_account_name
  }

  target_cluster {
    managed_cluster {
      kafka_connect_vpc_type  = "VPC_PEERED"
      target_kafka_cluster_id = instaclustr_kafka_cluster_v3.kafka.id
    }
  }
}

# =============================================================================
# Firewall Rules
# =============================================================================

resource "instaclustr_cluster_network_firewall_rules_v2" "kafka_firewall" {
  cluster_id = instaclustr_kafka_cluster_v3.kafka.id

  firewall_rule {
    network = "10.2.0.0/16" # Kafka Connect VPC
    type    = "KAFKA"
  }

  firewall_rule {
    network = var.aws_vpc_cidr # Your pipeline VPC
    type    = "KAFKA"
  }

  firewall_rule {
    network = var.my_ip_address
    type    = "KAFKA"
  }
}

resource "instaclustr_cluster_network_firewall_rules_v2" "clickhouse_firewall" {
  cluster_id = instaclustr_clickhouse_cluster_v2.clickhouse.id

  firewall_rule {
    network = "10.2.0.0/16" # Kafka Connect VPC
    type    = "CLICKHOUSE"
  }

  firewall_rule {
    network = "10.2.0.0/16"
    type    = "CLICKHOUSE_WEB"
  }

  firewall_rule {
    network = var.aws_vpc_cidr
    type    = "CLICKHOUSE"
  }

  firewall_rule {
    network = var.aws_vpc_cidr
    type    = "CLICKHOUSE_WEB"
  }

  firewall_rule {
    network = var.my_ip_address
    type    = "CLICKHOUSE"
  }

  firewall_rule {
    network = var.my_ip_address
    type    = "CLICKHOUSE_WEB"
  }
}

resource "instaclustr_cluster_network_firewall_rules_v2" "connect_firewall" {
  cluster_id = instaclustr_kafka_connect_cluster_v2.connect.id

  firewall_rule {
    network = var.aws_vpc_cidr
    type    = "KAFKA_CONNECT"
  }

  firewall_rule {
    network = var.my_ip_address
    type    = "KAFKA_CONNECT"
  }
}

# =============================================================================
# AWS VPC Infrastructure
# =============================================================================

resource "aws_vpc" "main" {
  cidr_block           = var.aws_vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name    = "pipeline-vpc"
    Project = "instaclustr-tutorial"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "pipeline-igw" }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.aws_public_subnet_cidr
  availability_zone       = "us-east-1a"
  map_public_ip_on_launch = true
  tags                    = { Name = "pipeline-public" }
}

resource "aws_subnet" "private" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.aws_private_subnet_cidr
  availability_zone = "us-east-1a"
  tags              = { Name = "pipeline-private" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "pipeline-public-rt" }
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "pipeline-private-rt" }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private" {
  subnet_id      = aws_subnet.private.id
  route_table_id = aws_route_table.private.id
}

# =============================================================================
# Security Group & EC2 Test Instance
# =============================================================================

resource "aws_security_group" "test_instance" {
  name        = "pipeline-test-sg"
  description = "Security group for connectivity testing"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.my_ip_address]
    description = "SSH access"
  }

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["18.206.107.24/29"]
    description = "EC2 Instance Connect for us-east-1"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "pipeline-test-sg" }
}

resource "aws_instance" "test" {
  count = var.enable_test_instance ? 1 : 0

  ami                         = local.amazon_linux_2023_ami
  instance_type               = "t3.micro"
  subnet_id                   = aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.test_instance.id]
  associate_public_ip_address = true

  user_data = <<-EOF
              #!/bin/bash
              yum update -y
              yum install -y nc telnet curl java-17-amazon-corretto

              # Install Kafka CLI tools
              cd /opt
              curl -O https://archive.apache.org/dist/kafka/3.7.0/kafka_2.13-3.7.0.tgz
              tar -xzf kafka_2.13-3.7.0.tgz
              ln -s kafka_2.13-3.7.0 kafka
              echo 'export PATH=$PATH:/opt/kafka/bin' >> /etc/profile.d/kafka.sh
              EOF

  tags = { Name = "pipeline-test" }
}

# =============================================================================
# Outputs
# =============================================================================

output "aws_account_id"       { value = data.aws_caller_identity.current.account_id }
output "aws_vpc_id"           { value = aws_vpc.main.id }
output "aws_vpc_cidr"         { value = aws_vpc.main.cidr_block }
output "aws_route_table_id"   { value = aws_route_table.public.id }

output "kafka_cluster_id" { value = instaclustr_kafka_cluster_v3.kafka.id }
output "kafka_private_ips" {
  value = [for node in instaclustr_kafka_cluster_v3.kafka.data_centre[0].nodes : node.private_address]
}
output "kafka_bootstrap_servers" {
  value = join(",", [for node in instaclustr_kafka_cluster_v3.kafka.data_centre[0].nodes : "${node.public_address}:9093"])
}

output "clickhouse_cluster_id" { value = instaclustr_clickhouse_cluster_v2.clickhouse.id }
output "clickhouse_private_ips" {
  value = [for node in instaclustr_clickhouse_cluster_v2.clickhouse.data_centre[0].nodes : node.private_address]
}
output "clickhouse_domain_names" {
  description = "Hostnames for ClickHouse HTTPS (use with connector hostname / TLS); same source as clickhouse_endpoints."
  value       = local.clickhouse_connection_hosts
}
output "clickhouse_endpoints" {
  description = "Alias of clickhouse_domain_names for compatibility with older drafts."
  value       = local.clickhouse_connection_hosts
}

output "kafka_connect_cluster_id" { value = instaclustr_kafka_connect_cluster_v2.connect.id }
output "kafka_connect_private_ips" {
  value = [for node in instaclustr_kafka_connect_cluster_v2.connect.data_centre[0].nodes : node.private_address]
}

output "test_instance_id"         { value = var.enable_test_instance ? aws_instance.test[0].id : null }
output "test_instance_public_ip"  { value = var.enable_test_instance ? aws_instance.test[0].public_ip : null }

output "vpc_peering_instructions" {
  value = <<-EOT

    ╔══════════════════════════════════════════════════════════════════════════╗
    ║                     VPC PEERING (customer BYOC)                        ║
    ╠══════════════════════════════════════════════════════════════════════════╣
    ║  All cluster VPCs and your pipeline VPC are in YOUR AWS account          ║
    ║  (${data.aws_caller_identity.current.account_id}).                        ║
    ╠══════════════════════════════════════════════════════════════════════════╣
    ║                                                                          ║
    ║  STEP 1: Instaclustr Console — pipeline VPC <-> each cluster (×3)        ║
    ║  For Kafka, Kafka Connect, and ClickHouse: cluster -> Settings ->          ║
    ║  VPC Peering -> Add VPC Peering / New VPC Connection. Enter:              ║
    ║    • AWS Account ID : ${data.aws_caller_identity.current.account_id}
    ║    • VPC ID         : ${aws_vpc.main.id}
    ║    • VPC CIDR       : ${var.aws_vpc_cidr}
    ║    • Region         : ${var.aws_region}
    ║                                                                          ║
    ║  STEP 2: AWS Console - accept + route in YOUR pipeline VPC               ║
    ║  VPC -> Peering Connections -> Accept each pending request (×3).           ║
    ║  Route table ${aws_route_table.public.id} -> add routes:                 ║
    ║    • 10.0.0.0/16 -> pcx-xxx (Kafka)                                       ║
    ║    • 10.2.0.0/16 -> pcx-xxx (Kafka Connect)                               ║
    ║    • 10.6.0.0/16 -> pcx-xxx (ClickHouse)                                  ║
    ║                                                                          ║
    ║  STEP 3: AWS Console — Kafka Connect VPC ↔ ClickHouse VPC (direct)       ║
    ║  Required: VPC peering is NOT transitive; Connect (10.2.0.0/16) and      ║
    ║  ClickHouse (10.6.0.0/16) need their own peering in THIS account.         ║
    ║                                                                        ║
    ║  VPC → Peering Connections → Create peering connection:                ║
    ║    • Requester: Kafka Connect managed VPC (10.2.0.0/16)                  ║
    ║    • Accepter:  ClickHouse managed VPC (10.6.0.0/16)                     ║
    ║                                                                        ║
    ║  Create -> Accept (same account).                                         ║
    ║  Routes on BOTH sides (subnet-associated / non-main RTs as needed):      ║
    ║    • Connect VPC RT:    10.6.0.0/16 -> that pcx-*                          ║
    ║    • ClickHouse VPC RT: 10.2.0.0/16 -> same pcx-*                         ║
    ║  Do not add 10.2.0.0/16 as a remote route inside the Connect VPC.        ║
    ║                                                                          ║
    ║  STEP 4: Instaclustr — Connected Clusters (TLS trust, not peering)      ║
    ║  Kafka Connect cluster → Connected Clusters → add ClickHouse; leave        ║
    ║  "Use Private IPs" unchecked per tutorial. Then sink uses                ║
    ║  /trusted-clusters.jks and ClickHouse DOMAIN from outputs (not raw IP).    ║
    ║                                                                          ║
    ║  STEP 5: EC2 Instance Connect — reachability (same ports as E2E)         ║
    ║    nc -zv <KAFKA_PRIVATE_IP> 9093                                        ║
    ║    nc -zv <CLICKHOUSE_PRIVATE_IP> 8443                                   ║
    ║                                                                          ║
    ╚══════════════════════════════════════════════════════════════════════════╝

  EOT
}
