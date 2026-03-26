# rack-aware-assignor-sample

Example Java project showing a KIP-1101 style rack-aware assignment strategy:

- Prefer assigning partitions to consumers in the same rack as the partition leader
- Keep assignment balanced with a least-loaded policy
- Measure locality hit rate against a simple baseline

## Project structure

- `RackAwareAssignor` - locality-first assignment algorithm
- `SimulationHarness` - runnable simulation
- `RackAwareAssignorSimulationTest` - JUnit test that prints locality hit rate

## Run simulation

```bash
cd /Users/pbrebner/rack-aware-assignor-sample
mvn -q compile exec:java
```

## Run tests

```bash
cd /Users/pbrebner/rack-aware-assignor-sample
mvn -q test
```

## Simulation profiles

- `perfect-metadata` (often near 100% locality in this setup)
- `imperfect-metadata` (lower but still better than baseline due to missing rack metadata)

The imperfect profile intentionally removes rack metadata from:

- some partition leaders (`leaderRack = null`)
- some consumer members (`memberRack = null`)

This models realistic cases where rack information is incomplete.

## Latest measured results

| Profile | Partitions Assigned | Local Assignments (Rack-Aware) | Local Assignments (Baseline) | Locality Hit Rate (Rack-Aware) | Locality Hit Rate (Baseline) | Improvement |
|---|---:|---:|---:|---:|---:|---:|
| `perfect-metadata` | 90 | 90 | 30 | 100.00% | 33.33% | +66.67 pp |
| `imperfect-metadata` | 90 | 72 | 18 | 80.00% | 20.00% | +60.00 pp |

The same table is also stored in `RESULTS_TABLE.md`.

## Interpreting the numbers

### Why baseline is about 33.33% (perfect profile)

The perfect profile uses 3 racks (`rack-a`, `rack-b`, `rack-c`) with even distribution:

- consumers are spread evenly across 3 racks
- partition leaders are spread evenly across 3 racks
- baseline assignment is not rack-aware

So each baseline assignment has roughly a 1-in-3 chance of landing on the same rack as the partition leader:

- expected local baseline assignments: `90 * (1/3) = 30`
- baseline locality hit rate: `30 / 90 = 33.33%`

### Why rack-aware reaches 100% (perfect profile)

In this synthetic setup, metadata is complete and local candidates always exist:

- every partition has leader rack metadata
- every consumer has rack metadata
- each topic has consumers in all racks
- balancing never forces cross-rack fallback

The assignor always picks a same-rack consumer first, so all 90 assignments are local (`90/90 = 100%`).

In real clusters this is usually lower due to missing rack metadata, uneven consumer placement, topic-specific subscriptions, and rebalance constraints.

## Notes

This project focuses on the assignment logic and simulation metrics. It does not plug directly into the Kafka `ConsumerPartitionAssignor` interface so it remains stable across client API changes. You can adapt the core logic into a custom assignor implementation in your target `kafka-clients` version.
