package fu.osms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class OsmsApplication {

	/**
	 * MAIN METHOD — Bootstrap thông minh: tự tạo database + schema nếu chưa có.
	 *
	 * VẤN ĐỀ LỊCH SỬ:
	 *   1. SchemaEnvironmentPostProcessor (META-INF/spring.factories) chỉ chạy khi
	 *      Spring Boot load file spring.factories → KHÔNG đáng tin cậy 100%.
	 *   2. spring.sql.init chạy SAU Hibernate JPA → không ngăn được Hibernate
	 *      generate DDL fail với ENUM types.
	 *   3. main() chạy TRƯỚC tất cả → 100% chắc chắn chạy trước Spring.
	 *   4. Database "osms" có thể KHÔNG TỒN TẠI nếu free tier expired hoặc
	 *      render.yaml chưa apply → phải tự CREATE DATABASE.
	 *
	 * FLOW:
	 *   1. Set timezone.
	 *   2. Read env vars (DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD).
	 *   3. Connect tới PostgreSQL "postgres" database (mặc định luôn tồn tại).
	 *   4. Check database "osms" có tồn tại không → nếu không, CREATE DATABASE osms.
	 *   5. Connect tới "osms", check schema đã init chưa.
	 *   6. Nếu categories table KHÔNG tồn tại → chạy schema.sql qua raw JDBC.
	 *   7. Verify lại tables tồn tại.
	 *   8. SpringApplication.run() bình thường.
	 *
	 * KHÔNG throw exception để app vẫn boot được nếu schema init fail
	 * (cho phép debug qua logs).
	 */
	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
		System.out.println("=================================================");
		System.out.println("[main] Starting OSMS application, timezone=" + ZoneId.systemDefault());
		System.out.println("=================================================");

		// Read env vars DIRECTLY (placeholders chưa được resolve ở đây)
		String dbHost = System.getenv("DB_HOST");
		String dbPort = System.getenv("DB_PORT");
		String dbName = System.getenv("DB_NAME");
		String dbUser = System.getenv("DB_USERNAME");
		String dbPass = System.getenv("DB_PASSWORD");

		System.out.println("[main] DB env: host=" + dbHost + ", port=" + dbPort +
				", name=" + dbName + ", user=" + dbUser +
				", pass=" + (dbPass != null ? "***SET***" : "NULL"));

		if (dbHost != null && dbUser != null && dbPass != null) {
			// Default values
			if (dbName == null || dbName.isBlank()) dbName = "osms";
			if (dbPort == null || dbPort.isBlank()) dbPort = "5432";

			// STEP 1: Connect tới "postgres" database (mặc định luôn tồn tại) để check/create target DB
			String adminUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/postgres";
			System.out.println("[main] STEP 1: Checking if database '" + dbName + "' exists...");

			try {
				Class.forName("org.postgresql.Driver");

				boolean dbExists = checkDatabaseExists(adminUrl, dbUser, dbPass, dbName);
				if (!dbExists) {
					System.out.println("[main] Database '" + dbName + "' does NOT exist. Creating...");
					createDatabase(adminUrl, dbUser, dbPass, dbName);
					System.out.println("[main] ✅ Database '" + dbName + "' created.");
				} else {
					System.out.println("[main] ✅ Database '" + dbName + "' exists.");
				}

				// STEP 2: Connect tới target database và init schema
				String url = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
				System.out.println("[main] STEP 2: Connecting to target DB: " + url);

				try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
					System.out.println("[main] ✅ Connected to '" + dbName + "'");

				if (isSchemaInitialized(conn)) {
					System.out.println("[main] ✅ categories table exists — SKIP schema init");
					System.out.println("[main] STEP 2.5: Running lightweight startup migrations...");
					runStartupMigrations(conn);
				} else {
					System.out.println("[main] categories table missing — running schema.sql");
					runSchemaSql(conn);
					// Even after fresh schema init, run migrations (idempotent ADD COLUMN IF NOT EXISTS).
					runStartupMigrations(conn);
				}
				}

			} catch (Exception e) {
				System.err.println("[main] ❌ Early DB setup FAILED: " + e.getMessage());
				e.printStackTrace();
				// KHÔNG throw - để app boot tiếp
			}
		} else {
			System.out.println("[main] DB env vars missing — skipping early schema init (likely local dev)");
		}

		// STEP 3: Tiếp tục Spring Boot
		System.out.println("[main] STEP 3: Starting SpringApplication.run()...");
		SpringApplication.run(OsmsApplication.class, args);
	}

	/**
	 * Check database có tồn tại không bằng cách query pg_database.
	 */
	private static boolean checkDatabaseExists(String adminUrl, String user, String pass, String dbName) {
		try (Connection conn = DriverManager.getConnection(adminUrl, user, pass);
			 PreparedStatement ps = conn.prepareStatement(
					 "SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = ?)")) {
			ps.setString(1, dbName);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() && rs.getBoolean(1);
			}
		} catch (Exception e) {
			System.err.println("[main] checkDatabaseExists error: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Tạo database mới. PostgreSQL KHÔNG support parameterized CREATE DATABASE.
	 * Phải validate dbName chỉ chứa safe characters trước khi concat vào SQL.
	 */
	private static void createDatabase(String adminUrl, String user, String pass, String dbName) throws Exception {
		// SECURITY: chỉ cho phép chữ cái, số, underscore
		if (!dbName.matches("^[a-zA-Z0-9_]+$")) {
			throw new IllegalArgumentException("Invalid database name: " + dbName);
		}

		try (Connection conn = DriverManager.getConnection(adminUrl, user, pass);
			 Statement stmt = conn.createStatement()) {
			stmt.executeUpdate("CREATE DATABASE \"" + dbName + "\"");
		}
	}

	/**
	 * Check categories table đã tồn tại chưa.
	 */
	private static boolean isSchemaInitialized(Connection conn) throws Exception {
		try (Statement stmt = conn.createStatement();
			 ResultSet rs = stmt.executeQuery(
					 "SELECT EXISTS (SELECT 1 FROM information_schema.tables " +
					 "WHERE table_schema = 'public' AND table_name = 'categories')")) {
			return rs.next() && rs.getBoolean(1);
		}
	}

	/**
	 * Chạy các ALTER TABLE idempotent để bổ sung cột còn thiếu.
	 *
	 * LÝ DO CẦN:
	 *   - Render DB là persistent (không bị wipe), nhưng schema.sql chỉ chạy
	 *     khi categories table CHƯA tồn tại.
	 *   - Khi entity mới thêm field, DB cũ thiếu cột → Hibernate validate fail.
	 *   - Giải pháp: chạy ADD COLUMN IF NOT EXISTS mỗi lần startup (safe, idempotent).
	 *
	 * CÁCH THÊM MIGRATION MỚI:
	 *   - Thêm 1 dòng ALTER TABLE ... ADD COLUMN IF NOT EXISTS ... vào list bên dưới.
	 *   - Migration phải idempotent (chạy nhiều lần không lỗi).
	 *   - KHÔNG drop/rename column ở đây → phải viết SQL migration riêng.
	 */
	private static void runStartupMigrations(Connection conn) {
		String[] migrations = {
				// 2026-08-06: stocktake detail page - actor + timestamp columns
				"ALTER TABLE stocktake_sessions " +
						"ADD COLUMN IF NOT EXISTS notes         TEXT, " +
						"ADD COLUMN IF NOT EXISTS started_by    UUID, " +
						"ADD COLUMN IF NOT EXISTS started_at    TIMESTAMPTZ, " +
						"ADD COLUMN IF NOT EXISTS completed_by  UUID, " +
						"ADD COLUMN IF NOT EXISTS completed_at  TIMESTAMPTZ, " +
						"ADD COLUMN IF NOT EXISTS cancelled_by  UUID, " +
						"ADD COLUMN IF NOT EXISTS cancelled_at  TIMESTAMPTZ",
				"ALTER TABLE stocktake_items ALTER COLUMN actual_quantity DROP NOT NULL",
		};

		try (Statement stmt = conn.createStatement()) {
			for (String sql : migrations) {
				try {
					stmt.execute(sql);
					System.out.println("[migrate] ✅ " + sql.substring(0, Math.min(80, sql.length())) + "...");
				} catch (Exception e) {
					System.err.println("[migrate] ❌ " + e.getMessage());
					System.err.println("[migrate] SQL: " + sql);
				}
			}
		} catch (Exception e) {
			System.err.println("[migrate] ❌ Migration runner failed: " + e.getMessage());
		}
	}

	/**
	 * Chạy schema.sql qua raw JDBC. Schema đã strip PL/pgSQL nên safe.
	 */
	private static void runSchemaSql(Connection conn) {
		try {
			var resource = OsmsApplication.class.getClassLoader().getResource("schema.sql");
			if (resource == null) {
				System.err.println("[main] ❌ schema.sql not found in classpath");
				return;
			}
			String sql = new String(resource.openStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			System.out.println("[main] Read " + sql.length() + " bytes from schema.sql");

			String[] parts = sql.split(";");
			int success = 0, errors = 0;
			try (Statement stmt = conn.createStatement()) {
				for (int i = 0; i < parts.length; i++) {
					String s = parts[i].trim();
					if (s.isEmpty() || s.startsWith("--")) continue;

					// Strip comment lines
					StringBuilder sb = new StringBuilder();
					for (String line : s.split("\n")) {
						String trimmed = line.trim();
						if (trimmed.startsWith("--")) continue;
						sb.append(line).append("\n");
					}
					String clean = sb.toString().trim();
					if (clean.isEmpty()) continue;

					try {
						stmt.execute(clean);
						success++;
					} catch (Exception e) {
						errors++;
						String preview = clean.length() > 200 ? clean.substring(0, 200) + "..." : clean;
						System.err.println("[main] ❌ stmt #" + (i + 1) + " failed: " + e.getMessage() +
								" | SQL: " + preview.replaceAll("\\s+", " "));
					}
				}
			}

			System.out.println("[main] Schema init done: " + success + " success, " + errors + " errors");

			if (isSchemaInitialized(conn)) {
				System.out.println("[main] ✅✅✅ VERIFIED: categories table now exists!");
			} else {
				System.err.println("[main] ❌ categories table STILL missing after init");
			}
		} catch (Exception e) {
			System.err.println("[main] ❌ Schema init failed: " + e.getMessage());
			e.printStackTrace();
		}
	}
}