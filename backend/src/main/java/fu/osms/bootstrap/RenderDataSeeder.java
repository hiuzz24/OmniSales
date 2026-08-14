package fu.osms.bootstrap;

import fu.osms.auth.entity.Role;
import fu.osms.auth.entity.User;
import fu.osms.auth.entity.UserRole;
import fu.osms.auth.enums.UserStatus;
import fu.osms.auth.repository.RoleRepository;
import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.enums.CategoryStatus;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.address.entity.Country;
import fu.osms.address.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Render.com environment data seeder.
 *
 * Run automatically on every Render deployment via Spring Boot's
 * {@link ApplicationReadyEvent} — <b>only when the {@code render} profile
 * is active</b> (enforced by {@code @Profile("render")}).
 *
 * <p>Runs AFTER Tomcat binds port → app ready first, seeder second (async).
 * This avoids Render's port-scan-timeout when seed takes >30s.
 *
 * <h3>Idempotency</h3>
 * This seeder is fully idempotent. It checks whether each entity already exists
 * before inserting, so re-running on subsequent deploys is safe and will not
 * create duplicate records.
 *
 * <h3>What gets seeded</h3>
 * <ul>
 *   <li>Roles: SYSTEM_ADMIN, OWNER, OPERATIONS, SALES (khớp schema-postgresql.sql)</li>
 *   <li>Admin user: credentials read from {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD}
 *       environment variables (defaults to {@code admin@osms.local / ChangeMe123!}).
 *       Password is BCrypt-encoded before storage.</li>
 *   <li>Countries: VN, SG, TH, US, MY, ID (most-relevant Southeast Asian + US markets)</li>
 *   <li>Categories: Fashion, Electronics, Home & Living (seed structure, no products)</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * No manual action required. After pushing to GitHub and triggering a Render
 * Blueprint deploy, check the {@code api-osms} logs for a line like:
 * <pre>[seeder] ✅ Seeded admin user: admin@osms.local</pre>
 */
@Slf4j
@Component
@Profile("render")
@RequiredArgsConstructor
public class RenderDataSeeder {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final CategoryRepository categoryRepository;
    private final CountryRepository countryRepository;
    private final PasswordEncoder passwordEncoder;

    // -------------------------------------------------------------------------
    // Master data — safe to re-insert (ON CONFLICT DO NOTHING semantics)
    // -------------------------------------------------------------------------

    private static final List<Map<String, String>> SEED_ROLES = List.of(
            // CHỈ CÓ 4 ROLES (khớp với schema-postgresql.sql):
            //   - SYSTEM_ADMIN: dùng trong @PreAuthorize("hasRole('SYSTEM_ADMIN')")
            //   - OWNER: business owner full access
            //   - OPERATIONS: operations staff
            //   - SALES: sales staff
            // KHÔNG seed thêm 'ADMIN', 'STAFF', 'WAREHOUSE' vì schema gốc không có.
            // Nếu cần thêm role mới, sửa CẢ schema-postgresql.sql VÀ seeder.
            Map.of("name", "SYSTEM_ADMIN", "description", "System administrator — full access to all modules (used by @PreAuthorize)"),
            Map.of("name", "OWNER",        "description", "Business owner — full ownership with billing and settings access"),
            Map.of("name", "OPERATIONS",   "description", "Operations staff — inventory and channel sync"),
            Map.of("name", "SALES",        "description", "Sales staff — orders and customer management")
    );

    private static final List<Map<String, String>> SEED_COUNTRIES = List.of(
            Map.of("code", "VN", "name", "Vietnam",       "flag", "🇻🇳"),
            Map.of("code", "SG", "name", "Singapore",     "flag", "🇸🇬"),
            Map.of("code", "TH", "name", "Thailand",      "flag", "🇹🇭"),
            Map.of("code", "US", "name", "United States", "flag", "🇺🇸"),
            Map.of("code", "MY", "name", "Malaysia",     "flag", "🇲🇾"),
            Map.of("code", "ID", "name", "Indonesia",     "flag", "🇮🇩"),
            Map.of("code", "PH", "name", "Philippines",   "flag", "🇵🇭"),
            Map.of("code", "KH", "name", "Cambodia",     "flag", "🇰🇭")
    );

    private static final List<Map<String, String>> SEED_CATEGORIES = List.of(
            Map.of("name", "Fashion",      "slug", "fashion",       "sortOrder", "1"),
            Map.of("name", "Electronics",  "slug", "electronics",  "sortOrder", "2"),
            Map.of("name", "Home & Living","slug", "home-living",   "sortOrder", "3"),
            Map.of("name", "Beauty",       "slug", "beauty",        "sortOrder", "4"),
            Map.of("name", "Sports",       "slug", "sports",        "sortOrder", "5")
    );

    // -------------------------------------------------------------------------
    // Admin user credentials — read from env; fall back to safe defaults.
    // IMPORTANT: Change ADMIN_PASSWORD in Render dashboard after first login!
    // -------------------------------------------------------------------------

