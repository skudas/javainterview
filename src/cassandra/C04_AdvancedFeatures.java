package cassandra;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║   C04 — ADVANCED FEATURES                                        ║
 * ║   Consistency, LWT, Collections, Tombstones, Compaction          ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * ─── 1. CONSISTENCY LEVELS IN DEPTH ────────────────────────────────
 *
 *  Cassandra sends a write/read to ALL replica nodes, but only waits for
 *  ACKs from the number required by your consistency level.
 *
 *  For Replication Factor (RF) = 3:
 *
 *  Level         │ Write waits for │ Read waits for │ Tolerates node loss
 *  ──────────────┼─────────────────┼────────────────┼────────────────────
 *  ANY           │ 1 (hint OK)     │ N/A            │ any (even coordinator)
 *  ONE           │ 1               │ 1              │ 2 nodes down
 *  TWO           │ 2               │ 2              │ 1 node down
 *  THREE         │ 3               │ 3              │ 0 nodes down
 *  QUORUM        │ 2 (RF/2 + 1)    │ 2              │ 1 node down
 *  LOCAL_ONE     │ 1 in local DC   │ 1 in local DC  │ DC-local
 *  LOCAL_QUORUM  │ majority local  │ majority local │ DC-local
 *  EACH_QUORUM   │ quorum each DC  │ quorum each DC │ strict per-DC
 *  ALL           │ 3               │ 3              │ 0 nodes down
 *
 *  STRONG CONSISTENCY formula:
 *    write CL + read CL > RF → you always read what was written
 *    QUORUM + QUORUM > 3 (2+2=4 > 3) → strong ✅
 *    ONE   + ONE   > 3 (1+1=2 < 3) → eventual ❌
 *
 *  Real-world usage:
 *    User login / auth:      LOCAL_QUORUM (strong, within DC)
 *    Shopping cart:          LOCAL_QUORUM (can't show stale cart to user)
 *    Analytics / logs:       ONE (fast, eventual is fine)
 *    Cross-DC replication:   EACH_QUORUM (full consistency across DCs)
 *
 *  In CQL:
 *    CONSISTENCY QUORUM;
 *    SELECT * FROM users WHERE user_id = ?;
 *
 * ─── 2. LIGHTWEIGHT TRANSACTIONS (LWT) ─────────────────────────────
 *
 *  Problem: How do you check-then-act atomically in Cassandra?
 *  Example: "Create user account — fail if email already taken."
 *
 *  Cassandra uses Paxos protocol for LWT → more expensive (~4× slower than normal write).
 *  Use sparingly — only when you genuinely need compare-and-set semantics.
 *
 *  -- Unique email registration (IF NOT EXISTS)
 *  INSERT INTO users_by_email (email, user_id, name)
 *  VALUES ('alice@example.com', uuid(), 'Alice')
 *  IF NOT EXISTS;
 *  -- Returns: [applied]=true (email was free) or [applied]=false (already taken)
 *
 *  -- Optimistic locking / conditional update
 *  UPDATE inventory
 *  SET quantity = 9
 *  WHERE product_id = ?
 *  IF quantity = 10;
 *  -- Returns: [applied]=true if quantity was 10; otherwise [applied]=false + current value
 *
 *  -- Safe account balance update
 *  UPDATE accounts
 *  SET balance = 900
 *  WHERE account_id = ?
 *  IF balance = 1000;
 *
 *  -- Conditional delete
 *  DELETE FROM sessions WHERE session_id = ? IF EXISTS;
 *
 *  LWT Rules:
 *    - Always uses SERIAL or LOCAL_SERIAL consistency (not tunable)
 *    - Much slower than regular writes (Paxos round-trips)
 *    - Cannot span multiple partition keys (no multi-partition LWT)
 *    - Returns a result set with [applied] column
 *
 * ─── 3. COLLECTIONS IN DEPTH ───────────────────────────────────────
 *
 *  Cassandra collections are stored INLINE in the row (not in a separate table).
 *  Keep them small: ideally < 64KB, max 65535 elements.
 *
 *  ── LIST ──────────────────────────────────────────────────────────
 *  Ordered, allows duplicates. Identified by index position.
 *
 *  CREATE TABLE playlists (
 *    playlist_id UUID PRIMARY KEY,
 *    name        TEXT,
 *    track_ids   LIST<UUID>
 *  );
 *
 *  INSERT INTO playlists (playlist_id, name, track_ids)
 *  VALUES (uuid(), 'My Mix', [track1-uuid, track2-uuid]);
 *
 *  UPDATE playlists SET track_ids = track_ids + [track3-uuid] WHERE playlist_id = ?;  -- append
 *  UPDATE playlists SET track_ids = [track0-uuid] + track_ids WHERE playlist_id = ?;  -- prepend
 *  DELETE track_ids[0] FROM playlists WHERE playlist_id = ?;   -- delete by index
 *  UPDATE playlists SET track_ids[1] = track99-uuid WHERE playlist_id = ?;  -- replace by index
 *
 *  ── SET ───────────────────────────────────────────────────────────
 *  Unordered, no duplicates. Good for tags, roles, permissions.
 *
 *  CREATE TABLE articles (
 *    article_id UUID PRIMARY KEY,
 *    title      TEXT,
 *    tags       SET<TEXT>
 *  );
 *
 *  UPDATE articles SET tags = tags + {'java', 'cassandra'} WHERE article_id = ?;  -- add
 *  UPDATE articles SET tags = tags - {'java'} WHERE article_id = ?;               -- remove
 *
 *  ── MAP ───────────────────────────────────────────────────────────
 *  Key-value pairs. Keys are unique. Good for metadata/attributes.
 *
 *  CREATE TABLE products (
 *    product_id  UUID PRIMARY KEY,
 *    name        TEXT,
 *    attributes  MAP<TEXT, TEXT>
 *  );
 *
 *  UPDATE products SET attributes['color'] = 'red' WHERE product_id = ?;  -- set a key
 *  DELETE attributes['color'] FROM products WHERE product_id = ?;         -- remove a key
 *
 *  ── FROZEN ────────────────────────────────────────────────────────
 *  Makes a collection or UDT immutable (written as a single blob).
 *  Required when used as part of a primary key, or nested in another collection.
 *
 *  CREATE TABLE user_addresses (
 *    user_id   UUID PRIMARY KEY,
 *    addresses LIST<FROZEN<MAP<TEXT, TEXT>>>  -- list of maps (nested)
 *  );
 *  -- To update: must replace the entire outer collection
 *
 * ─── 4. USER DEFINED TYPES (UDT) ───────────────────────────────────
 *
 *  Group related fields into a reusable type — stored inline in the row.
 *
 *  CREATE TYPE address (
 *    street   TEXT,
 *    city     TEXT,
 *    state    TEXT,
 *    zip      TEXT,
 *    country  TEXT
 *  );
 *
 *  CREATE TABLE users (
 *    user_id         UUID PRIMARY KEY,
 *    name            TEXT,
 *    shipping_address FROZEN<address>,     -- always frozen when embedded
 *    past_addresses  LIST<FROZEN<address>>
 *  );
 *
 *  INSERT INTO users (user_id, name, shipping_address)
 *  VALUES (uuid(), 'Alice', {
 *    street: '123 Main St',
 *    city: 'Brooklyn',
 *    state: 'NY',
 *    zip: '11201',
 *    country: 'US'
 *  });
 *
 *  SELECT shipping_address.city FROM users WHERE user_id = ?;
 *
 * ─── 5. TOMBSTONES ─────────────────────────────────────────────────
 *
 *  Cassandra NEVER overwrites. DELETE writes a tombstone (a deletion marker).
 *  Tombstones are removed after gc_grace_seconds (default: 10 days).
 *
 *  Why gc_grace_seconds?
 *    If a node was down when you deleted row X, it won't know about the delete.
 *    When it comes back online, it would resurrect X from its SSTable.
 *    gc_grace_seconds gives time for the node to rejoin before compaction erases tombstones.
 *
 *  Tombstone problems:
 *    → Too many tombstones → read latency spikes (Cassandra scans tombstones on every read)
 *    → Warning threshold: 1000 tombstones per partition
 *    → Hard limit: tombstone_failure_threshold = 100,000 → query fails
 *
 *  Avoid tombstone buildup:
 *    ✅ Use TTL instead of explicit deletes (tombstones still created, but expected)
 *    ✅ Use USING TIMESTAMP to make deletes conflict-resolve correctly
 *    ✅ Design to avoid writing nulls (null columns create tombstones too!)
 *    ✅ Monitor: nodetool tpstats | grep Tombstone
 *
 * ─── 6. COMPACTION ─────────────────────────────────────────────────
 *
 *  SSTables are immutable. Old data + new data + tombstones all sit in separate files.
 *  Compaction merges SSTables, removes tombstones, and produces new SSTables.
 *
 *  Compaction strategies:
 *
 *  STCS (SizeTieredCompactionStrategy) — DEFAULT
 *    Merges SSTables of similar size.
 *    Good for: write-heavy workloads.
 *    Bad for: tables with lots of deletes (tombstones sit around longer).
 *
 *  LEVELED (LeveledCompactionStrategy)
 *    Organizes SSTables into levels. Fewer overlapping SSTables.
 *    Good for: read-heavy workloads, even read latency.
 *    Bad for: write amplification (more compaction work per write).
 *
 *  TWCS (TimeWindowCompactionStrategy)
 *    Groups SSTables by time window. Compacts within each window only.
 *    BEST for: time-series data (old time windows never re-compacted).
 *    Requires: data only written in order (no late writes outside window).
 *
 *  Set compaction strategy on table:
 *  CREATE TABLE sensor_readings (...)
 *  WITH compaction = {
 *    'class': 'TimeWindowCompactionStrategy',
 *    'compaction_window_unit': 'HOURS',
 *    'compaction_window_size': 1
 *  };
 *
 * ─── 7. HINTED HANDOFF AND READ REPAIR ─────────────────────────────
 *
 *  HINTED HANDOFF:
 *    If a replica node is down during a write, the coordinator stores a "hint"
 *    and replays the write when the node comes back online.
 *    → This is how Cassandra achieves write availability even when nodes are down.
 *    → Hints are stored for up to max_hint_window_in_ms (default: 3 hours).
 *
 *  READ REPAIR:
 *    When Cassandra reads from multiple replicas (QUORUM), it compares responses.
 *    If one replica is stale, Cassandra repairs it in the background.
 *    read_repair_chance (default: 0.1) = 10% of reads trigger a repair.
 *
 *  NODETOOL REPAIR:
 *    Run manually to fully synchronize all replicas.
 *    nodetool repair -full keyspace tablename
 *    Schedule weekly to prevent data divergence.
 *
 * ─── 8. ANTI-ENTROPY AND MONITORING ────────────────────────────────
 *
 *  Key nodetool commands:
 *
 *  nodetool status            -- cluster health: UN=Up Normal, DN=Down
 *  nodetool info              -- this node's info (heap, uptime, etc.)
 *  nodetool tpstats           -- thread pool stats (dropped messages = problem)
 *  nodetool cfstats <table>   -- table stats: read/write latency, partition sizes
 *  nodetool compactionstats   -- in-progress compactions
 *  nodetool repair            -- sync replicas
 *  nodetool flush             -- flush MemTable to SSTable
 *  nodetool decommission      -- safely remove a node from the ring
 *
 *  Key metrics to watch:
 *    99th percentile read/write latency  → should be < 10ms for p99
 *    Dropped messages                    → indicates overload
 *    Tombstone warnings                  → > 1000 per query = problem
 *    Partition sizes                     → keep < 10MB
 *    Pending compactions                 → growing = I/O bottleneck
 */
public class C04_AdvancedFeatures {
    public static void main(String[] args) {
        System.out.println("Advanced Cassandra features — study LWT and tombstones carefully.");
        System.out.println("Next: C05_JavaDriver.java");
    }
}
