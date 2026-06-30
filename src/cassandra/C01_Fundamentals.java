package cassandra;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║   C01 — CASSANDRA FUNDAMENTALS                                   ║
 * ║   What it is, why it exists, how it thinks differently           ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * ─── WHAT IS CASSANDRA? ────────────────────────────────────────────
 *
 * Apache Cassandra is a distributed NoSQL database designed for:
 *   - Massive write throughput  (millions of writes/sec)
 *   - Linear scalability        (add nodes → more capacity)
 *   - No single point of failure (every node is equal — no master)
 *   - Global distribution       (multi-datacenter replication built-in)
 *
 * Created at Facebook (2008) to handle the Inbox search problem.
 * Now used by Netflix, Apple (75,000+ nodes), Instagram, Discord, Uber.
 *
 * ─── CAP THEOREM POSITION ──────────────────────────────────────────
 *
 * CAP: a distributed system can guarantee only 2 of:
 *   C = Consistency   (every read sees the latest write)
 *   A = Availability  (every request gets a response, even if stale)
 *   P = Partition tolerance (system works even if nodes can't talk to each other)
 *
 * Cassandra is AP:
 *   → Always Available, always Partition tolerant
 *   → Tunable Consistency (you choose the trade-off per query)
 *   → Eventual consistency by default
 *
 * MySQL/PostgreSQL is CP (Consistency + Partition tolerance).
 *
 * ─── WHEN TO USE CASSANDRA ─────────────────────────────────────────
 *
 * GREAT FIT:
 *   ✅ Time-series data          (IoT sensor readings, logs, metrics)
 *   ✅ User activity feeds       (Instagram timeline, Twitter feed)
 *   ✅ Messaging / chat          (Discord messages — 100M+ per day)
 *   ✅ Product catalogs          (Amazon item data at scale)
 *   ✅ Recommendation history    (Netflix watch history)
 *   ✅ Write-heavy workloads     (high ingest rate)
 *   ✅ Geo-distributed apps      (data in multiple regions)
 *
 * BAD FIT:
 *   ❌ Complex JOIN queries      (no joins in Cassandra)
 *   ❌ Ad-hoc queries            (must know queries at design time)
 *   ❌ Strong ACID transactions  (no multi-row ACID by default)
 *   ❌ Small datasets            (operational overhead not worth it)
 *   ❌ Aggregations on all rows  (no "SELECT COUNT(*) WHERE ..." without full scan)
 *
 * ─── ARCHITECTURE OVERVIEW ─────────────────────────────────────────
 *
 *  Node 1 ──── Node 2 ──── Node 3
 *    │              │            │
 *    └──────────────┴────────────┘
 *           Ring (peer-to-peer)
 *
 * Key concepts:
 *
 *  1. RING topology
 *     All nodes are peers. No master. Data is distributed around a ring.
 *     Each node owns a "token range" (a slice of the hash space).
 *
 *  2. PARTITIONER
 *     Hashes the partition key → determines which node stores the row.
 *     Murmur3Partitioner (default): distributes data evenly.
 *
 *  3. REPLICATION
 *     Each piece of data is stored on N nodes (Replication Factor = N).
 *     RF=3: data lives on 3 nodes. You can lose 2 nodes and still read data.
 *
 *  4. SNITCH
 *     Tells Cassandra about network topology (which rack, which datacenter).
 *     Used for smart replica placement (don't put replicas on same rack).
 *
 *  5. GOSSIP PROTOCOL
 *     Nodes share health information with each other every second.
 *     How nodes discover each other and detect failures.
 *
 * ─── WRITE PATH (what happens when you INSERT) ─────────────────────
 *
 *  Client → Coordinator Node
 *               │
 *               ├─ 1. Write to COMMIT LOG (sequential disk write — fast)
 *               │
 *               ├─ 2. Write to MEMTABLE (in-memory sorted structure)
 *               │
 *               └─ 3. When MemTable is full → flush to SSTable (disk)
 *
 *  SSTables are immutable. Compaction merges them periodically.
 *  This is why Cassandra writes are SO fast — sequential I/O, no in-place updates.
 *
 *  UPDATE and DELETE are also writes (tombstones for deletes, upserts for updates).
 *
 * ─── READ PATH (what happens when you SELECT) ──────────────────────
 *
 *  Client → Coordinator Node
 *               │
 *               ├─ Check MemTable (in-memory — fastest)
 *               ├─ Check Row Cache (if enabled)
 *               ├─ Check Bloom Filter (probabilistic — "does this SSTable have this key?")
 *               ├─ Check Key Cache (SSTable offset cache)
 *               └─ Read from SSTable (disk — slowest)
 *
 *  Then merges results from multiple nodes based on consistency level.
 *  Reads are slower than writes in Cassandra (opposite of most DBs).
 *
 * ─── CONSISTENCY LEVELS ────────────────────────────────────────────
 *
 *  For RF=3:
 *
 *  Consistency Level  │ Write must succeed on │ Read must succeed from
 *  ───────────────────┼───────────────────────┼──────────────────────
 *  ONE                │ 1 node                │ 1 node (may be stale)
 *  QUORUM             │ 2 nodes (majority)    │ 2 nodes (strong)
 *  ALL                │ 3 nodes               │ 3 nodes (strongest)
 *  LOCAL_QUORUM       │ majority in local DC  │ majority in local DC
 *
 *  QUORUM on both read + write = strong consistency (you always see latest write).
 *  ONE on write + ONE on read = eventual consistency (fast, may see stale data).
 *
 * ─── KEY TERMINOLOGY ───────────────────────────────────────────────
 *
 *  KEYSPACE   = database (like a schema in PostgreSQL)
 *  TABLE      = table (but modeled differently — no joins, denormalized)
 *  PARTITION  = all rows sharing the same partition key (stored together on one node)
 *  ROW        = a single record within a partition
 *  COLUMN     = a field in a row
 *
 *  PRIMARY KEY = (partition_key, clustering_col1, clustering_col2)
 *                 └── determines node  └── determines order within partition
 *
 *  PARTITION KEY: which node stores this data
 *  CLUSTERING KEY: how rows are sorted within a partition (like ORDER BY baked in)
 *
 * ─── THE GOLDEN RULE ───────────────────────────────────────────────
 *
 *  In RDBMS:   Model your data, then figure out queries.
 *  In Cassandra: Know your queries FIRST, then model your data around them.
 *
 *  Every table is essentially a pre-computed, materialized answer to one query.
 *  If you have 3 different query patterns, you might need 3 tables.
 *
 * ─── CASSANDRA vs RDBMS COMPARISON ────────────────────────────────
 *
 *  Feature              │ PostgreSQL       │ Cassandra
 *  ─────────────────────┼──────────────────┼──────────────────────────
 *  Data model           │ Normalized       │ Denormalized (query-first)
 *  Joins                │ Yes              │ No (JOIN at app layer)
 *  Transactions         │ Full ACID        │ Lightweight (single partition)
 *  Schema               │ Rigid            │ Schema exists but flexible
 *  Scaling              │ Vertical         │ Horizontal (add nodes)
 *  Write speed          │ Moderate         │ Extremely fast
 *  Read speed           │ Fast with indexes│ Fast for known access patterns
 *  Ad-hoc queries       │ Yes              │ Painful (ALLOW FILTERING)
 *  Aggregations         │ Yes              │ Limited
 *  Failure tolerance    │ Requires replica │ Built-in (RF > 1)
 *  Consistency          │ Strong           │ Tunable (eventual by default)
 */
public class C01_Fundamentals {
    // This file is a learning reference — see C02 onward for CQL and code examples.
    public static void main(String[] args) {
        System.out.println("Read the class-level Javadoc above — it IS the lesson.");
        System.out.println("Next: C02_DataModeling.java");
    }
}
