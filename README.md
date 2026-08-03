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

To run unit tests:

```bash
mvn test
```

---

## Day 1 Architecture & Interfaces

### Project Architecture Overview
The Day 1 codebase establishes the core business domain entities, object pools, and matching engine interface contracts. It represents the innermost layer of Clean Architecture:
1. **Domain Model**: Fully decoupled from external frameworks and libraries.
   - [Order](file:///c:/Users/Raval%20Darshan/OneDrive/Desktop/internship-project-2/src/main/java/com/exchange/matching/domain/model/Order.java): Encapsulates order state (price, quantity, side, type, status, and lifecycle transitions). Designed for zero-GC pooling.
   - [OrderBook](file:///c:/Users/Raval%20Darshan/OneDrive/Desktop/internship-project-2/src/main/java/com/exchange/matching/domain/model/OrderBook.java): Stateful matching engine implementation maintaining two directional queues (TreeMap of ArrayDeques) and a hash index map for fast $O(1)$ lookup and cancellations.
   - [Trade](file:///c:/Users/Raval%20Darshan/OneDrive/Desktop/internship-project-2/src/main/java/com/exchange/matching/domain/model/Trade.java): A record capturing transaction details generated from matching.
   - [PriceLevel](file:///c:/Users/Raval%20Darshan/OneDrive/Desktop/internship-project-2/src/main/java/com/exchange/matching/domain/model/PriceLevel.java) / [MarketData](file:///c:/Users/Raval%20Darshan/OneDrive/Desktop/internship-project-2/src/main/java/com/exchange/matching/domain/model/MarketData.java): Lightweight structures for data projection and market feed generation.
2. **Infrastructure (Object Pools)**:
   - [ObjectPool](file:///c:/Users/Raval%20Darshan/OneDrive/Desktop/internship-project-2/src/main/java/com/exchange/matching/infrastructure/pool/ObjectPool.java) & [OrderPool](file:///c:/Users/Raval%20Darshan/OneDrive/Desktop/internship-project-2/src/main/java/com/exchange/matching/infrastructure/pool/OrderPool.java): Avoids garbage collection pressure during peak volume by reusing order objects.

### Public Interfaces
- **[IOrderBook](file:///c:/Users/Raval%20Darshan/OneDrive/Desktop/internship-project-2/src/main/java/com/exchange/matching/domain/model/IOrderBook.java)**: The primary interface defining order matching (`match(Order)`), cancellation (`cancelOrder(String)`), lookup (`getOrder(String)`), depth snapshots (`getDepth(int)`), and statistics retrieval.
- **[Order](file:///c:/Users/Raval%20Darshan/OneDrive/Desktop/internship-project-2/src/main/java/com/exchange/matching/domain/model/Order.java)**: Represents order parameters and tracks executions (supports both limit and market orders).
- **[Trade](file:///c:/Users/Raval%20Darshan/OneDrive/Desktop/internship-project-2/src/main/java/com/exchange/matching/domain/model/Trade.java)**: Read-only record capturing matched quantities, execution prices, maker/taker identifiers.

---

## Day 2 Continuation Guide

For developers continuing on Day 2:
1. **LMAX Disruptor Pipeline Integration**: Wrap the single-threaded `OrderBook` execution inside the LMAX Disruptor thread-safe ring buffer handler to enable ultra-low-latency asynchronous execution.
2. **Journaling & Durability**: Setup the journaling handler to serialize incoming commands to disk.
3. **Multi-threaded Architecture Model**: The `OrderBook` state is purposely not thread-safe. Thread safety and lock-free execution are achieved by isolating write access to a single dedicated consumer thread. Maintain this model when writing concurrency handlers.

