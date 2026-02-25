# Postgres-ANF Performance Test Scripts

## Description

Scripts used to performance test the Instaclustr PostgreSQL on Azure NetApp Files (ANF) managed service. These scripts automate pgbench-based benchmarking with both read-only and read-write workloads, collecting detailed performance statistics including transactions per second (TPS), WAL volume, and database size metrics.

Software provided as a sample as per our [support documentation](https://www.instaclustr.com/support/documentation/announcements/instaclustr-open-source-project-status/).

## Getting Started

1. Ensure you have access to a PostgreSQL instance (see Prerequisites).
2. Review and update the configuration variables at the top of each script (database host, scale factor, durations, etc.).
3. Run the initialization script to set up the benchmark database:
   ```bash
   bash scripts/run-init-remote.sh
   ```
4. Run the full benchmark suite:
   ```bash
   bash scripts/run-bench-remote.sh
   ```
5. Optionally, collect live performance statistics during a benchmark run:
   ```bash
   bash scripts/collect-stats.sh <duration_seconds> <output_directory>
   ```

The scripts include:
- **run-remote.sh** - Main orchestrator that combines initialization and benchmarking
- **run-init-remote.sh** - Initializes pgbench database, performs VACUUM FREEZE, and collects baseline measurements
- **run-bench-remote.sh** - Runs read-only and read-write benchmarks with varying client counts (4, 8, 16, 32, 64, 96) and generates gnuplot-ready output
- **collect-stats.sh** - Collects `pg_stat_database`, `pg_stat_bgwriter`, and `pg_stat_archiver` metrics to CSV files

### Prerequisites

- **PostgreSQL** instance (e.g., [Instaclustr managed PostgreSQL](https://www.instaclustr.com/products/postgresql/))
- **psql** client installed locally
- **pgbench** (included with PostgreSQL)
- **gnuplot** (optional, for generating performance charts)
- **Bash** shell
- Network access from your machine to the PostgreSQL instance

## Deployment

These scripts are designed to run from a client machine against a remote PostgreSQL instance. No server-side deployment is required.

Key configuration variables in the scripts:
- `SCALE` - pgbench scale factor (default: 10000)
- `JOBS` - parallel pgbench jobs (default: 8)
- `DURATION_RO` - read-only test duration in seconds (default: 900)
- `DURATION_RW` - read-write test duration in seconds (default: 1800)

## Authors

* [NetApp Instaclustr](https://github.com/Instaclustr)

See also the list of [MAINTAINERS](https://github.com/instaclustr/code-samples/blob/main/Maintainer.md) who participated in projects in this repository.

## License

Copyright 2021 Instaclustr Pty Ltd

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