    private static final String DEFAULT_ADMIN_EMAIL    = "admin@osms.local";
    private static final String DEFAULT_ADMIN_PASSWORD = "ChangeMe123!";
    private static final String DEFAULT_ADMIN_FULL_NAME = "Administrator";

    private static final String DEFAULT_OWNER_EMAIL    = "owner1@osms.local";
    private static final String DEFAULT_OWNER_PASSWORD = "nguyentheduy2004";
    private static final String DEFAULT_OWNER_FULL_NAME = "Owner One";

    // -------------------------------------------------------------------------
    // ApplicationReadyEvent listener — fires AFTER Tomcat binds port and app is
    // ready to accept traffic. @Async ensures the seeding runs in a background
    // thread so it does NOT block the readiness probe.
    // -------------------------------------------------------------------------

    @Async
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        log.info("[seeder] Starting Render environment data seeder (async)...");

        // Each seed step is wrapped in try-catch so one failure doesn't kill the app.
        // This is critical for Render free tier: if a table is missing (DDL race condition),
        // the app must still boot so we can diagnose via logs / DB.

        try {
            seedRoles();
        } catch (Exception e) {
            log.error("[seeder] ❌ seedRoles failed: {}", e.getMessage(), e);
        }

        try {
            seedAdminUser();
        } catch (Exception e) {
            log.error("[seeder] ❌ seedAdminUser failed: {}", e.getMessage(), e);
        }

        try {
            seedOwnerUser();
        } catch (Exception e) {
            log.error("[seeder] ❌ seedOwnerUser failed: {}", e.getMessage(), e);
        }

        try {
            seedCountries();
        } catch (Exception e) {
            log.error("[seeder] ❌ seedCountries failed: {}", e.getMessage(), e);
        }

        try {
            seedCategories();
        } catch (Exception e) {
            log.error("[seeder] ❌ seedCategories failed: {}", e.getMessage(), e);
        }

