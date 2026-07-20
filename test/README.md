# OmniSales Test Suite

## Overview

```
test/
├── api/           # Playwright API tests
├── e2e/           # Playwright E2E (browser) tests
├── playwright.config.js
└── package.json
```

- **API tests** — gọi trực tiếp endpoint backend qua HTTP (không cần trình duyệt).
- **E2E tests** — mô phỏng hành vi người dùng thật trên trình duyệt Chromium.

---

## Prerequisites

### Software required

| Software | Phiên bản tối thiểu | Ghi chú |
|----------|---------------------|---------|
| Node.js  | 18.x trở lên        | `node -v` |
| Java     | 17+                 | Cần cho backend unit test |
| Maven    | 3.8+                | Cần cho backend unit test |
| Git      | bất kỳ              | Tùy chọn |

### Backend & Frontend

Playwright tests giao tiếp với ứng dụng thật, nên cần khởi động trước:

```bash
# Terminal 1 — Backend (cổng 8080)
cd OmniSales/backend
mvn spring-boot:run

# Terminal 2 — Frontend (cổng 5174)
cd OmniSales/frontend
npm run dev
```

---

## Setup (lần đầu tiên trên máy mới)

### 1. Cài npm dependencies

```bash
cd OmniSales/test
npm install
```

### 2. Cài Playwright browsers

```bash
npx playwright install chromium
```

> Nếu gặp lỗi network, thử:

```bash
# Windows (PowerShell)
npx playwright install --force --with-deps chromium

# Linux/macOS
npx playwright install --force --with-deps
```

### 3. Cấu hình biến môi trường (tùy chọn)

Mặc định tests sử dụng:

```bash
FRONTEND_URL=http://localhost:5174   # frontend base URL cho e2e
API_BASE=http://localhost:8080/api    # backend base URL cho api
```

Tạo file `.env` nếu cần ghi đè:

```bash
# OmniSales/test/.env
FRONTEND_URL=http://localhost:5174
API_BASE=http://localhost:8080/api
```

---

## Chạy Tests

### Playwright — Tất cả tests

```bash
cd OmniSales/test
npx playwright test
```

### Playwright — API tests (không mở trình duyệt)

```bash
npx playwright test api/ --project=api
```

### Playwright — E2E tests (trình duyệt Chromium)

```bash
npx playwright test e2e/ --project=chromium
```

### Playwright — Một file cụ thể

```bash
npx playwright test e2e/auth.spec.js
```

### Playwright — Với UI tương tác (headed)

```bash
npx playwright test e2e/ --project=chromium --headed
```

### Playwright — Xem report HTML

```bash
npx playwright show-report
```

### Backend Unit Tests (Spring Boot)

```bash
cd OmniSales/backend
mvn test
```

### Backend — Chạy một class test cụ thể

```bash
mvn test -Dtest=AuthServiceImplTest
```

### Backend — Chạy với coverage report

```bash
mvn test jacoco:report
# Report tại: backend/target/site/jacoco/index.html
```

### Backend Integration Tests (Spring Boot Failsafe)

> Integration Tests (IT) chạy với Spring context đầy đủ (`@SpringBootTest`),
> kết nối tới PostgreSQL `osms_it` thật, dùng JWT thật, mock các dịch vụ
> ngoài (Gmail, RabbitMQ, RestCountries). Có 3 loại: full-stack controller
> IT, repository IT (custom JPQL), và multi-step flow + security IT.

#### Chuẩn bị Database `osms_it`

Yêu cầu: PostgreSQL đang chạy, `backend/.env` có `DB_PASSWORD`.

```powershell
# Cách 1 — dùng script PowerShell (Windows)
cd OmniSales/backend
powershell -ExecutionPolicy Bypass -File src/test/resources/db-init.ps1
```

```bash
# Cách 2 — dùng shell script (Linux/macOS)
cd OmniSales/backend
./src/test/resources/db-init.sh
```

Script sẽ:
1. Tìm `psql.exe` (hoặc `psql`) trong PATH / Program Files
2. Đọc `DB_PASSWORD` từ `backend/.env`
3. Drop + recreate database `osms_it`
4. Apply `backend/hibernate-schema.sql` để có schema + master data

#### Chạy toàn bộ IT

```bash
cd OmniSales/backend
mvn verify
```

Hoặc dùng `run-it.ps1` (tự reset DB rồi chạy Failsafe):

```powershell
powershell -ExecutionPolicy Bypass -File src/test/resources/run-it.ps1
```

