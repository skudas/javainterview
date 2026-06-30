package cassandra;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║   C07 — INTERVIEW QUICK REFERENCE                                ║
 * ║   Everything you need in one place, 5 minutes before interview   ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * ─── THE 5 THINGS THAT MATTER MOST ─────────────────────────────────
 *
 *  1. QUERY-FIRST design — know your queries before you create tables
 *  2. PARTITION KEY — chooses the node; must be evenly distributed
 *  3. CLUSTERING KEY — sorts rows within a partition; enables range queries
 *  4. DENORMALIZE — one table per query, no joins, copy data liberally
 *  5. BUCKET wide rows — time-series data must be bucketed by time period
 *
 * ─── PRIMARY KEY CHEAT SHEET ────────────────────────────────────────
 *
 *  PRIMARY KEY (user_id)
 *  → simple key, one row per user_id
 *
 *  PRIMARY KEY (user_id, created_at)
 *  → partition=user_id, clustering=created_at (sorted, range queryable)
 *
 *  PRIMARY KEY ((user_id, date), created_at)
 *  → composite partition: user_id+date together choose the node
 *
 *  WITH CLUSTERING ORDER BY (created_at DESC)
 *  → stored newest-first (no ORDER BY needed in query)
 *
 * ─── WHAT YOU CAN AND CANNOT DO IN WHERE ────────────────────────────
 *
 *  Table: PRIMARY KEY ((user_id), order_time, order_id)
 *
 *  ✅ WHERE user_id = ?                          -- partition key = OK
 *  ✅ WHERE user_id = ? AND order_time = ?       -- PK + first clustering = OK
 *  ✅ WHERE user_id = ? AND order_time >= ?      -- PK + range on clustering = OK
 *  ✅ WHERE user_id = ? AND order_time = ? AND order_id = ?   -- full PK = OK
 *
 *  ❌ WHERE order_time = ?                       -- missing partition key
 *  ❌ WHERE user_id = ? AND order_id = ?         -- skipped a clustering column
 *  ❌ WHERE user_id = ? AND order_id >= ?        -- can't range-filter on 2nd clustering
 *                                                    if 1st clustering not fully specified
 *
 * ─── CONSISTENCY LEVEL DECISION TREE ───────────────────────────────
 *
 *  Need strong consistency?
 *    Yes → QUORUM (or LOCAL_QUORUM for single-DC)
 *    No  → ONE (fastest, eventual)
 *
 *  Multi-datacenter?
 *    Yes → LOCAL_QUORUM (strong within each DC, async cross-DC)
 *    No  → QUORUM
 *
 *  Need ultra-low latency + OK with stale reads?
 *    → ONE on both read and write
 *
 *  Auth / financial / inventory?
 *    → Always LOCAL_QUORUM minimum
 *
 * ─── TOOL SELECTION GUIDE ───────────────────────────────────────────
 *
 *  Scenario                          → Cassandra feature to use
 *  ──────────────────────────────────┼───────────────────────────────
 *  "Show me records since yesterday" → CLUSTERING ORDER BY + range query
 *  "Auto-delete sessions after 1h"   → TTL (per-row or table default)
 *  "Register unique email"           → IF NOT EXISTS (LWT)
 *  "Check stock before reserving"    → IF quantity > 0 (LWT)
 *  "Update score on leaderboard"     → DELETE old + INSERT new (clustering can't be updated)
 *  "High-write IoT sensor data"      → Time-bucketed partition + TWCS compaction
 *  "User inbox sorted by latest"     → CLUSTERING ORDER BY (message_time DESC)
 *  "Track page view count"           → COUNTER column
 *  "Store user roles / tags"         → SET<TEXT> collection
 *  "Store product attributes"        → MAP<TEXT, TEXT> collection
 *  "Multiple queries on same entity" → Create one table per query pattern
 *
 * ─── COLLECTION QUICK REFERENCE ─────────────────────────────────────
 *
 *  LIST<TEXT>           → ordered, duplicates, indexed access
 *  SET<TEXT>            → unordered, no duplicates → use for tags, roles
 *  MAP<TEXT, TEXT>      → key-value → use for attributes, metadata
 *  FROZEN<LIST<...>>    → immutable, treated as a blob → use in nested types
 *
 *  Add to SET:    UPDATE t SET col = col + {'new-value'} WHERE ...;
 *  Remove from SET: UPDATE t SET col = col - {'old-value'} WHERE ...;
 *  Add to MAP:    UPDATE t SET col['key'] = 'value' WHERE ...;
 *  Remove MAP key: DELETE col['key'] FROM t WHERE ...;
 *  Append to LIST: UPDATE t SET col = col + ['item'] WHERE ...;
 *
 * ─── ANTI-PATTERNS TO MENTION IN INTERVIEWS ─────────────────────────
 *
 *  ALLOW FILTERING   → full cluster scan → never in production
 *  Hot partition      → e.g., country=US gets all traffic
 *  Unbounded rows     → time-series without buckets grows forever
 *  Too many tombstones → lots of deletes → add TTL or redesign
 *  Batch for performance → batches are for atomicity, not speed
 *  Secondary index on high-cardinality column → fan-out to all nodes
 *  SELECT * without LIMIT → can pull millions of rows into memory
 *
 * ─── WRITE PATH (say this in system design rounds) ──────────────────
 *
 *  Write → CommitLog (sequential disk, durability)
 *        → MemTable (in-memory, fast)
 *        → SSTable (when MemTable flushes, immutable)
 *        → Compaction (merges SSTables, removes tombstones, periodically)
 *
 *  Why writes are fast: sequential I/O + no in-place updates + no index locks
 *
 * ─── READ PATH ───────────────────────────────────────────────────────
 *
 *  Read → MemTable (check first, fastest)
 *       → Bloom Filter (probabilistic: "does this SSTable have this key?")
 *       → Key Cache (SSTable offset cache)
 *       → SSTable (disk, slowest)
 *       → Merge from multiple replica nodes (based on consistency level)
 *       → Read Repair (if replicas disagree, repair in background)
 *
 *  Why reads can be slower: multiple SSTables to check + network round-trips
 *
 * ─── COMPARISON TABLE (have this ready) ─────────────────────────────
 *
 *  Property          Cassandra      DynamoDB       MongoDB        Redis
 *  ──────────────────┼──────────────┼──────────────┼──────────────┼────────
 *  Model             Wide-column    Wide-column    Document       Key-Value
 *  CAP               AP             AP             CP (w/ config) AP
 *  Joins             No             No             No             No
 *  Transactions      Single-part.   Single-item    Multi-doc      No
 *  Scaling           Horizontal     Auto (AWS)     Horizontal     Horizontal
 *  Write speed       Excellent      Good           Good           Excellent
 *  Query flexibility Low            Low            High           Very Low
 *  Best for          Time-series    Serverless AWS Rich documents Caching
 *
 * ─── QUICK CQL PATTERNS TO KNOW BY HEART ────────────────────────────
 *
 *  -- Create keyspace
 *  CREATE KEYSPACE ks WITH replication = {'class':'SimpleStrategy','replication_factor':3};
 *
 *  -- Create time-series table (most common pattern)
 *  CREATE TABLE events (
 *    entity_id TEXT, date DATE, event_time TIMESTAMP,
 *    data TEXT,
 *    PRIMARY KEY ((entity_id, date), event_time)
 *  ) WITH CLUSTERING ORDER BY (event_time DESC);
 *
 *  -- Insert with TTL
 *  INSERT INTO sessions (id, user_id) VALUES (uuid(), uuid()) USING TTL 3600;
 *
 *  -- Conditional insert (unique constraint)
 *  INSERT INTO unique_emails (email, user_id) VALUES (?, ?) IF NOT EXISTS;
 *
 *  -- Conditional update (optimistic lock)
 *  UPDATE accounts SET balance = ? WHERE id = ? IF balance = ?;
 *
 *  -- Counter increment
 *  UPDATE page_views SET views = views + 1 WHERE page_id = ?;
 *
 *  -- Logged batch (atomicity across denormalized tables)
 *  BEGIN BATCH
 *    INSERT INTO table_1 ...;
 *    INSERT INTO table_2 ...;
 *  APPLY BATCH;
 */
public class C07_InterviewQuickRef {
    public static void main(String[] args) {
        System.out.println("Quick reference — read this before every Cassandra interview.");
    }
}
