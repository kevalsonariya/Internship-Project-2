# High-Performance Order Matching Engine

A low-latency financial trading core designed to match buy and sell orders for a cryptocurrency or stock exchange. This system operates entirely in-memory during trading sessions, bypassing traditional database bottlenecks, and uses advanced concurrency models and ring-buffer data structures to target sub-millisecond execution times.

> **Note:** This is an internship / academic project built as a scoped-down proof-of-concept over 4 days, based on a full production-grade architecture. It demonstrates the core patterns (ring-buffer concurrency, off-heap persistence, gRPC streaming) rather than being a production-ready trading system.

---

## ✨ Features

- Strict price-time priority order matching
- Asynchronous, high-throughput order processing via the LMAX Disruptor pattern
- Off-heap key-value persistence for order book state (no GC pressure from persistence)
- gRPC + Protocol Buffers API for order submission and market data streaming
- JMH-benchmarked critical paths and JVM tuning for low-latency GC

---

## 🏗️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Concurrency | LMAX Disruptor |
| Persistence | Chronicle Map (off-heap) |
| Networking | gRPC + Protocol Buffers |
| Testing | JUnit 5 |
| Benchmarking | JMH (Java Microbenchmark Harness) |

---

## 📐 Architecture Overview

```
 gRPC Client
     │
     ▼
 gRPC Server  ──► publishes order events
     │
     ▼
 LMAX Disruptor Ring Buffer
     │
     ├──► Risk Validation Handler
     ├──► Matching Engine Handler ──► Order Book (in-memory)
     └──► Journaling / Persistence Handler ──► Chronicle Map (disk)
                                                    │
                                                    ▼
                                          gRPC streaming updates
                                             back to clients
```

Orders arrive over gRPC, get published onto the Disruptor ring buffer, and flow through a chain of handlers: validation → matching → persistence. Persistence is asynchronous so it never blocks the matching thread.

---

## 📁 Project Structure

```
order-matching-engine/
├── src/
│   ├── main/
│   │   ├── java/          # Domain, Disruptor handlers, gRPC server, persistence
│   │   └── proto/         # Protobuf schemas (Order, Trade, MarketData)
│   └── test/
│       └── java/          # JUnit 5 tests + JMH benchmarks
├── pom.xml
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites
- **JDK 21** — [Adoptium Temurin](https://adoptium.net/temurin/releases/?version=21)
- **Maven 3.9+**
- **Protocol Buffers compiler (`protoc`)** 3.21+

```bash
java -version     # should show 21.x
mvn -version
protoc --version
```

### Clone & Build
```bash
git clone <this-repo-url>
cd order-matching-engine
mvn clean install
```

### Run Tests
```bash
mvn test
```

### Run Benchmarks
```bash
mvn clean package
java -jar target/benchmarks.jar
```

### Run the Server
```bash
mvn exec:java -Dexec.mainClass="com.example.matchingengine.Server"
```

---

## ⚙️ JVM Tuning

For low-latency GC, run with one of the following flag sets:

```bash
# ZGC (recommended for sub-millisecond latency targets)
java -XX:+UseZGC -jar target/order-matching-engine.jar

# Shenandoah
java -XX:+UseShenandoahGC -jar target/order-matching-engine.jar
```

---

## 📊 Benchmark Results

_(Fill in after running JMH benchmarks)_

| Benchmark | Throughput | Avg Latency | p99 Latency |
|---|---|---|---|
| Order matching | — | — | — |
| Ring buffer publish | — | — | — |

---

## 🗺️ Roadmap / Known Limitations

- [ ] Persistence recovery on restart is not fully crash-tested
- [ ] Risk validation checks are minimal (basic price/quantity sanity checks only)
- [ ] No authentication/authorization on the gRPC endpoints
- [ ] Single-node only — no clustering/failover

---

## 👥 Team

| Name | Contribution |
|---|---|
| Keval | Domain model & Protobuf schemas |
| Darshan | LMAX Disruptor integration & concurrency handlers |
| Dhrumil | Chronicle Map persistence & gRPC networking |

---

## 📄 License

This project was built for educational/internship purposes.
