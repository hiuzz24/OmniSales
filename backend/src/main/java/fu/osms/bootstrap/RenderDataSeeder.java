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
import fu.osms.address.entity.Country;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
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
 * {@link CommandLineRunner} mechanism — <b>only when the {@code render} profile
 * is active</b> (enforced by {@code @Profile("render")}).
 *
 * <h3>Idempotency</h3>
 * This seeder is fully idempotent. It checks whether each entity already exists
 * before inserting, so re-running on subsequent deploys is safe and will not
 * create duplicate records.
 *
 * <h3>What gets seeded</h3>
 * <ul>
 *   <li>Roles: ADMIN, STAFF, WAREHOUSE (default set)</li>
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
public class RenderDataSeeder implements CommandLineRunner {

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
            Map.of("name", "ADMIN",      "description", "System administrator — full access to all modules"),
            Map.of("name", "STAFF",      "description", "Regular staff — orders, customers, products"),
            Map.of("name", "WAREHOUSE",  "description", "Warehouse keeper — stock, transfers, stocktakes"),
            Map.of("name", "SALES",      "description", "Sales staff — orders and customer management"),
            Map.of("name", "OPERATIONS", "description", "Operations staff — inventory and channel sync"),
            Map.of("name", "OWNER",      "description", "Business owner — full ownership with billing and settings access")
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
    // CommandLineRunner — Spring calls this once after the application context
    // is fully initialised.
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[seeder] Starting Render environment data seeder...");

        seedRoles();
        seedAdminUser();
        seedOwnerUser();
        seedCountries();
        seedCategories();

        log.info("[seeder] ✅ Seeding complete.");
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
            return;
        }

        // Look up the ADMIN role (must exist — seeded above)
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException(
                        "[seeder] ADMIN role not found — ensure seedRoles() runs first"));

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

        // Create the join-table entry in user_roles
        UserRole userRole = UserRole.builder()
                .user(savedAdmin)
                .role(adminRole)
                .grantedAt(OffsetDateTime.now())
                .build();
        userRoleRepository.save(userRole);

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