        log.info("[seeder] ✅ Seeding run complete (check above for any errors).");
    }

    // -------------------------------------------------------------------------
    // Individual seed methods — each is idempotent (skip if already exists)
    // -------------------------------------------------------------------------

    private void seedRoles() {
        for (Map<String, String> r : SEED_ROLES) {
            String name = r.get("name");
            if (roleRepository.existsByName(name)) {
                log.debug("[seeder] Role '{}' already exists, skipping.", name);
                continue;
            }
            Role role = Role.builder()
                    .name(name)
                    .description(r.get("description"))
                    .build();
            roleRepository.save(role);
            log.info("[seeder] ✅ Seeded role: {}", name);
        }
    }

    private void seedAdminUser() {
        String email    = envOr("ADMIN_EMAIL",    DEFAULT_ADMIN_EMAIL);
        String password = envOr("ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD);
        String fullName = DEFAULT_ADMIN_FULL_NAME;

        // Skip if admin already exists (e.g. created via invite or manual DB entry)
        if (userRepository.existsByEmail(email)) {
            log.info("[seeder] Admin user '{}' already exists, skipping.", email);
            ensureUserHasSystemAdminRole(email);
            return;
        }

        // Look up the SYSTEM_ADMIN role (must exist — seeded above and in schema-postgresql.sql)
        // LÝ DO dùng SYSTEM_ADMIN thay vì ADMIN:
        //   - schema-postgresql.sql chỉ seed 4 roles: SYSTEM_ADMIN, OWNER, OPERATIONS, SALES.
        //   - @PreAuthorize("hasRole('SYSTEM_ADMIN')") trong code yêu cầu authority ROLE_SYSTEM_ADMIN.
        //   - User phải có role SYSTEM_ADMIN để truy cập được API admin.
        Role adminRole = roleRepository.findByName("SYSTEM_ADMIN")
                .orElseThrow(() -> new IllegalStateException(
                        "[seeder] SYSTEM_ADMIN role not found — ensure seedRoles() runs first"));

        // Build user entity (UserStatus defaults to INACTIVE via @Builder.Default;
        // set explicitly to ACTIVE so the admin can log in immediately after seeding)
        User admin = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(fullName)
                .status(UserStatus.ACTIVE)
                .emailVerifiedAt(OffsetDateTime.now())  // skip email verification step on Render
                .build();

        User savedAdmin = userRepository.save(admin);

        // Create the join-table entry in user_roles for ADMIN role
        UserRole userRole = UserRole.builder()
                .user(savedAdmin)
                .role(adminRole)
                .grantedAt(OffsetDateTime.now())
                .build();
        userRoleRepository.save(userRole);

        // Also assign SYSTEM_ADMIN role so user can access @PreAuthorize endpoints
        // that require ROLE_SYSTEM_ADMIN (Spring Security strips ROLE_ prefix).
        ensureUserHasSystemAdminRole(email);

        log.warn("[seeder] ╔══════════════════════════════════════════════════════════╗");
        log.warn("[seeder] ║  ✅ ADMIN USER CREATED — CHANGE PASSWORD AFTER LOGIN!     ║");
        log.warn("[seeder] ║  Email    : {}                                       ║", email);
        log.warn("[seeder] ║  Password : {} (from ADMIN_PASSWORD env)           ║", password);
        log.warn("[seeder] ╚══════════════════════════════════════════════════════════╝");
    }

    private void seedOwnerUser() {
        String email    = envOr("OWNER_EMAIL",    DEFAULT_OWNER_EMAIL);
        String password = envOr("OWNER_PASSWORD", DEFAULT_OWNER_PASSWORD);
        String fullName = DEFAULT_OWNER_FULL_NAME;

        if (userRepository.existsByEmail(email)) {
            log.info("[seeder] Owner user '{}' already exists, skipping.", email);
            return;
        }

        Role ownerRole = roleRepository.findByName("OWNER")
                .orElseThrow(() -> new IllegalStateException(
                        "[seeder] OWNER role not found — ensure seedRoles() runs first"));

        User owner = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(fullName)
                .status(UserStatus.ACTIVE)
                .emailVerifiedAt(OffsetDateTime.now())
                .build();

        User savedOwner = userRepository.save(owner);

        UserRole userRole = UserRole.builder()
                .user(savedOwner)
                .role(ownerRole)
                .grantedAt(OffsetDateTime.now())
                .build();
        userRoleRepository.save(userRole);

        log.warn("[seeder] ╔══════════════════════════════════════════════════════════╗");
        log.warn("[seeder] ║  ✅ OWNER USER CREATED — CHANGE PASSWORD AFTER LOGIN!     ║");
        log.warn("[seeder] ║  Email    : {}                                       ║", email);
        log.warn("[seeder] ║  Password : {} (from OWNER_PASSWORD env)           ║", password);
        log.warn("[seeder] ╚══════════════════════════════════════════════════════════╝");
    }

    /**
     * Đảm bảo user (admin owner) có role SYSTEM_ADMIN.
     *
     * LÝ DO CẦN:
     *   - @PreAuthorize("hasRole('SYSTEM_ADMIN')") check authority = "ROLE_SYSTEM_ADMIN"
     *     (Spring Security tự thêm tiền tố "ROLE_").
     *   - Database CHỈ có role "SYSTEM_ADMIN", "OWNER", "OPERATIONS", "SALES"
     *     (xem schema-postgresql.sql).
     *   - User admin phải có role SYSTEM_ADMIN để truy cập API admin.
     *   - Method này gán role SYSTEM_ADMIN cho admin/owner user nếu chưa có.
     *     Idempotent — chạy nhiều lần không lỗi.
     */
    private void ensureUserHasSystemAdminRole(String email) {
        try {
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                log.debug("[seeder] User '{}' not found when ensuring SYSTEM_ADMIN role", email);
                return;
            }

            Role systemAdminRole = roleRepository.findByName("SYSTEM_ADMIN")
                    .orElseGet(() -> {
                        log.warn("[seeder] SYSTEM_ADMIN role missing — creating now");
                        Role newRole = Role.builder()
                                .name("SYSTEM_ADMIN")
                                .description("System administrator alias — auto-created for legacy data")
                                .build();
                        return roleRepository.save(newRole);
                    });

            // Check existing assignment
            boolean alreadyHas = userRoleRepository
                    .findByUserIdAndRoleId(user.getId(), systemAdminRole.getId())
                    .isPresent();
            if (alreadyHas) {
                log.debug("[seeder] User '{}' already has SYSTEM_ADMIN role", email);
                return;
            }

            UserRole userRole = UserRole.builder()
                    .user(user)
                    .role(systemAdminRole)
                    .grantedAt(OffsetDateTime.now())
                    .build();
            userRoleRepository.save(userRole);
            log.info("[seeder] ✅ Granted SYSTEM_ADMIN role to user '{}'", email);
        } catch (Exception e) {
            log.error("[seeder] Failed to ensure SYSTEM_ADMIN role for '{}': {}", email, e.getMessage());
        }
    }

    private void seedCountries() {
        for (Map<String, String> c : SEED_COUNTRIES) {
            if (countryRepository.findByCode(c.get("code")).isPresent()) {
                log.debug("[seeder] Country '{}' already exists, skipping.", c.get("code"));
                continue;
            }
            Country country = Country.builder()
                    .code(c.get("code"))
                    .name(c.get("name"))
                    .flagEmoji(c.get("flag"))
                    .build();
            countryRepository.save(country);
            log.info("[seeder] ✅ Seeded country: {} ({})", c.get("name"), c.get("code"));
        }
    }

    private void seedCategories() {
        for (Map<String, String> cat : SEED_CATEGORIES) {
            if (categoryRepository.existsBySlug(cat.get("slug"))) {
                log.debug("[seeder] Category slug '{}' already exists, skipping.", cat.get("slug"));
                continue;
            }
            Category category = Category.builder()
                    .name(cat.get("name"))
                    .slug(cat.get("slug"))
                    .sortOrder(Integer.parseInt(cat.get("sortOrder")))
                    .status(CategoryStatus.ACTIVE)
                    .build();
            categoryRepository.save(category);
            log.info("[seeder] ✅ Seeded category: {}", cat.get("name"));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Read an environment variable, returning {@code defaultValue} if the
     * variable is unset or blank.
     */
    private static String envOr(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
