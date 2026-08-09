package fu.osms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class OsmsApplication {

	/**
	 * MAIN METHOD — chạy schema init TRƯỚC Spring Boot (fallback cho SchemaEnvironmentPostProcessor).
	 *
	 * Lý do fallback ở đây:
	 *   1. SchemaEnvironmentPostProcessor (META-INF/spring.factories) CHỈ chạy nếu JAR
	 *      có file spring.factories (có thể bị Maven plugin skip nếu empty).
	 *   2. spring.sql.init cần Spring context sẵn sàng → chạy SAU Hibernate JPA
	 *      EntityManagerFactory được tạo (không phải lúc nào cũng TRƯỚC Hibernate validate).
	 *   3. main() chạy TRƯỚC tất cả → 100% chắc chắn chạy TRƯỚC Spring context.
	 *
	 * Flow:
	 *   1. Set timezone
	 *   2. Check env vars (DB_HOST, DB_USERNAME, DB_PASSWORD)
	 *   3. Nếu có → connect PostgreSQL → check categories table
	 *   4. Nếu categories KHÔNG tồn tại → chạy schema.sql raw JDBC
	 *   5. Tiếp tục SpringApplication.run() bình thường
	 *
	 * KHÔNG throw exception để app vẫn boot được nếu schema init fail
	 * (cho phép debug qua logs).
	 */
	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
		System.out.println("[main] Starting OSMS application, timezone=" + ZoneId.systemDefault());

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
			// Default DB_NAME = "OSMS" nếu không có
			if (dbName == null || dbName.isBlank()) dbName = "OSMS";
			if (dbPort == null || dbPort.isBlank()) dbPort = "5432";

			String url = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
			System.out.println("[main] Attempting early schema init via raw JDBC: " + url);

			try {
				Class.forName("org.postgresql.Driver");
				try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
					System.out.println("[main] ✅ DB connected");

					if (isSchemaInitialized(conn)) {
						System.out.println("[main] ✅ Categories table exists — SKIP schema init");
					} else {
						System.out.println("[main] Categories table missing — running schema.sql");
						runSchemaSql(conn);
					}
				}
			} catch (Exception e) {
				System.err.println("[main] ❌ Early schema init FAILED: " + e.getMessage());
				e.printStackTrace();
				// KHÔNG throw - để app boot tiếp
			}
		} else {
			System.out.println("[main] DB env vars missing — skipping early schema init (likely local dev)");
		}

		// Tiếp tục Spring Boot
		SpringApplication.run(OsmsApplication.class, args);
	}

	private static boolean isSchemaInitialized(Connection conn) throws Exception {
		try (Statement stmt = conn.createStatement();
			 var rs = stmt.executeQuery(
					 "SELECT EXISTS (SELECT 1 FROM information_schema.tables " +
					 "WHERE table_schema = 'public' AND table_name = 'categories')")) {
			return rs.next() && rs.getBoolean(1);
		}
	}

	private static void runSchemaSql(Connection conn) {
		try {
			// Đọc schema.sql từ classpath
			var resource = OsmsApplication.class.getClassLoader().getResource("schema.sql");
			if (resource == null) {
				System.err.println("[main] ❌ schema.sql not found in classpath");
				return;
			}
			String sql = new String(resource.openStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			System.out.println("[main] Read " + sql.length() + " bytes from schema.sql");

			// Split theo ";"
			String[] parts = sql.split(";");
			int success = 0, errors = 0;
			try (Statement stmt = conn.createStatement()) {
				for (int i = 0; i < parts.length; i++) {
					String s = parts[i].trim();
					if (s.isEmpty() || s.startsWith("--")) continue;
					// Skip comment lines
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
						String preview = clean.length() > 150 ? clean.substring(0, 150) + "..." : clean;
						System.err.println("[main] ❌ stmt #" + (i+1) + " failed: " + e.getMessage() +
								" | SQL: " + preview.replaceAll("\\s+", " "));
					}
				}
			}

			System.out.println("[main] Schema init done: " + success + " success, " + errors + " errors");

			// Verify lại
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
