# High-Performance Order Matching Engine

An enterprise-grade, in-memory cryptocurrency order matching engine built in Java 21, designed to achieve ultra-low latency execution using LMAX Disruptor, price-time priority, and custom GC-free object pooling.

This project is built following strict **Clean Architecture**, **SOLID principles**, and **Layered Architecture** design guidelines without using heavy frameworks like Spring Boot to ensure absolute control over object allocations and CPU cache lines.

## Technical Stack
- **Language**: Java 21
- **Concurrency**: LMAX Disruptor 4.0.0
- **Serialization**: Google Protocol Buffers 3.25.1
- **Testing**: JUnit 5, Mockito
- **Build System**: Maven

---

## Clean Architecture & Design System

The system is organized into decoupled layers:

```
src/main/java/com/exchange/matching/
│
├── domain/                  # Core Business Domain (Independent of external libraries/frameworks)
│   ├── model/               # Pure Entity and Value Objects (Order, Trade, PriceLevel, etc.)
│   ├── exception/           # Domain-Specific Exceptions
│   └── repository/          # Core abstractions/interfaces for state management
│
├── application/             # Application Use Cases & Orchestrators
│   ├── engine/              # Core OrderBook and Matching Engine Orchestration
│   └── handler/             # Custom LMAX Disruptor Consumers (Risk, Matching, Journaling)
│
└── infrastructure/          # External Integrations & Low-Level Components
    ├── disruptor/           # Disruptor Setup (Ring Buffer, Event Producers)
    ├── pool/                # Garbage Collection-free Object Pools
    └── protobuf/            # Protobuf Schemas & Serialization Adaptors
```

---

## Features (Week 1 & Week 2 Roadmap)

### **Week 1: Core Domain & Data Structures**
- **GC-Free Custom Memory Management**: Pre-allocated `ObjectPool` for `Order` and `Trade` events to eliminate GC runtime overhead.
- **Price-Time Priority (FIFO)**: High-performance limit order book utilizing dual data structures for $O(1)$ and $O(\log N)$ operations.
- **Order Modification & Cancellation**: Real-time order adjustment and cancellation with strict state checking.
- **Partial/Full Order Matching**: Seamless matching mechanics generating comprehensive trade records.
- **Protobuf Schemas**: Protocol Buffers for fast, compact, and structured network payload handling.

### **Week 2: Concurrency & Async Processing**
- **LMAX Disruptor Ring Buffer**: Single-writer ring buffer architecture for high-throughput, thread-safe message passing.
- **Pipeline Processing**: 
  - `Risk Validation Handler` -> Inspects and validates account balances/limits.
  - `Matching Handler` -> Processes matching logic on the isolated single-threaded OrderBook.
  - `Journal Handler` -> Persists raw events asynchronously for recovery and replication.
- **Virtual Thread Integration**: Lightweight concurrent worker execution to keep OS overhead to a minimum.
- **Multithreaded Stress Testing**: Comprehensive validation tests targeting race conditions, data races, and performance throughput.

---

## Build and Compilation

Verify the environment compilation and run tests:

```bash
mvn clean compile
```

To run unit tests (once implemented):

```bash
mvn test
```
