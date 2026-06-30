package cassandra;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║   C06 — REAL-WORLD PATTERNS & INTERVIEW SCENARIOS                ║
 * ║   Complete solutions to classic system design problems           ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * These are the exact scenarios that come up in senior backend / system design
 * interviews when Cassandra is the expected answer.
 *
 * ═══════════════════════════════════════════════════════════
 * PATTERN 1: TIME-SERIES DATA (IoT / Metrics / Logs)
 * ═══════════════════════════════════════════════════════════
 *
 * Scenario: Store temperature readings from 10,000 IoT sensors.
 *           Each sensor writes every 5 seconds (17,280 rows/sensor/day).
 *           Query: "Get last 1 hour of readings for sensor X."
 *
 * Key decisions:
 *   1. Bucket by DAY to prevent unbounded partition growth
 *   2. Use TIMEUUID or TIMESTAMP as clustering key for natural sort
 *   3. Use TWCS compaction — old buckets never re-compacted
 *   4. App must issue one query per day when range spans multiple days
 *
 * ── CQL ──────────────────────────────────────────────────
 *
 *  CREATE TABLE sensor_readings (
 *    sensor_id    TEXT,
 *    date         DATE,            -- bucket: one partition per sensor per day
 *    reading_time TIMESTAMP,
 *    temperature  DOUBLE,
 *    humidity     DOUBLE,
 *    pressure     DOUBLE,
 *    PRIMARY KEY ((sensor_id, date), reading_time)
 *  ) WITH CLUSTERING ORDER BY (reading_time DESC)
 *    AND compaction = {
 *      'class': 'TimeWindowCompactionStrategy',
 *      'compaction_window_unit': 'DAYS',
 *      'compaction_window_size': 1
 *    };
 *
 *  -- Write (Java driver PreparedStatement):
 *  INSERT INTO sensor_readings (sensor_id, date, reading_time, temperature, humidity, pressure)
 *  VALUES ('sensor-001', '2024-01-15', toTimestamp(now()), 23.5, 60.2, 1013.0);
 *
 *  -- Read: last hour of data (single partition, single query)
 *  SELECT * FROM sensor_readings
 *  WHERE sensor_id = 'sensor-001' AND date = '2024-01-15'
 *  AND reading_time >= '2024-01-15 09:00:00'
 *  LIMIT 720;   -- max 720 readings in 1 hour at 5s intervals
 *
 *  -- Read: cross-day range (2024-01-14 22:00 to 2024-01-15 02:00)
 *  -- Application must make 2 queries:
 *  SELECT * FROM sensor_readings WHERE sensor_id = ? AND date = '2024-01-14'
 *    AND reading_time >= '2024-01-14 22:00:00';
 *  SELECT * FROM sensor_readings WHERE sensor_id = ? AND date = '2024-01-15'
 *    AND reading_time <= '2024-01-15 02:00:00';
 *
 * ── Partition size estimate ──────────────────────────────
 *  1 sensor × 1 day × 17,280 rows × ~100 bytes/row = ~1.7MB/partition
 *  Well within the 10MB recommendation ✅
 *
 *
 * ═══════════════════════════════════════════════════════════
 * PATTERN 2: SOCIAL MEDIA FEED (Instagram / Twitter timeline)
 * ═══════════════════════════════════════════════════════════
 *
 * Scenario: Users post photos. Followers see posts in their home feed.
 *           Query: "Get 20 most recent posts in my feed."
 *
 * Two strategies:
 *   Fan-out on WRITE:  when Alice posts, copy to ALL her followers' timelines immediately
 *                      → fast reads, slow writes, large storage
 *                      → used when followers < 100K
 *
 *   Fan-out on READ:   when Bob views his feed, fetch posts from all people he follows
 *                      → slow reads (N queries), fast writes
 *                      → used for celebrities with millions of followers
 *
 * ── CQL (fan-out on write) ───────────────────────────────
 *
 *  -- User's own posts
 *  CREATE TABLE posts_by_user (
 *    user_id     UUID,
 *    post_time   TIMESTAMP,
 *    post_id     UUID,
 *    content     TEXT,
 *    media_url   TEXT,
 *    like_count  COUNTER,
 *    PRIMARY KEY (user_id, post_time, post_id)
 *  ) WITH CLUSTERING ORDER BY (post_time DESC, post_id DESC);
 *
 *  -- Home timeline (denormalized copy of posts for each follower)
 *  CREATE TABLE home_timeline (
 *    viewer_id   UUID,
 *    post_time   TIMESTAMP,
 *    post_id     UUID,
 *    author_id   UUID,
 *    author_name TEXT,       -- denormalized to avoid join
 *    content     TEXT,
 *    media_url   TEXT,
 *    PRIMARY KEY (viewer_id, post_time, post_id)
 *  ) WITH CLUSTERING ORDER BY (post_time DESC, post_id DESC);
 *
 *  -- Write a post (application writes to BOTH tables + fans out to followers):
 *  -- Step 1: Write to posts_by_user
 *  -- Step 2: Query followsers_of_alice (from a separate followers table)
 *  -- Step 3: For each follower, INSERT into home_timeline
 *  --         (do this in parallel with async writes)
 *
 *  -- Read home feed:
 *  SELECT * FROM home_timeline WHERE viewer_id = ? LIMIT 20;
 *
 * ── Followers table ─────────────────────────────────────
 *
 *  CREATE TABLE followers (
 *    user_id     UUID,
 *    follower_id UUID,
 *    followed_at TIMESTAMP,
 *    PRIMARY KEY (user_id, follower_id)
 *  );
 *  -- "Who follows user X?" → WHERE user_id = X
 *
 *  CREATE TABLE following (
 *    user_id    UUID,
 *    follows_id UUID,
 *    followed_at TIMESTAMP,
 *    PRIMARY KEY (user_id, follows_id)
 *  );
 *  -- "Who does user X follow?" → WHERE user_id = X
 *
 *
 * ═══════════════════════════════════════════════════════════
 * PATTERN 3: USER INBOX / DIRECT MESSAGES
 * ═══════════════════════════════════════════════════════════
 *
 * Scenario: Two-way direct messaging (like WhatsApp DMs).
 *           Query 1: "Show my inbox — all conversations sorted by latest message."
 *           Query 2: "Show messages in conversation X."
 *
 * ── CQL ──────────────────────────────────────────────────
 *
 *  -- Messages within a conversation thread
 *  CREATE TABLE messages (
 *    conversation_id UUID,
 *    message_time    TIMEUUID,     -- TIMEUUID = globally unique + time-sortable
 *    sender_id       UUID,
 *    content         TEXT,
 *    read            BOOLEAN,
 *    PRIMARY KEY (conversation_id, message_time)
 *  ) WITH CLUSTERING ORDER BY (message_time DESC);
 *
 *  -- Inbox view: latest conversation per user
 *  CREATE TABLE inbox (
 *    user_id         UUID,
 *    last_message_time TIMEUUID,
 *    conversation_id UUID,
 *    other_user_id   UUID,
 *    other_user_name TEXT,
 *    last_message    TEXT,         -- denormalized preview
 *    unread_count    INT,
 *    PRIMARY KEY (user_id, last_message_time, conversation_id)
 *  ) WITH CLUSTERING ORDER BY (last_message_time DESC);
 *
 *  -- Conversations table (metadata)
 *  CREATE TABLE conversations (
 *    conversation_id UUID PRIMARY KEY,
 *    participant_1   UUID,
 *    participant_2   UUID,
 *    created_at      TIMESTAMP
 *  );
 *
 *  -- Send a message (app writes to all three tables in a logged batch):
 *  BEGIN BATCH
 *    INSERT INTO messages (conversation_id, message_time, sender_id, content)
 *    VALUES (conv-uuid, now(), sender-uuid, 'Hey!');
 *
 *    -- Update inbox for receiver
 *    INSERT INTO inbox (user_id, last_message_time, conversation_id, other_user_id, last_message)
 *    VALUES (receiver-uuid, now(), conv-uuid, sender-uuid, 'Hey!');
 *
 *    -- Update inbox for sender
 *    INSERT INTO inbox (user_id, last_message_time, conversation_id, other_user_id, last_message)
 *    VALUES (sender-uuid, now(), conv-uuid, receiver-uuid, 'Hey!');
 *  APPLY BATCH;
 *
 *  -- Read inbox:
 *  SELECT * FROM inbox WHERE user_id = ? LIMIT 20;
 *
 *  -- Read conversation messages:
 *  SELECT * FROM messages WHERE conversation_id = ? LIMIT 50;
 *
 *
 * ═══════════════════════════════════════════════════════════
 * PATTERN 4: LEADERBOARD / RANKINGS
 * ═══════════════════════════════════════════════════════════
 *
 * Scenario: Gaming leaderboard. Show top 100 players by score.
 *           Update score when a game ends.
 *           Query: "Top 100 players globally" / "Top 100 in my country."
 *
 * Note: For live leaderboards, Redis ZSET is often better.
 *       Cassandra is used when you need persistent leaderboard history.
 *
 * ── CQL ──────────────────────────────────────────────────
 *
 *  -- Global leaderboard (single partition = small! max ~1000 players per partition)
 *  -- For millions of players, shard by score range or region
 *  CREATE TABLE leaderboard_global (
 *    game_id    TEXT,
 *    score      BIGINT,
 *    player_id  UUID,
 *    player_name TEXT,
 *    PRIMARY KEY (game_id, score, player_id)
 *  ) WITH CLUSTERING ORDER BY (score DESC, player_id ASC);
 *
 *  -- Regional leaderboard
 *  CREATE TABLE leaderboard_by_country (
 *    game_id    TEXT,
 *    country    TEXT,
 *    score      BIGINT,
 *    player_id  UUID,
 *    player_name TEXT,
 *    PRIMARY KEY ((game_id, country), score, player_id)
 *  ) WITH CLUSTERING ORDER BY (score DESC, player_id ASC);
 *
 *  -- Read top 100 globally:
 *  SELECT * FROM leaderboard_global WHERE game_id = 'chess' LIMIT 100;
 *
 *  -- Read top 100 in India:
 *  SELECT * FROM leaderboard_by_country WHERE game_id = 'chess' AND country = 'IN' LIMIT 100;
 *
 *  -- Problem: How do you UPDATE a player's score?
 *  --   Can't UPDATE a clustering key → DELETE old row, INSERT new row
 *  BEGIN BATCH
 *    DELETE FROM leaderboard_global WHERE game_id = ? AND score = oldScore AND player_id = ?;
 *    INSERT INTO leaderboard_global (game_id, score, player_id, player_name) VALUES (?, newScore, ?, ?);
 *  APPLY BATCH;
 *
 *
 * ═══════════════════════════════════════════════════════════
 * PATTERN 5: SHOPPING CART (Ephemeral Data with TTL)
 * ═══════════════════════════════════════════════════════════
 *
 *  CREATE TABLE shopping_carts (
 *    user_id     UUID,
 *    product_id  UUID,
 *    quantity    INT,
 *    added_at    TIMESTAMP,
 *    product_name TEXT,
 *    price        DECIMAL,
 *    PRIMARY KEY (user_id, product_id)
 *  ) WITH default_time_to_live = 2592000;  -- cart expires in 30 days
 *
 *  -- Add item:
 *  INSERT INTO shopping_carts (user_id, product_id, quantity, product_name, price, added_at)
 *  VALUES (?, ?, 1, 'iPhone 15', 999.00, toTimestamp(now()))
 *  USING TTL 2592000;  -- or rely on table default
 *
 *  -- Update quantity:
 *  UPDATE shopping_carts SET quantity = 3 WHERE user_id = ? AND product_id = ?;
 *
 *  -- Remove item:
 *  DELETE FROM shopping_carts WHERE user_id = ? AND product_id = ?;
 *
 *  -- Get cart:
 *  SELECT * FROM shopping_carts WHERE user_id = ?;
 *
 *
 * ─── INTERVIEW QUESTIONS AND ANSWERS ───────────────────────────────
 *
 *  Q: Why can't you do JOINs in Cassandra?
 *  A: Cassandra is distributed across many nodes. A JOIN would require
 *     data from multiple partitions (potentially on different nodes) to be
 *     merged at query time — prohibitively expensive across a cluster.
 *     Solution: denormalize (store the data you need in each table).
 *
 *  Q: How does Cassandra handle node failure?
 *  A: With RF=3, data exists on 3 nodes. If one goes down:
 *     - Writes: coordinator stores hints, replays when node recovers
 *     - Reads: coordinator reads from 2 remaining replicas (QUORUM)
 *     - Node recovery: runs repair to resync missed writes
 *
 *  Q: What is a "hot partition" and how do you prevent it?
 *  A: A partition that receives disproportionately more traffic than others.
 *     Caused by: low-cardinality partition key (e.g., country=US → 300M rows).
 *     Fix: Add a random bucket/shard to the partition key to spread load.
 *
 *  Q: When would you use QUORUM vs ONE consistency?
 *  A: QUORUM: when reading stale data causes a user-visible problem
 *             (account balance, inventory, authentication).
 *     ONE:    when staleness is acceptable (analytics, logs, feeds).
 *
 *  Q: What is the difference between CQL and SQL?
 *  A: CQL looks like SQL but has strict limitations:
 *     - No JOINs, no subqueries, no GROUP BY (without aggregation functions)
 *     - WHERE clause MUST include the partition key
 *     - Can only filter on clustering columns if you also provide the partition key
 *     - ORDER BY only works on clustering columns in their declared direction
 *
 *  Q: How is Cassandra different from DynamoDB?
 *  A: Both are wide-column AP databases with similar data models.
 *     - Cassandra: open source, self-hosted or cloud (Astra DB)
 *     - DynamoDB: AWS-managed, serverless, pay-per-use
 *     - Both: partition key + sort key model, no joins, eventual consistency
 *     - DynamoDB has GSI (Global Secondary Index) which is more flexible
 *
 *  Q: How does Cassandra achieve high write throughput?
 *  A: Three reasons:
 *     1. Writes go to CommitLog (sequential disk write) + MemTable (memory)
 *        — no random I/O, unlike B-tree indexes in RDBMS
 *     2. No locking: writes are append-only, no in-place updates
 *     3. Replication is async by default (coordinator doesn't wait for all replicas)
 */
public class C06_RealWorldPatterns {
    public static void main(String[] args) {
        System.out.println("Real-world Cassandra patterns — these are interview scenarios.");
        System.out.println("Master the time-series and feed patterns first.");
        System.out.println("Then C07_InterviewQuickRef.java for the cheat sheet.");
    }
}
