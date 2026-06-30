package cassandra;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║   C05 — JAVA DRIVER (DataStax Driver 4.x)                        ║
 * ║   How to use Cassandra from a Spring Boot / Java backend         ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * Dependency (Maven):
 *   <dependency>
 *     <groupId>com.datastax.oss</groupId>
 *     <artifactId>java-driver-core</artifactId>
 *     <version>4.17.0</version>
 *   </dependency>
 *
 * Spring Boot:
 *   <dependency>
 *     <groupId>org.springframework.boot</groupId>
 *     <artifactId>spring-boot-starter-data-cassandra</artifactId>
 *   </dependency>
 *
 * ─── 1. CONNECT ─────────────────────────────────────────────────────
 *
 *   // Basic connection
 *   CqlSession session = CqlSession.builder()
 *       .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
 *       .withLocalDatacenter("datacenter1")
 *       .withKeyspace("ecommerce")
 *       .build();
 *
 *   // Always close the session when done (or use try-with-resources)
 *   try (CqlSession session = CqlSession.builder()...build()) {
 *       // use session
 *   }
 *
 *   // Production: connect to multiple nodes for fault tolerance
 *   CqlSession session = CqlSession.builder()
 *       .addContactPoints(List.of(
 *           new InetSocketAddress("cassandra-1", 9042),
 *           new InetSocketAddress("cassandra-2", 9042),
 *           new InetSocketAddress("cassandra-3", 9042)
 *       ))
 *       .withLocalDatacenter("us-east")
 *       .withAuthCredentials("username", "password")
 *       .withKeyspace("ecommerce")
 *       .build();
 *   // Driver auto-discovers the rest of the cluster via gossip.
 *   // You don't need to list all nodes.
 *
 * ─── 2. STATEMENT TYPES ─────────────────────────────────────────────
 *
 *  There are 3 ways to execute a query. Know when to use each.
 *
 *  ── SimpleStatement ───────────────────────────────────────────────
 *  For ad-hoc queries: DDL, one-off scripts, testing.
 *  NOT for repeated queries — no server-side caching.
 *
 *   SimpleStatement stmt = SimpleStatement
 *       .newInstance("SELECT * FROM users WHERE user_id = ?", userId)
 *       .setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
 *
 *   ResultSet rs = session.execute(stmt);
 *
 *  ── PreparedStatement ─────────────────────────────────────────────
 *  For any query executed more than once.
 *  Cassandra parses and caches the query plan server-side.
 *  Binding just sends the parameters — much faster than SimpleStatement.
 *
 *   // Prepare ONCE at startup (expensive — parses on server)
 *   PreparedStatement findUser = session.prepare(
 *       "SELECT * FROM users WHERE user_id = ?"
 *   );
 *
 *   // Bind and execute MANY TIMES (cheap)
 *   BoundStatement bound = findUser.bind(userId)
 *       .setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM)
 *       .setTimeout(Duration.ofSeconds(2));
 *
 *   ResultSet rs = session.execute(bound);
 *
 *  ── BatchStatement ────────────────────────────────────────────────
 *  For keeping multiple denormalized tables in sync atomically.
 *
 *   PreparedStatement insertByUser = session.prepare(
 *       "INSERT INTO orders_by_user (user_id, order_time, order_id, status) VALUES (?, ?, ?, ?)"
 *   );
 *   PreparedStatement insertById = session.prepare(
 *       "INSERT INTO orders_by_id (order_id, user_id, status) VALUES (?, ?, ?)"
 *   );
 *
 *   BatchStatement batch = BatchStatement.builder(DefaultBatchType.LOGGED)
 *       .addStatement(insertByUser.bind(userId, orderTime, orderId, "PLACED"))
 *       .addStatement(insertById.bind(orderId, userId, "PLACED"))
 *       .build();
 *
 *   session.execute(batch);
 *
 * ─── 3. READING RESULTS ─────────────────────────────────────────────
 *
 *   ResultSet rs = session.execute(bound);
 *
 *   // Get a single row
 *   Row row = rs.one();   // null if no results
 *   if (row != null) {
 *       UUID userId     = row.getUuid("user_id");
 *       String email    = row.getString("email");
 *       Instant joinedAt = row.getInstant("joined_at");
 *   }
 *
 *   // Iterate all rows
 *   for (Row row : rs) {
 *       String name = row.getString("name");
 *   }
 *
 *   // Stream
 *   List<String> emails = rs.all().stream()
 *       .map(r -> r.getString("email"))
 *       .collect(Collectors.toList());
 *
 *   // Map to your domain object
 *   User user = row == null ? null : new User(
 *       row.getUuid("user_id"),
 *       row.getString("email"),
 *       row.getString("name")
 *   );
 *
 *   // Collections
 *   List<String>        tags  = row.getList("tags", String.class);
 *   Set<String>         roles = row.getSet("roles", String.class);
 *   Map<String, String> attrs = row.getMap("attributes", String.class, String.class);
 *
 * ─── 4. ASYNC EXECUTION ─────────────────────────────────────────────
 *
 *  The driver returns CompletionStage (compatible with CompletableFuture).
 *  Use async for high-throughput scenarios — don't block threads waiting for Cassandra.
 *
 *   CompletableFuture<User> getUserAsync(UUID userId) {
 *       BoundStatement bound = findUserStmt.bind(userId);
 *       return session.executeAsync(bound)
 *           .thenApply(rs -> {
 *               Row row = rs.one();
 *               return row == null ? null : mapToUser(row);
 *           })
 *           .toCompletableFuture();
 *   }
 *
 *   // Parallel async reads — fetch 100 users at once
 *   List<CompletableFuture<User>> futures = userIds.stream()
 *       .map(id -> getUserAsync(id))
 *       .collect(Collectors.toList());
 *
 *   CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
 *       .thenRun(() -> {
 *           List<User> users = futures.stream()
 *               .map(CompletableFuture::join)
 *               .collect(Collectors.toList());
 *           System.out.println("Got " + users.size() + " users");
 *       });
 *
 * ─── 5. PAGINATION ──────────────────────────────────────────────────
 *
 *  Cassandra returns results in pages. Driver handles this automatically.
 *
 *   // Automatic iteration (driver fetches next page transparently)
 *   SimpleStatement stmt = SimpleStatement
 *       .newInstance("SELECT * FROM orders_by_user WHERE user_id = ?", userId)
 *       .setPageSize(50);  // fetch 50 rows per page
 *
 *   for (Row row : session.execute(stmt)) {
 *       process(row);  // driver fetches next page as you iterate
 *   }
 *
 *   // Manual paging (for REST API: "give me page 3")
 *   ResultSet firstPage = session.execute(
 *       SimpleStatement.newInstance("SELECT * FROM orders_by_user WHERE user_id = ?", userId)
 *           .setPageSize(20)
 *   );
 *
 *   ByteBuffer pagingState = firstPage.getExecutionInfo().getPagingState();
 *   // serialize and send pagingState to client in API response
 *
 *   // Next page request: client sends pagingState back
 *   ResultSet nextPage = session.execute(
 *       SimpleStatement.newInstance("SELECT * FROM orders_by_user WHERE user_id = ?", userId)
 *           .setPageSize(20)
 *           .setPagingState(pagingState)
 *   );
 *
 * ─── 6. LWT (Lightweight Transactions) IN JAVA ──────────────────────
 *
 *   PreparedStatement registerEmail = session.prepare(
 *       "INSERT INTO users_by_email (email, user_id, name) VALUES (?, ?, ?) IF NOT EXISTS"
 *   );
 *
 *   BoundStatement bound = registerEmail.bind("alice@example.com", userId, "Alice")
 *       .setSerialConsistencyLevel(ConsistencyLevel.LOCAL_SERIAL);
 *
 *   ResultSet rs = session.execute(bound);
 *   Row result = rs.one();
 *   boolean applied = result.getBoolean("[applied]");
 *
 *   if (applied) {
 *       System.out.println("Email registered successfully");
 *   } else {
 *       UUID existingUserId = result.getUuid("user_id");
 *       System.out.println("Email already taken by user: " + existingUserId);
 *   }
 *
 * ─── 7. SPRING BOOT INTEGRATION ─────────────────────────────────────
 *
 *  application.properties:
 *    spring.cassandra.contact-points=localhost:9042
 *    spring.cassandra.local-datacenter=datacenter1
 *    spring.cassandra.keyspace-name=ecommerce
 *    spring.cassandra.schema-action=CREATE_IF_NOT_EXISTS
 *
 *  Entity:
 *   @Table("orders_by_user")
 *   public class Order {
 *       @PrimaryKeyColumn(name = "user_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
 *       private UUID userId;
 *
 *       @PrimaryKeyColumn(name = "order_time", ordinal = 1, type = PrimaryKeyType.CLUSTERED,
 *                         ordering = Ordering.DESCENDING)
 *       private Instant orderTime;
 *
 *       @PrimaryKeyColumn(name = "order_id", ordinal = 2, type = PrimaryKeyType.CLUSTERED)
 *       private UUID orderId;
 *
 *       @Column("status")
 *       private String status;
 *
 *       @Column("total")
 *       private BigDecimal total;
 *       // getters, setters, constructors
 *   }
 *
 *  Repository:
 *   @Repository
 *   public interface OrderRepository extends CassandraRepository<Order, UUID> {
 *       List<Order> findByUserId(UUID userId);
 *
 *       @Query("SELECT * FROM orders_by_user WHERE user_id = ? AND order_time >= ?")
 *       List<Order> findRecentOrders(UUID userId, Instant since);
 *   }
 *
 *  Service:
 *   @Service
 *   public class OrderService {
 *       private final OrderRepository repo;
 *       private final CassandraOperations cassandra;  // for custom queries
 *
 *       public List<Order> getOrders(UUID userId) {
 *           return repo.findByUserId(userId);
 *       }
 *
 *       public void placeOrder(Order order) {
 *           repo.save(order);
 *       }
 *   }
 *
 * ─── 8. COMMON JAVA DRIVER PATTERNS ────────────────────────────────
 *
 *  Pattern 1: Repository with prepared statements (production-grade)
 *
 *   @Repository
 *   public class UserRepository {
 *       private final CqlSession session;
 *       private final PreparedStatement findById;
 *       private final PreparedStatement insert;
 *       private final PreparedStatement updateStatus;
 *
 *       public UserRepository(CqlSession session) {
 *           this.session = session;
 *           // Prepare all statements at startup — NOT inside request handlers
 *           this.findById = session.prepare(
 *               "SELECT * FROM users WHERE user_id = ?");
 *           this.insert = session.prepare(
 *               "INSERT INTO users (user_id, email, name, joined_at) VALUES (?, ?, ?, ?)");
 *           this.updateStatus = session.prepare(
 *               "UPDATE users SET status = ? WHERE user_id = ? IF status = ?");
 *       }
 *
 *       public Optional<User> findById(UUID userId) {
 *           Row row = session.execute(
 *               findById.bind(userId)
 *                   .setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM)
 *           ).one();
 *           return Optional.ofNullable(row).map(this::mapToUser);
 *       }
 *
 *       public void save(User user) {
 *           session.execute(insert.bind(
 *               user.getId(), user.getEmail(), user.getName(), Instant.now()
 *           ).setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM));
 *       }
 *
 *       private User mapToUser(Row row) {
 *           return new User(
 *               row.getUuid("user_id"),
 *               row.getString("email"),
 *               row.getString("name")
 *           );
 *       }
 *   }
 *
 *  Pattern 2: TTL-based session management
 *
 *   PreparedStatement createSession = session.prepare(
 *       "INSERT INTO sessions (session_id, user_id, token, created_at) " +
 *       "VALUES (?, ?, ?, ?) USING TTL ?"
 *   );
 *
 *   session.execute(createSession.bind(
 *       sessionId, userId, token, Instant.now(), 3600  // TTL = 1 hour
 *   ));
 *
 *  Pattern 3: Fan-out write (social media post)
 *
 *   void publishPost(Post post, List<UUID> followerIds) {
 *       PreparedStatement writeTimeline = session.prepare(
 *           "INSERT INTO home_timeline (viewer_id, post_time, post_id, author_id, content) " +
 *           "VALUES (?, ?, ?, ?, ?)"
 *       );
 *
 *       // Fan out to all followers in parallel
 *       List<CompletionStage<AsyncResultSet>> writes = followerIds.stream()
 *           .map(followerId -> session.executeAsync(
 *               writeTimeline.bind(followerId, post.getCreatedAt(),
 *                   post.getId(), post.getAuthorId(), post.getContent())
 *           ))
 *           .collect(Collectors.toList());
 *
 *       // Wait for all writes to complete
 *       CompletableFuture.allOf(
 *           writes.stream()
 *               .map(CompletionStage::toCompletableFuture)
 *               .toArray(CompletableFuture[]::new)
 *       ).join();
 *   }
 */
public class C05_JavaDriver {
    public static void main(String[] args) {
        System.out.println("Java Driver patterns — PreparedStatement is the key pattern to master.");
        System.out.println("Next: C06_RealWorldPatterns.java");
    }
}
