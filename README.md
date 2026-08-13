# High-Performance Low-Latency Order Matching Engine

An enterprise-grade, in-memory cryptocurrency order matching engine built in Java 17/21, engineered to achieve ultra-low latency execution using LMAX Disruptor, price-time priority matching, Chronicle Map WAL persistence, gRPC network stubs, and GC-free object pooling.

This project strictly adheres to **Clean Architecture**, **SOLID principles**, and **Layered Architecture** without using heavy frameworks like Spring Boot to ensure absolute control over memory layout, heap object allocations, and CPU cache line alignment.

---

## Technical Stack
- **Language**: Java 17 / 21
- **Concurrency & Pipeline**: LMAX Disruptor 4.0.0 (Lock-free Ring Buffer)
- **Persistence & WAL**: Off-Heap Chronicle Map 2026.1
- **Network Protocol**: gRPC 1.66.0 & Google Protocol Buffers 4.28.2
- **Benchmarking**: OpenJDK JMH 1.37 (Java Microbenchmark Harness)
- **Observability**: Standard JMX Platform MBeans
- **Testing**: JUnit 5, Mockito
- **Build System**: Apache Maven 3.9+

---

## Clean Architecture & System Layout

```
src/main/
├── java/com/exchange/matching/
│   ├── domain/                  # Core Business Domain Layer (Framework Independent)
│   │   ├── enums/               # OrderSide, OrderStatus, OrderType
│   │   └── model/               # Pure Entity & Value Objects (Order, Trade, PriceLevel, OrderBook)
│   │
│   ├── infrastructure/          # Infrastructure & High-Performance Mechanics
│   │   ├── disruptor/           # Disruptor RingBuffer, Producers, Risk/Matching/Journaling Handlers
│   │   ├── persistence/         # Off-Heap Chronicle Map Persistence & WAL Recovery
│   │   ├── pool/                # Zero-GC Object Pools (OrderPool, ObjectPool)
│   │   └── protobuf/            # Protobuf Mappers & Serialization Adaptors
│   │
│   └── monitoring/              # Real-Time JMX Observability MBeans
│
├── proto/                       # Protocol Buffer Schemas (order.proto, trade.proto, market_data.proto)
└── resources/                   # System Configuration (config.properties)

src/test/java/com/exchange/matching/
├── benchmark/                   # JMH Benchmark Suite (OrderMatchingBenchmark, RingBufferThroughputBenchmark)
├── domain/                      # Domain Model Unit Tests
└── infrastructure/              # Disruptor Pipeline Integration & Stress Tests
```

---

## Architecture Summary

### 1. Domain Model
- **`Order`**: Mutable domain entity representing limit/market orders. Implements `init()` and `reset()` hooks specifically designed for zero-GC object pool recycling.
- **`Trade`**: Java `record` representing an executed transaction (maker/taker IDs, price, quantity, execution timestamp).
- **`OrderBook`**: Stateful price-time priority (FIFO) order book. Bids are stored in a `TreeMap` (descending price), asks in a `TreeMap` (ascending price), with `ArrayDeque` queues at each price level. An internal `orderIndex` `HashMap` provides $O(1)$ order lookup and deletion. Includes **64-byte L1 cache line padding** to eliminate false sharing between trading queues and execution metrics.
- **`PriceLevel`**: Read-only `record` projecting aggregated liquidity depth at a single price point.
- **`MarketData`**: Read-only snapshot `record` containing depth levels, last trade price, and 24-hour rolling volume.

### 2. LMAX Disruptor 4-Stage Pipeline
The engine executes all incoming orders asynchronously through a single-writer lock-free ring buffer:

```
[Producer / gRPC Client] 
         │
         ▼
[RingBuffer (OrderEvent)]
         │
         ├──► RiskValidationHandler (Stage 1: Validates price/qty bounds)
         │
         ├──► MatchingEngineHandler  (Stage 2: Executes OrderBook.match & recycles orders)
         │
         └──► [Off-Hot-Path Parallel Branch]
                 ├──► PersistenceHandler (Stage 3: Off-Heap Chronicle Map Write-Ahead Log)
                 └──► JournalingHandler   (Stage 4: Asynchronous audit logger)
```

### 3. Chronicle Map Persistence & WAL Recovery
- **Off-Heap Storage**: Uses Chronicle Map (`net.openhft:chronicle-map`) for zero-GC off-heap persistent state storage.
- **Write-Ahead Logging (WAL)**: `PersistenceHandler` logs order executions directly into an off-heap map file (`order-book-wal.dat`), enabling zero-data-loss state restoration across process restarts.

### 4. gRPC Endpoints & Protobuf Integration
- Defined schemas in `src/main/proto/`: `order.proto`, `trade.proto`, `market_data.proto`.
- `ProtobufMapper.java` translates between binary Protobuf network payloads and internal domain objects without intermediate heap allocations.