> Lưu ý: Sau khi SecurityIT (SEC-04) chạy, một số user có thể bị lock.
> Reset trước khi chạy lại bằng:
> ```sql
> UPDATE users SET failed_login_attempts=0, locked_until=NULL, status='ACTIVE';
> ```

#### Chạy một class IT cụ thể

```bash
mvn verify -Dit.test=CustomerControllerFullStackIT
```

#### Chạy nhiều class IT

```bash
mvn verify "-Dit.test=CustomerControllerFullStackIT,OrderControllerFullStackIT,ApiFlowsIT,SecurityIT"
```

#### Cấu trúc file IT

| Loại | Pattern file | Mô tả |
|------|--------------|-------|
| Full-stack controller IT | `*ControllerFullStackIT.java` | `@SpringBootTest` + `TestRestTemplate` + JWT thật |
| Full-stack auth/user | `*ControllerFullStackIT.java` | Auth, User, Customer, Order |
| Repository IT (custom JPQL) | `*RepositoryIT.java` | `@SpringBootTest` + JPA repository thật |
| Multi-step flow | `flow/ApiFlowsIT.java` | FLOW-01..04 theo L3 spec |
| Security / OWASP | `security/SecurityIT.java` | SEC-01..08 theo L3 spec |
| Controller slice (cũ) | `*ControllerIT.java` | `@WebMvcTest`, không DB |

#### Helpers có sẵn

- `IntegrationTestBase` — boot Spring context, login cache, truncate DB giữa các test
- `BaseFullStackIT` — thêm helpers HTTP (`getForJson`, `postForJson`, ...)
- `ControllerSliceITBase` — base cho `@WebMvcTest` slice
- `TestDataFactory` — factory tạo entity với giá trị unique
- `JwtTestUtils` — mint JWT trực tiếp (kể cả expired)
- `IntegrationTestCleanupHelper` — `TRUNCATE` các bảng transactional
- `TestExternalServicesConfig` — mock `JavaMailSender`, Rabbit, RestCountries

---

## Cấu trúc & Conventions Code

### Playwright (JavaScript)

#### Đặt tên

- Test file: `<feature>.spec.js` (snake_case)
- Test suite: `test.describe('Auth API Tests', () => {`
- Test case: `test('<mô tả hành vi bằng tiếng Việt>', async ({ request }) => {`
- Mô tả rõ ràng, động từ hành động: `Login successfully`, `Shows validation error when ...`

#### Cấu trúc test

```javascript
// ✅ ĐÚNG — mỗi test là một kịch bản độc lập
test.describe('Auth API Tests', () => {
  test.beforeEach(async ({ request }) => {
    // Setup chung cho suite
  });

  test('Login successfully', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/login`, {
      data: { email: 'user@test.com', password: 'Pass123!' },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });
});
```

```javascript
// ❌ SAI — không dùng describe lồng nhau trong api test
test.describe('Auth API Tests', () => {
  test.describe('Login', () => {  // nested describe không cần thiết
    // ...
  });
});
```

#### Locator & Assertions

```javascript
// ✅ Luôn dùng data-testid hoặc id cố định
await expect(page.locator('#login-email')).toBeVisible();

// ✅ Dùng .first() khi selector match nhiều phần tử
await expect(page.locator('[role="alert"]').first()).toBeVisible();

// ✅ Wait chủ động thay vì sleep
await Promise.all([
  page.waitForURL('**/dashboard', { timeout: 8000 }),
  page.locator('#login-submit-btn').click(),
]);

// ✅ Kiểm tra nội dung text
const error = await page.locator('#email-error').textContent();
expect(error).toContain('Vui lòng nhập');

// ❌ Tránh hardcoded sleep
await page.waitForTimeout(2000); // Chỉ dùng khi không có cách nào khác
```

#### HTTP Request API tests

```javascript
// ✅
test('Login with invalid credentials returns 401', async ({ request }) => {
  const response = await request.post(`${API_BASE}/auth/login`, {
    data: { email: 'wrong@test.com', password: 'WrongPass1!' },
  });

  expect(response.status()).toBe(401);
  const body = await response.json();
  expect(body.success).toBe(false);
});
```

#### Test Isolation

```javascript
// ✅ Mỗi test tự chịu trách nhiệm dữ liệu của mình
// Không chia sẻ state giữa các test
// Dùng unique email cho mỗi test nếu tạo dữ liệu

