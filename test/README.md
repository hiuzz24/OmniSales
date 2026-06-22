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
| `mvn test -Dtest=ClassName` | Chạy một class test cụ thể |
