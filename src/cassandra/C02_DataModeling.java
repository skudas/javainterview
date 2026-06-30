package cassandra;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║   C02 — DATA MODELING (The Most Critical Cassandra Skill)        ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * Data modeling in Cassandra is the single skill that separates someone
 * who "knows Cassandra" from someone who can build production systems with it.
 * Get this wrong → your queries will time out or scan the entire cluster.
 *
 * ─── THE 3-STEP CASSANDRA MODELING PROCESS ─────────────────────────
 *
 *  Step 1: Define your entities (like RDBMS)
 *  Step 2: Define ALL your query patterns (unlike RDBMS — do this FIRST)
 *  Step 3: Design one table per query pattern
 *
 * ─── PART 1: PRIMARY KEY ANATOMY ───────────────────────────────────
 *
 *  PRIMARY KEY ((partition_key), clustering_col1, clustering_col2)
 *               └──────┬──────   └─────────────────────────────┘
 *               decides the node        decides sort order
 *
 *  Simple primary key:   PRIMARY KEY (user_id)
 *    → partition key = user_id, no clustering columns
 *    → one row per user_id (no ordering within partition)
 *
 *  Composite partition key:  PRIMARY KEY ((user_id, date), event_time)
 *    → partition key = (user_id, date) combined
 *    → rows within a partition are sorted by event_time
 *    → forces data spread across nodes (avoids hot partitions)
 *
 * ─── PART 2: THE HOT PARTITION PROBLEM ─────────────────────────────
 *
 *  Every row with the same partition key goes to the SAME node.
 *
 *  BAD: PRIMARY KEY (country_code, user_id)
 *    → All US users hit the same node → HOT PARTITION
 *    → One node gets 300M rows while others get 10K
 *
 *  FIX 1: Add a bucket to spread load
 *    PRIMARY KEY ((user_id, bucket), event_time)
 *    bucket = user_id % 10  → spreads across 10× more nodes
 *
 *  FIX 2: Use time-based bucketing for time-series
 *    PRIMARY KEY ((sensor_id, month), reading_time)
 *    → Jan-2024 readings go to one partition, Feb-2024 to another
 *    → Limits partition size to one month of data
 *
 * ─── PART 3: PARTITION SIZE LIMITS ────────────────────────────────
 *
 *  Hard limits:
 *    Max 2 billion rows per partition
 *    Max 100MB per partition (practical recommendation: stay under 10MB)
 *
 *  A partition that grows without bound = "wide row problem"
 *  → Always ask: "Can a partition grow unbounded? If yes, add a time bucket."
 *
 * ─── PART 4: REAL-WORLD MODELING EXAMPLES ──────────────────────────
 *
 * ════════════════════════════════════════════════════════
 * EXAMPLE A: E-Commerce Order History
 * ════════════════════════════════════════════════════════
 *
 * Queries needed:
 *   Q1: Get all orders for a user (sorted by newest first)
 *   Q2: Get a specific order by ID
 *   Q3: Get all orders for a user in the last 30 days
 *
 * WRONG (thinking like RDBMS — one table for everything):
 *
 *   CREATE TABLE orders (
 *     order_id  UUID PRIMARY KEY,  -- only works for Q2, breaks Q1 and Q3
 *     user_id   UUID,
 *     ...
 *   );
 *   -- Q1 would need: SELECT * FROM orders WHERE user_id = ? → FULL TABLE SCAN
 *
 * RIGHT (one table per query):
 *
 *   -- Table for Q1 and Q3: "orders by user, sorted by time"
 *   CREATE TABLE orders_by_user (
 *     user_id     UUID,
 *     order_time  TIMESTAMP,
 *     order_id    UUID,
 *     status      TEXT,
 *     total       DECIMAL,
 *     PRIMARY KEY (user_id, order_time, order_id)
 *   ) WITH CLUSTERING ORDER BY (order_time DESC);
 *   -- Query: SELECT * FROM orders_by_user WHERE user_id = ? LIMIT 20;
 *   -- Query: SELECT * FROM orders_by_user WHERE user_id = ? AND order_time >= '2024-01-01';
 *
 *   -- Table for Q2: "lookup a specific order"
 *   CREATE TABLE orders_by_id (
 *     order_id    UUID PRIMARY KEY,
 *     user_id     UUID,
 *     order_time  TIMESTAMP,
 *     status      TEXT,
 *     total       DECIMAL
 *   );
 *   -- Query: SELECT * FROM orders_by_id WHERE order_id = ?;
 *
 * ════════════════════════════════════════════════════════
 * EXAMPLE B: Social Media Feed (Instagram/Twitter style)
 * ════════════════════════════════════════════════════════
 *
 * Queries needed:
 *   Q1: Get the 20 most recent posts by a user
 *   Q2: Get all posts by users I follow (home timeline)
 *
 * Solution:
 *
 *   -- Table for Q1: user's own posts
 *   CREATE TABLE posts_by_user (
 *     user_id     UUID,
 *     post_time   TIMESTAMP,
 *     post_id     UUID,
 *     content     TEXT,
 *     media_url   TEXT,
 *     PRIMARY KEY (user_id, post_time, post_id)
 *   ) WITH CLUSTERING ORDER BY (post_time DESC);
 *
 *   -- Table for Q2: home timeline (fan-out on write pattern)
 *   -- When user A posts, write a copy to the timeline of each of A's followers.
 *   CREATE TABLE home_timeline (
 *     viewer_id   UUID,
 *     post_time   TIMESTAMP,
 *     post_id     UUID,
 *     author_id   UUID,
 *     content     TEXT,
 *     PRIMARY KEY (viewer_id, post_time, post_id)
 *   ) WITH CLUSTERING ORDER BY (post_time DESC);
 *
 *   -- Query: SELECT * FROM home_timeline WHERE viewer_id = ? LIMIT 20;
 *
 *   -- Note: This is called "fan-out on write" — denormalize aggressively.
 *   -- Twitter uses this for users with < 100K followers.
 *   -- Users with 10M+ followers use "fan-out on read" (different strategy).
 *
 * ════════════════════════════════════════════════════════
 * EXAMPLE C: IoT Time-Series Data
 * ════════════════════════════════════════════════════════
 *
 * Queries needed:
 *   Q1: Get all readings for sensor X in the last hour
 *   Q2: Get readings for sensor X between two timestamps
 *
 * Problem: If sensor writes every second → 86,400 rows/day, 2.5M rows/month
 *          Unbounded partition growth!
 *
 * Solution: Time-bucketed partition key
 *
 *   CREATE TABLE sensor_readings (
 *     sensor_id   TEXT,
 *     date        DATE,           -- bucket: one partition per sensor per day
 *     reading_time TIMESTAMP,
 *     temperature  DOUBLE,
 *     humidity     DOUBLE,
 *     PRIMARY KEY ((sensor_id, date), reading_time)
 *   ) WITH CLUSTERING ORDER BY (reading_time DESC);
 *
 *   -- Query Q1:
 *   SELECT * FROM sensor_readings
 *   WHERE sensor_id = 'sensor-001' AND date = '2024-01-15'
 *   AND reading_time >= '2024-01-15 09:00:00'
 *   LIMIT 3600;  -- at most 3600 rows (1 per second for 1 hour)
 *
 *   -- Q2 spanning multiple days requires multiple queries (one per day bucket)
 *   -- Do this at the application layer.
 *
 * ════════════════════════════════════════════════════════
 * EXAMPLE D: Chat Messaging (Discord-style)
 * ════════════════════════════════════════════════════════
 *
 *   CREATE TABLE messages (
 *     channel_id  UUID,
 *     bucket      INT,            -- month number: 202401, 202402, etc.
 *     message_id  TIMEUUID,       -- TIMEUUID = UUID with embedded timestamp, sortable
 *     author_id   UUID,
 *     content     TEXT,
 *     PRIMARY KEY ((channel_id, bucket), message_id)
 *   ) WITH CLUSTERING ORDER BY (message_id DESC);
 *
 *   -- Query: latest 50 messages in a channel
 *   SELECT * FROM messages
 *   WHERE channel_id = ? AND bucket = 202401
 *   LIMIT 50;
 *
 *   -- TIMEUUID advantages over TIMESTAMP:
 *   --   Unique even if two messages arrive at the exact same millisecond
 *   --   Generated with nowfunction() in CQL: INSERT ... (NOW(), ...)
 *
 * ─── PART 5: ANTI-PATTERNS TO AVOID ───────────────────────────────
 *
 *  ❌ ALLOW FILTERING    → full partition scan, never in production
 *  ❌ Unbounded partitions → always bucket time-series data
 *  ❌ Secondary index on high-cardinality column → fan-out to all nodes
 *  ❌ IN clause with many values → multiple coordinator round-trips
 *  ❌ Using Cassandra like a relational DB → fight the data model
 *  ❌ SELECT * without LIMIT → can return millions of rows
 *
 * ─── PART 6: MODELING CHECKLIST ────────────────────────────────────
 *
 *  Before creating any table, ask:
 *
 *  1. What is the query this table answers? (write it in CQL first)
 *  2. What is the partition key? Is it evenly distributed?
 *  3. Can the partition grow unbounded? If yes, add a time/range bucket.
 *  4. What is the sort order? → Use clustering column + CLUSTERING ORDER BY.
 *  5. What columns do I need? → Denormalize, include everything the query needs.
 *  6. Is there a uniqueness constraint? → Include a UUID/TIMEUUID in the key.
 */
public class C02_DataModeling {
    public static void main(String[] args) {
        System.out.println("Data Modeling is the most important Cassandra skill.");
        System.out.println("Study the CQL table designs in the comments above.");
        System.out.println("Next: C03_CQL.java — hands-on CQL you can run in cqlsh");
    }
}