// ✅ beforeEach / afterEach cho cleanup
test.beforeEach(async ({ page }) => {
  await page.goto('/login');
});
```

#### Import & require

```javascript
// Luôn dùng CommonJS require (phù hợp với @playwright/test)
// KHÔNG dùng ES module import
const { test, expect } = require('@playwright/test');
const API_BASE = process.env.API_BASE || 'http://localhost:8080/api';
```

---

### Backend Unit Tests (Java / JUnit 5)

#### Đặt tên

- Test class: `<Service>ImplTest.java` (PascalCase)
- Test method: `method_Scenario_ExpectedBehavior()`
- Package: giữ nguyên package gốc của service, đặt trong `src/test/java`

#### Cấu trúc test

```java
// ✅ ĐÚNG — dùng @Nested @DisplayName để nhóm test case có liên quan
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private AuthServiceImpl authService;

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void login_Success() {
            // Arrange
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

            // Act
            TokenPairDTO result = authService.login(request);

            // Assert
            assertThat(result.getAccessToken()).isNotNull();
            assertThat(result.getUser().getEmail()).isEqualTo("test@example.com");
        }
    }
}
```

```java
// ❌ SAI — tất cả test methods ở top-level, không nhóm
class AuthServiceImplTest {
    @Test void testLoginSuccess() { ... }
    @Test void testLoginFail() { ... }
    // Không rõ ràng, khó đọc
}
```

#### Arrange – Act – Assert (AAA)

```java
// ✅ Rõ ràng từng phase
@Test
void login_EmailNotFound() {
    // Arrange
    when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> authService.login(loginRequest))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
}
```

#### Mocking

```java
// ✅ Dùng any() khi giá trị không quan trọng cho assertion
when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

// ✅ Verify số lần gọi khi cần
verify(userRepository, times(1)).save(any(User.class));
verify(refreshTokenRepository, never()).delete(any()); // không gọi
```

#### Assertion

```java
// ✅ Dùng AssertJ (assertThat) — fluent, dễ đọc
assertThat(result).isNotNull();
assertThat(result.getAccessToken()).isEqualTo("token");
assertThatThrownBy(() -> service.method()).isInstanceOf(Exception.class);

// ❌ Tránh JUnit assertions quá mức
assertEquals(expected, actual); // chỉ dùng khi AssertJ không thể
```

#### Import organization

```java
// Thứ tự: java stdlib → third-party → project
import java.util.*;
import java.time.*;

import org.junit.jupiter.api.*;
import org.mockito.*;

import fu.osms.auth.repository.*;
import fu.osms.common.exception.*;
```

#### Test data

```java
// ✅ Dùng @BeforeEach để setup object chung
// ✅ Builder pattern cho test data
private User testUser;

@BeforeEach
void setUp() {
    testUser = User.builder()
            .id(UUID.randomUUID())
            .email("test@example.com")
            .status(UserStatus.ACTIVE)
            .build();
}
```

---

## Troubleshooting

### Lỗi `playwright` command not found

```bash
npm install
npx playwright --version
```

### Lỗi `connect ECONNREFUSED` khi chạy e2e

Frontend chưa chạy. Khởi động frontend trước:

```bash
cd OmniSales/frontend
npm run dev
```

### Lỗi `connect ECONNREFUSED` khi chạy api

Backend chưa chạy. Khởi động backend trước:

```bash
cd OmniSales/backend
mvn spring-boot:run
```

### Lỗi `401 Unauthorized` trong api tests

Token hết hạn. Kiểm tra credentials trong test files có đúng với dữ liệu trong database.

### Lỗi `playwright: command not found` trong CI

```bash
npm ci
npx playwright install chromium --with-deps
npx playwright test
```

---

## Test data cleanup

Mục tiêu: **DB phải sạch** sau khi tests chạy. Hai lớp bảo vệ:

### 1. Sau mỗi test — `afterEach` cleanup

Mỗi spec phải đăng ký `afterEach` xóa dữ liệu nó tạo ra. Pattern mẫu (xem `test/api/channel/channel.spec.js` để tham khảo đầy đủ):

```javascript
const { createTestChannel, deleteTestChannel } = require('../../utils/channel-helpers');

