# 🚀 Distributed Observability & Troubleshooting Platform

A high-throughput distributed backend system designed for real-time event ingestion, aggregation, and troubleshooting.

## 📌 Overview

This platform processes large-scale backend events and provides real-time insights for debugging and monitoring distributed systems.

- Handles **100K+ events/day**
- Built with **scalable microservices architecture**
- Optimized for **low latency and high throughput**

---

## 🏗️ Architecture
Producers → Kafka → Ingestion Service → MongoDB
↓
Redis (Caching / Rate Limiting)
↓
Query / Aggregation APIs



### Components:
- **Kafka** → Event streaming & ingestion
- **Ingestion Service** → Parallel consumers for processing
- **MongoDB** → Persistent storage with optimized indexing
- **Redis** → Caching & fast lookups
- **Logging SDK** → Reusable client for event publishing

---

## ⚙️ Tech Stack

- **Backend:** Java, Spring Boot
- **Streaming:** Apache Kafka
- **Database:** MongoDB
- **Caching:** Redis
- **DevOps:** Docker

---

## 🔥 Features

- ⚡ Real-time event ingestion using Kafka
- 📊 Efficient time-range queries with indexed MongoDB collections
- 🚀 Parallel processing using multi-threaded consumers
- 🧠 Smart caching with Redis to reduce DB load
- 🔌 Plug-and-play **logging SDK** for easy integration
- 🐳 Fully containerized using Docker

---

## 📈 Performance Optimizations

- Batch writes to MongoDB
- Indexed queries for time-series data
- Concurrent Kafka consumers
- Redis caching for hot data

---

## 🧪 How to Run

### Prerequisites
- Java 17+
- Docker
- Kafka & Zookeeper
- MongoDB
- Redis

### Steps

```bash
# Clone repo
git clone <repo-url>

# Start dependencies (Docker)
docker-compose up -d

# Run application
./mvnw spring-boot:run
