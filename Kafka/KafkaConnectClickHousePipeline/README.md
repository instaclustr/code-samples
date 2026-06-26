# Streaming Analytics Pipeline - Terraform (Kafka + Kafka Connect + ClickHouse on AWS VPC)

## Description
A single Terraform configuration (`main_03_gist.tf`) that provisions an Instaclustr
BYOC streaming analytics pipeline peered into your own AWS VPC.

**Technologies:** Apache Kafka (primary), Kafka Connect, ClickHouse, AWS VPC, Terraform.

## What it does
Provisions and wires together, in `us-east-1`:
- Instaclustr Kafka, Kafka Connect, and ClickHouse clusters (all `AWS_VPC`,
  `NON_PRODUCTION` SLA tier).
- An AWS VPC with public and private subnets, an internet gateway, and route tables.
- Firewall rules for each cluster, scoped to the Kafka Connect VPC, your pipeline
  VPC, and your own IP.
- An optional EC2 test instance (Amazon Linux 2023, Kafka CLI tools pre-installed)
  for connectivity testing - toggle with `enable_test_instance`.
- Outputs for every cluster (IDs, private IPs, bootstrap servers, ClickHouse
  hostnames) plus a step-by-step `vpc_peering_instructions` block covering the manual
  peering work Terraform cannot do.

## Prerequisites
- Terraform, with the `instaclustr` (`~> 2.0`) and `aws` (`~> 5.0`) providers.
- An Instaclustr account and an Instaclustr API (Terraform) key.
- AWS credentials - either an SSO/named profile (`var.aws_profile`) or exported
  credentials / environment variables.
- Your Instaclustr BYOC provider account name (set `var.provider_account_name`).

## Examples / how to run
1. Create a `terraform.tfvars` and set at minimum:
   - `instaclustr_terraform_key` - your Instaclustr API key
   - `my_ip_address` - your IP in CIDR form (e.g. 203.0.113.50/32)
   - `provider_account_name` - your Instaclustr BYOC account name
2. Authenticate to AWS (SSO example):
   aws sso login && export $(aws configure export-credentials --profile YOUR_PROFILE --format env)
3. Apply:
   terraform init
   terraform apply
4. Follow the `vpc_peering_instructions` output to complete the manual VPC peering
   and Connected Clusters steps.

## Additional Materials
Tutorial: https://www.instaclustr.com/blog/how-to-build-a-streaming-analytics-pipeline-with-terraform-and-instaclustr-part-3-integrating-with-aws-vpc/

## Notes
- VPC peering is not transitive. Kafka Connect and ClickHouse need their own direct
  peering connection in addition to the pipeline-VPC peerings.
- The Kafka Connect target uses `kafka_connect_vpc_type = "VPC_PEERED"` (not
  `KAFKA_VPC`) - an undocumented gotcha when peering into your own VPC.
- Use the ClickHouse domain names (`clickhouse_domain_names` output) for TLS/SNI,
  not the raw IPs. `clickhouse_endpoints` is an alias of the same value kept for
  compatibility with earlier drafts.
- The Amazon Linux 2023 AMI is hardcoded for `us-east-1` to avoid requiring
  `ssm:GetParameter`; change it if you switch regions.
- All sensitive values are variables (no real credentials are committed). Replace the
  placeholder `provider_account_name` default with your own account name before applying.

## License
Apache 2.0