test.describe('Channel API Tests', () => {
  let createdChannelIds = [];

  test.afterEach(async ({ request, managerHeaders }) => {
    if (!createdChannelIds.length) return;
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    for (const id of createdChannelIds.splice(0)) {
      await deleteTestChannel(request, authToken, id);
    }
  });

  test('create a channel', async ({ request, managerHeaders }) => {
    // ...
    createdChannelIds.push(created.id);
  });
});
```

Danh sách helpers có sẵn trong `test/utils/`:

| Helper                  | Chức năng                              | Cleanup API                                   |
|-------------------------|----------------------------------------|------------------------------------------------|
| product-helpers         | `createTestProduct`, `deleteTestProduct` | `DELETE /api/products/{id}/delete`            |
| customer-helpers        | `createTestCustomer`, `deleteTestCustomer` | `DELETE /api/customers/{id}`                 |
| order-helpers           | `createTestOrder`, `deleteTestOrder` (cancel) | `POST /api/orders/{id}/cancel`          |
| user-helpers            | `createTestUser`, `cleanupTestUser`     | `DELETE /api/users/{id}`                       |
| category (in inventory-helpers) | `createTestCategory`, `deleteTestCategory` | `DELETE /api/categories/{id}`         |
| channel-helpers         | `createTestChannel`, `deleteTestChannel` | `DELETE /api/channels/{id}`                  |
| supplier-helpers        | `createTestSupplier`, `deactivateTestSupplier` | `PATCH /api/suppliers/{id}/status` |
| warehouse-helpers       | `createTestReceipt/Delivery/Stocktake/Transfer` + `cleanupTestData(type,id)` | mixed |

**Supplier không có DELETE API.** Cleanup là PATCH `/api/suppliers/{id}/status` với `{ isActive: false }` (đặt tên là "deactivate").

### 2. Cuối mỗi lần `npm test` — `globalTeardown`

`test/global-teardown.js` chạy tự động sau khi tất cả workers kết thúc. Nó thực hiện hai lớp cleanup:

1. **API cleanup (luôn chạy)** — gọi `cleanupAllTestData(request, token)` trong `utils/cleanup-helpers.js` để xóa các thực thể có DELETE API.
2. **SQL cleanup (opt-in)** — chỉ chạy khi `TEST_DB_SQL_CLEANUP=true`. Xóa các thực thể không có DELETE API (inventory_transactions, stocktakes, transfers, suppliers còn sót) bằng câu SQL trực tiếp.

⚠️ **Cảnh báo:** Đừng bao giờ bật `TEST_DB_SQL_CLEANUP=true` trên DB shared với dev. Chỉ bật khi chạy trên DB test riêng.

### 3. Scripts thủ công

Khi DB đã đầy rác từ nhiều lần chạy test, dùng một trong các scripts:

```bash
# Xem bao nhiêu dòng test còn sót lại
npm run db:dry-run          # = node scripts/dry-run-counts.js

# Backup DB trước khi xóa
npm run db:backup           # = node scripts/backup-db.js
                            # Ghi file SQL vào backend/backups/

# Xóa rác (kèm confirm)
npm run db:delete-test      # = node scripts/delete-test-data.js
                            # Cờ: --dry-run, --yes, --validate

# Cleanup nhẹ qua API (chạy sau khi test xong)
npm run test:clean          # = node scripts/cleanup-test-data.js
```

### 4. KHÔNG dùng TRUNCATE

Không bao giờ dùng `TRUNCATE` toàn DB. Hai lý do:

1. Sẽ xóa luôn master data (admin, manager, warehouses, countries).
2. Triggers `trg_*_immutable` sẽ chặn xóa tables bảo toàn như `inventory_transactions` và `audit_logs` — nên TRUNCATE không qua RESTRICT cũng không ăn thua.

Các scripts có sẵn (`scripts/delete-test-data.js`, `cleanup-helpers.js`) chỉ xóa các dòng có marker test rõ ràng (`TEST-`, `Test`, `TestSup`, etc.).

---

## Quick Reference

| Lệnh | Mô tả |
|------|-------|
| `npm install` | Cài dependencies |
| `npx playwright install chromium` | Cài trình duyệt Chromium |
| `npx playwright test` | Chạy tất cả Playwright tests |
| `npx playwright test api/` | Chỉ API tests |
| `npx playwright test e2e/ --project=chromium` | Chỉ E2E tests |
| `npx playwright test --headed` | Chạy với cửa sổ trình duyệt |
| `npx playwright show-report` | Mở HTML report |
| `mvn test` | Chạy backend unit tests |
| `mvn test -Dtest=ClassName` | Chạy một class test cụ thể (Surefire) |
| `mvn verify` | Chạy toàn bộ Integration Test (Failsafe) |
| `mvn verify -Dit.test=ClassName` | Chạy một IT class cụ thể |
| `powershell -ExecutionPolicy Bypass -File src/test/resources/db-init.ps1` | Reset + seed DB `osms_it` |
| `powershell -ExecutionPolicy Bypass -File src/test/resources/run-it.ps1` | Reset DB + chạy full IT |
| `npm run test:clean` | Dọn rác qua API (sau khi test) |
| `npm run db:backup` | Backup DB ra file SQL |
| `npm run db:dry-run` | Đếm bao nhiêu dòng test còn sót |
| `npm run db:delete-test` | Xóa rác trong transaction |
