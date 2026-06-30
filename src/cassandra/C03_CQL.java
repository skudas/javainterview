package cassandra;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║   C03 — CQL: Cassandra Query Language                            ║
 * ║   Copy each block into `cqlsh` to run it                         ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * Start cqlsh: docker run -d --name cass cassandra:4.1
 *              docker exec -it cass cqlsh
 *
 * ─── 1. KEYSPACE (= database) ──────────────────────────────────────
 *
 *  -- SimpleStrategy: for single datacenter / local dev
 *  CREATE KEYSPACE ecommerce
 *  WITH replication = {
 *    'class': 'SimpleStrategy',
 *    'replication_factor': 3
 *  };
 *
 *  -- NetworkTopologyStrategy: for production / multi-datacenter
 *  CREATE KEYSPACE ecommerce
 *  WITH replication = {
 *    'class': 'NetworkTopologyStrategy',
 *    'us-east': 3,
 *    'eu-west': 3
 *  };
 *
 *  USE ecommerce;            -- switch to this keyspace
 *  DESCRIBE keyspaces;       -- list all keyspaces
 *  DESCRIBE KEYSPACE ecommerce;
 *
 * ─── 2. TABLE DDL ──────────────────────────────────────────────────
 *
 *  -- Simple primary key (no clustering)
 *  CREATE TABLE users (
 *    user_id   UUID PRIMARY KEY,
 *    email     TEXT,
 *    name      TEXT,
 *    joined_at TIMESTAMP
 *  );
 *
 *  -- Composite key with clustering + ordering
 *  CREATE TABLE orders_by_user (
 *    user_id    UUID,
 *    order_time TIMESTAMP,
 *    order_id   UUID,
 *    status     TEXT,
 *    total      DECIMAL,
 *    items      LIST<TEXT>,
 *    PRIMARY KEY (user_id, order_time, order_id)
 *  ) WITH CLUSTERING ORDER BY (order_time DESC, order_id ASC)
 *    AND comment = 'Orders per user, newest first';
 *
 *  -- Composite PARTITION key (prevents hot partitions)
 *  CREATE TABLE sensor_readings (
 *    sensor_id    TEXT,
 *    date         DATE,
 *    reading_time TIMESTAMP,
 *    temperature  DOUBLE,
 *    humidity     DOUBLE,
 *    PRIMARY KEY ((sensor_id, date), reading_time)
 *  ) WITH CLUSTERING ORDER BY (reading_time DESC);
 *
 *  -- Alter table (only ADD allowed — no DROP or ALTER COLUMN TYPE)
 *  ALTER TABLE users ADD phone TEXT;
 *  ALTER TABLE users ADD tags SET<TEXT>;
 *
 *  DESCRIBE TABLE orders_by_user;
 *  DROP TABLE orders_by_user;
 *
 * ─── 3. DATA TYPES ─────────────────────────────────────────────────
 *
 *  Scalar:
 *    UUID        → random unique identifier:  uuid() or gen random
 *    TIMEUUID    → time-based UUID, sortable: now()
 *    TEXT / VARCHAR → UTF-8 string
 *    INT / BIGINT / SMALLINT / TINYINT / VARINT / COUNTER
 *    FLOAT / DOUBLE / DECIMAL
 *    BOOLEAN
 *    TIMESTAMP   → epoch milliseconds: '2024-01-15 09:30:00'
 *    DATE        → date only: '2024-01-15'
 *    TIME        → time only: '09:30:00'
 *    BLOB        → binary data
 *    INET        → IP address
 *    DURATION    → time duration: 3h20m
 *
 *  Collections (stored inline in the row):
 *    LIST<TEXT>          → ordered, duplicates allowed
 *    SET<TEXT>           → unordered, no duplicates
 *    MAP<TEXT, INT>      → key-value pairs
 *    FROZEN<LIST<TEXT>>  → immutable, written atomically (required for nested types)
 *
 *  Tuple:  TUPLE<TEXT, INT, DOUBLE>
 *  UDT:    CREATE TYPE address (street TEXT, city TEXT, zip TEXT)
 *
 * ─── 4. INSERT ─────────────────────────────────────────────────────
 *
 *  -- Basic insert (INSERT = upsert in Cassandra — it always overwrites)
 *  INSERT INTO users (user_id, email, name, joined_at)
 *  VALUES (uuid(), 'alice@example.com', 'Alice', toTimestamp(now()));
 *
 *  -- Prevent overwrite: IF NOT EXISTS (Lightweight Transaction)
 *  INSERT INTO users (user_id, email, name)
 *  VALUES (uuid(), 'bob@example.com', 'Bob')
 *  IF NOT EXISTS;
 *  -- Returns [applied] = true if inserted, false if row already existed.
 *
 *  -- Insert with TTL (row expires after N seconds)
 *  INSERT INTO sessions (session_id, user_id, token)
 *  VALUES (uuid(), uuid(), 'abc123')
 *  USING TTL 3600;   -- expires in 1 hour
 *
 *  -- Insert with timestamp (used for conflict resolution)
 *  INSERT INTO users (user_id, email) VALUES (uuid(), 'carol@test.com')
 *  USING TIMESTAMP 1705312200000000;  -- microseconds since epoch
 *
 *  -- Insert with collections
 *  INSERT INTO products (product_id, name, tags, attributes)
 *  VALUES (
 *    uuid(),
 *    'Laptop Pro 15',
 *    {'electronics', 'computers', 'laptops'},  -- SET literal
 *    {'brand': 'Apple', 'ram': '16GB'}          -- MAP literal
 *  );
 *
 * ─── 5. SELECT ─────────────────────────────────────────────────────
 *
 *  -- Get all orders for a user (partition key required in WHERE)
 *  SELECT * FROM orders_by_user WHERE user_id = a1b2c3d4-...;
 *
 *  -- Slice by clustering column (range query)
 *  SELECT * FROM orders_by_user
 *  WHERE user_id = a1b2c3d4-...
 *  AND order_time >= '2024-01-01'
 *  AND order_time < '2024-02-01';
 *
 *  -- Pagination: LIMIT + paging state
 *  SELECT * FROM orders_by_user WHERE user_id = ? LIMIT 20;
 *  -- Use paging token (driver handles this) for page 2, 3, etc.
 *
 *  -- Specific columns
 *  SELECT user_id, email, name FROM users WHERE user_id = ?;
 *
 *  -- Token function: scan a partition range across nodes
 *  SELECT * FROM users WHERE token(user_id) >= token(someUUID)
 *  LIMIT 100;
 *
 *  -- Count (only efficient on small partitions)
 *  SELECT COUNT(*) FROM orders_by_user WHERE user_id = ?;
 *
 *  -- ALLOW FILTERING (development only — NEVER in production)
 *  -- Forces a full cluster scan
 *  SELECT * FROM users WHERE email = 'alice@example.com' ALLOW FILTERING;
 *
 * ─── 6. UPDATE ─────────────────────────────────────────────────────
 *
 *  -- Basic update (must specify the full primary key)
 *  UPDATE users SET name = 'Alice Smith' WHERE user_id = ?;
 *
 *  -- Update with TTL (the field expires after N seconds)
 *  UPDATE sessions USING TTL 1800 SET token = 'xyz' WHERE session_id = ?;
 *
 *  -- Conditional update (LWT — Lightweight Transaction)
 *  UPDATE orders SET status = 'SHIPPED'
 *  WHERE user_id = ? AND order_time = ? AND order_id = ?
 *  IF status = 'PENDING';
 *  -- Returns [applied] = true if the condition was met.
 *
 *  -- Update collection fields
 *  UPDATE products SET tags = tags + {'sale'} WHERE product_id = ?;  -- add to set
 *  UPDATE products SET tags = tags - {'sale'} WHERE product_id = ?;  -- remove from set
 *  UPDATE products SET attributes['color'] = 'blue' WHERE product_id = ?;  -- map key
 *
 * ─── 7. DELETE ─────────────────────────────────────────────────────
 *
 *  -- Delete a whole row
 *  DELETE FROM users WHERE user_id = ?;
 *
 *  -- Delete specific columns (sets them to null/tombstone)
 *  DELETE email FROM users WHERE user_id = ?;
 *
 *  -- Delete a range of clustering rows
 *  DELETE FROM orders_by_user
 *  WHERE user_id = ? AND order_time < '2023-01-01';
 *
 *  -- Conditional delete (LWT)
 *  DELETE FROM users WHERE user_id = ? IF EXISTS;
 *
 *  -- Note: DELETEs write a TOMBSTONE (not immediately erased).
 *  -- Tombstones are garbage-collected after gc_grace_seconds (default 10 days).
 *  -- Too many tombstones → read performance degrades (Cassandra reads them all).
 *
 * ─── 8. BATCH ──────────────────────────────────────────────────────
 *
 *  -- LOGGED BATCH: atomic across multiple statements (uses a batch log)
 *  -- Use ONLY to keep multiple tables in sync (denormalized tables)
 *  BEGIN BATCH
 *    INSERT INTO orders_by_user (user_id, order_time, order_id, status)
 *    VALUES (?, ?, ?, 'PLACED');
 *
 *    INSERT INTO orders_by_id (order_id, user_id, status)
 *    VALUES (?, ?, 'PLACED');
 *  APPLY BATCH;
 *
 *  -- UNLOGGED BATCH: no atomicity guarantee, slightly faster
 *  -- Use ONLY when all statements target the same partition
 *  BEGIN UNLOGGED BATCH
 *    INSERT INTO orders_by_user ... ;
 *    INSERT INTO orders_by_user ... ;
 *  APPLY BATCH;
 *
 *  -- WARNING: batches are NOT for performance optimization.
 *  --   Batching across different partitions is SLOWER than parallel inserts
 *  --   because all statements go through a single coordinator.
 *  --   Use batches ONLY for atomicity (keeping denormalized tables in sync).
 *
 * ─── 9. SECONDARY INDEXES ──────────────────────────────────────────
 *
 *  -- Create an index on a non-primary-key column
 *  CREATE INDEX ON users (email);
 *
 *  -- Now you can query by email (but it fans out to all nodes — expensive)
 *  SELECT * FROM users WHERE email = 'alice@example.com';
 *
 *  -- SAI (Storage-Attached Index) — Cassandra 4.0+, more efficient
 *  CREATE CUSTOM INDEX ON users (email)
 *  USING 'StorageAttachedIndex';
 *
 *  -- When to use secondary indexes:
 *  ✅ Low-cardinality columns (status, country, boolean flags)
 *  ✅ Columns always queried WITH the partition key
 *  ❌ High-cardinality columns like email, user_id (too many nodes hit)
 *  ❌ Columns that change frequently (index updates are expensive)
 *
 *  -- Better alternative: create a separate lookup table
 *  CREATE TABLE users_by_email (
 *    email   TEXT PRIMARY KEY,
 *    user_id UUID
 *  );
 *  -- Faster than index: single-node lookup, no fan-out
 *
 * ─── 10. MATERIALIZED VIEWS ────────────────────────────────────────
 *
 *  -- A view maintained automatically by Cassandra (writes to base table → view updated)
 *  CREATE MATERIALIZED VIEW users_by_email AS
 *    SELECT user_id, email, name
 *    FROM users
 *    WHERE email IS NOT NULL AND user_id IS NOT NULL
 *    PRIMARY KEY (email, user_id);
 *
 *  SELECT * FROM users_by_email WHERE email = 'alice@example.com';
 *
 *  -- Trade-off: writes are slower (Cassandra maintains the view).
 *  --            Still experimental in some versions — many teams prefer manual tables.
 *
 * ─── 11. COUNTERS ──────────────────────────────────────────────────
 *
 *  -- Distributed counter (atomic increment/decrement)
 *  CREATE TABLE page_views (
 *    page_id  TEXT PRIMARY KEY,
 *    views    COUNTER
 *  );
 *
 *  UPDATE page_views SET views = views + 1 WHERE page_id = '/home';
 *  UPDATE page_views SET views = views - 1 WHERE page_id = '/home';
 *  SELECT views FROM page_views WHERE page_id = '/home';
 *
 *  -- Rules for counter tables:
 *  --   1. Counter columns cannot be mixed with non-counter columns
 *  --   2. Cannot INSERT into a counter table — only UPDATE
 *  --   3. Not 100% accurate under network partition (approximate counter)
 *  --   4. For exact counting, use AtomicInteger in Redis or a dedicated service
 *
 * ─── 12. TTL (Time To Live) ────────────────────────────────────────
 *
 *  -- Set TTL on insert (auto-delete after N seconds)
 *  INSERT INTO sessions (session_id, user_id, token)
 *  VALUES (uuid(), uuid(), 'tok123')
 *  USING TTL 86400;   -- 24 hours
 *
 *  -- Check remaining TTL
 *  SELECT TTL(token) FROM sessions WHERE session_id = ?;
 *
 *  -- Set default TTL on the table
 *  CREATE TABLE sessions (
 *    session_id UUID PRIMARY KEY,
 *    user_id    UUID,
 *    token      TEXT
 *  ) WITH default_time_to_live = 86400;  -- all rows expire in 24h by default
 *
 *  -- TTL creates tombstones when it expires — normal, but monitor tombstone count.
 *
 * ─── 13. USEFUL CQLSH COMMANDS ─────────────────────────────────────
 *
 *  DESCRIBE keyspaces;
 *  DESCRIBE tables;
 *  DESCRIBE TABLE sensor_readings;
 *  DESCRIBE TYPES;                    -- list UDTs
 *
 *  COPY users TO 'users.csv';         -- export
 *  COPY users FROM 'users.csv';       -- import
 *
 *  TRACING ON;                        -- show execution trace for next query
 *  SELECT * FROM users WHERE user_id = ?;
 *  TRACING OFF;
 *
 *  CONSISTENCY QUORUM;               -- change consistency level for this session
 *  CONSISTENCY ONE;
 */
public class C03_CQL {
    public static void main(String[] args) {
        System.out.println("CQL Reference — copy blocks into cqlsh to run them.");
        System.out.println("Next: C04_AdvancedFeatures.java");
    }
}
