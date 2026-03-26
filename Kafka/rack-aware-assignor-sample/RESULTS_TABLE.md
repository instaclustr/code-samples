# Rack-Aware Assignor Simulation Results

| Profile | Partitions Assigned | Local Assignments (Rack-Aware) | Local Assignments (Baseline) | Locality Hit Rate (Rack-Aware) | Locality Hit Rate (Baseline) | Improvement |
|---|---:|---:|---:|---:|---:|---:|
| `perfect-metadata` | 90 | 90 | 30 | 100.00% | 33.33% | +66.67 pp |
| `imperfect-metadata` | 90 | 72 | 18 | 80.00% | 20.00% | +60.00 pp |