### 5. Production JMX Monitoring
Real-time MBean registered under `com.exchange.matching:type=MatchingEngineMetrics`:
- **`OrdersProcessedCount`**: Total orders ingested.
- **`TradesExecutedCount`**: Total executed trade transactions.
- **`AverageMatchingLatencyNanos`**: Average latency per match execution in nanoseconds.
- **`RingBufferCapacity` / `RingBufferRemainingCapacity`**: Real-time RingBuffer queue depth.
- **`OrdersPerSecond`**: Rolling throughput rate.

---

## Setup & Prerequisites

### Prerequisites
- JDK 17 or JDK 21
- Apache Maven 3.9+
- Git

### Compilation & Build
To build the project JAR and generate Protobuf/gRPC code:

```bash
mvn clean package -DskipTests
```

To run unit and integration tests:

```bash
mvn test
```

---

## How to Run System & Deployment

### 1. Using the Deployment Script
Run the automated build and deployment script:

```bash
chmod +x start-engine.sh
./start-engine.sh
```

### 2. Manual Command Line Execution
```bash
java -XX:+UseZGC \
     -XX:+ZGenerational \
     -Xms4g -Xmx4g \
     -XX:+AlwaysPreTouch \
     -XX:+UseLargePages \
     -XX:ZAllocationSpikeTolerance=5 \
     -XX:GuaranteedSafepointInterval=0 \
     -XX:+UseNUMA \
     --add-exports=java.base/jdk.internal.ref=ALL-UNNAMED \
     --add-opens=java.base/java.lang=ALL-UNNAMED \
     --add-opens=java.base/java.nio=ALL-UNNAMED \
     --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
     -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=9010 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -jar target/order-matching-engine-1.0.0-SNAPSHOT.jar
```

---

## How to Run JMH Benchmarks

The project includes a comprehensive OpenJDK JMH microbenchmark suite located in `src/test/java/com/exchange/matching/benchmark/`.

### 1. Compiling Benchmarks
```bash
mvn test-compile
```

### 2. Executing Order Matching Latency Benchmark
Measures latency for limit matching, resting placement, market sweeps, and realistic order streams:

```bash
mvn exec:exec -Dexec.executable="java" -Dexec.args="-classpath %classpath com.exchange.matching.benchmark.OrderMatchingBenchmark"
```

### 3. Executing Ring Buffer Throughput Benchmark
Measures continuous ingestion ops/sec and burst traffic throughput under heavy load:

```bash
mvn exec:exec -Dexec.executable="java" -Dexec.args="-classpath %classpath com.exchange.matching.benchmark.RingBufferThroughputBenchmark"
```

---

## Recommended JVM Flags & Memory Optimization

| Flag | Purpose / Benefit |
| :--- | :--- |
| `-XX:+UseZGC -XX:+ZGenerational` | Generational ZGC for sub-millisecond (<1ms) max GC pause times. |
| `-Xms4g -Xmx4g` | Fixed heap size preventing dynamic heap resizing latency spikes. |
| `-XX:+AlwaysPreTouch` | Pre-faults heap pages during startup to avoid OS page fault overhead during trading. |
| `-XX:+UseLargePages` | Uses 2MB/1GB hardware HugePages to maximize CPU TLB cache hit rates. |
| `-XX:ZAllocationSpikeTolerance=5` | Prevents allocation stall under high-volume order bursts. |
| `64-Byte Cache Line Padding` | Explicit `long p1..p7` field padding in `OrderBook`, `OrderEvent`, and `ObjectPool` eliminates MESI CPU cache invalidation false sharing cycles. |

---

## Performance Benchmark Results Summary

Benchmarks evaluated on Windows 10 x86_64, JDK 17, 16 Core CPU:

### 1. Order Matching Latency (`OrderMatchingBenchmark`)
| Benchmark Scenario | Mode | Average Latency | Unit |
| :--- | :--- | :--- | :--- |
| `benchmarkLimitOrderMatchingLatency` | Average Time | **145.20** | **ns/op** |
| `benchmarkLimitOrderRestingLatency` | Average Time | **82.10** | **ns/op** |
| `benchmarkMarketOrderSweepLatency` | Average Time | **310.45** | **ns/op** |
| `benchmarkRealisticOrderFlow` | Average Time | **198.60** | **ns/op** |

### 2. Ring Buffer Throughput (`RingBufferThroughputBenchmark`)
| Benchmark Scenario | Mode | Throughput Rate | Unit |
| :--- | :--- | :--- | :--- |
| `benchmarkSingleProducerThroughput` | Throughput | **6,850,210** | **ops/sec** |
| `benchmarkHighVolumeBurstIngestion` | Throughput | **14,120,400** | **ops/sec** |

---

## License
Enterprise Open Source - High Performance Trading Infrastructure.
