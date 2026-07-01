# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: e2e\product.spec.js >> Product E2E Tests >> Product Listing Page >> A5 - Pagination navigation works
- Location: e2e\product.spec.js:49:5

# Error details

```
Test timeout of 30000ms exceeded while running "beforeEach" hook.
```

```
Error: page.waitForLoadState: Test timeout of 30000ms exceeded.
```

# Page snapshot

```yaml
- generic [ref=e2]:
  - generic [ref=e3]:
    - complementary [ref=e4]:
      - generic [ref=e5]:
        - generic [ref=e6]: OmniSales
        - button [ref=e7] [cursor=pointer]:
          - img [ref=e8]
      - navigation [ref=e9]:
        - link "Dashboard" [ref=e11] [cursor=pointer]:
          - /url: /dashboard
          - img [ref=e12]
          - generic [ref=e17]: Dashboard
        - generic [ref=e18]:
          - button "Sản phẩm" [ref=e19] [cursor=pointer]:
            - img [ref=e20]
            - generic [ref=e24]: Sản phẩm
            - img [ref=e25]
          - generic [ref=e27]:
            - link "Danh sách sản phẩm" [ref=e28] [cursor=pointer]:
              - /url: /products
              - img [ref=e29]
              - generic [ref=e33]: Danh sách sản phẩm
            - link "Danh mục sản phẩm" [ref=e34] [cursor=pointer]:
              - /url: /products/categories
              - img [ref=e35]
              - generic [ref=e38]: Danh mục sản phẩm
        - button "Kho hàng" [ref=e40] [cursor=pointer]:
          - img [ref=e41]
          - generic [ref=e44]: Kho hàng
          - img [ref=e45]
        - link "Khách hàng" [ref=e48] [cursor=pointer]:
          - /url: /customers
          - img [ref=e49]
          - generic [ref=e54]: Khách hàng
        - link "Bán hàng (POS)" [ref=e56] [cursor=pointer]:
          - /url: /pos
          - img [ref=e57]
          - generic [ref=e61]: Bán hàng (POS)
        - link "Đơn hàng" [ref=e63] [cursor=pointer]:
          - /url: /orders
          - img [ref=e64]
          - generic [ref=e68]: Đơn hàng
        - link "Kênh bán hàng" [ref=e70] [cursor=pointer]:
          - /url: /channels
          - img [ref=e71]
          - generic [ref=e77]: Kênh bán hàng
        - link "Phân tích" [ref=e79] [cursor=pointer]:
          - /url: /analytics
          - img [ref=e80]
          - generic [ref=e82]: Phân tích
        - link "Nhân sự" [ref=e84] [cursor=pointer]:
          - /url: /users
          - img [ref=e85]
          - generic [ref=e90]: Nhân sự
        - link "Cài đặt" [ref=e92] [cursor=pointer]:
          - /url: /settings
          - img [ref=e93]
          - generic [ref=e96]: Cài đặt
    - generic [ref=e97]:
      - banner [ref=e98]:
        - generic [ref=e99]: Hệ thống quản lý bán hàng đa kênh
        - generic [ref=e100]:
          - button [ref=e102] [cursor=pointer]:
            - img [ref=e103]
          - button "QL Quản lý Owner" [ref=e107] [cursor=pointer]:
            - generic [ref=e108]: QL
            - generic [ref=e109]:
              - generic [ref=e110]: Quản lý
              - generic [ref=e111]: Owner
      - main [ref=e112]:
        - generic [ref=e113]:
          - generic [ref=e114]:
            - generic [ref=e115]:
              - img [ref=e117]
              - generic [ref=e121]:
                - heading "Sản phẩm" [level=1] [ref=e122]
                - paragraph [ref=e123]: Quản lý kho hàng và các sản phẩm trên hệ thống
            - generic [ref=e124]:
              - button "Lịch sử đồng bộ" [ref=e125] [cursor=pointer]:
                - img [ref=e126]
                - text: Lịch sử đồng bộ
              - button "Nhật ký hệ thống" [ref=e131] [cursor=pointer]:
                - img [ref=e132]
                - text: Nhật ký hệ thống
              - button "Nhập Excel" [ref=e136] [cursor=pointer]:
                - img [ref=e137]
                - text: Nhập Excel
              - button "Xuất Excel" [ref=e141] [cursor=pointer]:
                - img [ref=e142]
                - text: Xuất Excel
              - button "Thêm sản phẩm mới" [ref=e146] [cursor=pointer]:
                - img [ref=e147]
                - text: Thêm sản phẩm mới
          - generic [ref=e148]:
            - generic [ref=e149]:
              - generic:
                - img
              - textbox "Tìm kiếm sản phẩm theo tên hoặc SKU..." [ref=e150]
            - generic [ref=e151]:
              - combobox [ref=e152] [cursor=pointer]:
                - option "Tất cả kênh" [selected]
                - option "Shopee"
                - option "TikTok Shop"
                - option "Lazada"
                - option "Shopify"
                - option "Thủ công"
              - generic:
                - img
            - generic [ref=e153]:
              - combobox [ref=e154] [cursor=pointer]:
                - option "Tất cả trạng thái" [selected]
                - option "Hoạt động"
                - option "Ngừng bán"
                - option "Nháp"
              - generic:
                - img
          - generic [ref=e155]:
            - generic [ref=e156]:
              - heading "Danh sách sản phẩm (12)" [level=3] [ref=e157]:
                - img [ref=e159]
                - text: Danh sách sản phẩm
                - generic [ref=e161]: (12)
              - button "Thêm sản phẩm" [ref=e162] [cursor=pointer]:
                - img [ref=e163]
                - text: Thêm sản phẩm
            - table [ref=e166]:
              - rowgroup [ref=e167]:
                - row "Sản phẩm SKU Danh mục Kênh bán Giá bán Tồn kho Trạng thái Chi tiết" [ref=e168]:
                  - columnheader "Sản phẩm" [ref=e169]
                  - columnheader "SKU" [ref=e170]
                  - columnheader "Danh mục" [ref=e171]
                  - columnheader "Kênh bán" [ref=e172]
                  - columnheader "Giá bán" [ref=e173]
                  - columnheader "Tồn kho" [ref=e174]
                  - columnheader "Trạng thái" [ref=e175]
                  - columnheader "Chi tiết" [ref=e176]
              - rowgroup [ref=e177]:
                - row "E2E Draft Product 1782312960132 DRAFT-1782312960311-36473 Thời trang Shopee Lazada 99.000đ 0 Nháp Chi tiết" [ref=e178] [cursor=pointer]:
                  - cell "E2E Draft Product 1782312960132" [ref=e179]:
                    - generic [ref=e180]:
                      - img [ref=e183]
                      - generic [ref=e188]: E2E Draft Product 1782312960132
                  - cell "DRAFT-1782312960311-36473" [ref=e189]:
                    - code [ref=e190]: DRAFT-1782312960311-36473
                  - cell "Thời trang" [ref=e191]:
                    - generic [ref=e192]: Thời trang
                  - cell "Shopee Lazada" [ref=e193]:
                    - generic [ref=e194]:
                      - generic [ref=e196]: Shopee
                      - generic [ref=e198]: Lazada
                  - cell "99.000đ" [ref=e199]:
                    - generic [ref=e201]: 99.000đ
                  - cell "0" [ref=e202]:
                    - generic [ref=e204]: "0"
                  - cell "Nháp" [ref=e206]:
                    - generic [ref=e208]: Nháp
                  - cell "Chi tiết" [ref=e209]:
                    - button "Chi tiết" [ref=e210]:
                      - img [ref=e211]
                      - generic [ref=e214]: Chi tiết
                - row "Test Product TEST-1782913947749-90736 Thời trang Shopee Lazada 0đ 0 Hoạt động Chi tiết" [ref=e215] [cursor=pointer]:
                  - cell "Test Product" [ref=e216]:
                    - generic [ref=e217]:
                      - img [ref=e220]
                      - generic [ref=e225]: Test Product
                  - cell "TEST-1782913947749-90736" [ref=e226]:
                    - code [ref=e227]: TEST-1782913947749-90736
                  - cell "Thời trang" [ref=e228]:
                    - generic [ref=e229]: Thời trang
                  - cell "Shopee Lazada" [ref=e230]:
                    - generic [ref=e231]:
                      - generic [ref=e233]: Shopee
                      - generic [ref=e235]: Lazada
                  - cell "0đ" [ref=e236]:
                    - generic [ref=e238]: 0đ
                  - cell "0" [ref=e239]:
                    - generic [ref=e241]: "0"
                  - cell "Hoạt động" [ref=e243]:
                    - generic [ref=e245]: Hoạt động
                  - cell "Chi tiết" [ref=e246]:
                    - button "Chi tiết" [ref=e247]:
                      - img [ref=e248]
                      - generic [ref=e251]: Chi tiết
                - row "Test Product TEST-1782914272923-14803 Thời trang Shopee Lazada 0đ 0 Hoạt động Chi tiết" [ref=e252] [cursor=pointer]:
                  - cell "Test Product" [ref=e253]:
                    - generic [ref=e254]:
                      - img [ref=e257]
                      - generic [ref=e262]: Test Product
                  - cell "TEST-1782914272923-14803" [ref=e263]:
                    - code [ref=e264]: TEST-1782914272923-14803
                  - cell "Thời trang" [ref=e265]:
                    - generic [ref=e266]: Thời trang
                  - cell "Shopee Lazada" [ref=e267]:
                    - generic [ref=e268]:
                      - generic [ref=e270]: Shopee
                      - generic [ref=e272]: Lazada
                  - cell "0đ" [ref=e273]:
                    - generic [ref=e275]: 0đ
                  - cell "0" [ref=e276]:
                    - generic [ref=e278]: "0"
                  - cell "Hoạt động" [ref=e280]:
                    - generic [ref=e282]: Hoạt động
                  - cell "Chi tiết" [ref=e283]:
                    - button "Chi tiết" [ref=e284]:
                      - img [ref=e285]
                      - generic [ref=e288]: Chi tiết
                - row "Test Product TEST-1782915947681-48651 Thời trang Shopee Lazada 0đ 0 Hoạt động Chi tiết" [ref=e289] [cursor=pointer]:
                  - cell "Test Product" [ref=e290]:
                    - generic [ref=e291]:
                      - img [ref=e294]
                      - generic [ref=e299]: Test Product
                  - cell "TEST-1782915947681-48651" [ref=e300]:
                    - code [ref=e301]: TEST-1782915947681-48651
                  - cell "Thời trang" [ref=e302]:
                    - generic [ref=e303]: Thời trang
                  - cell "Shopee Lazada" [ref=e304]:
                    - generic [ref=e305]:
                      - generic [ref=e307]: Shopee
                      - generic [ref=e309]: Lazada
                  - cell "0đ" [ref=e310]:
                    - generic [ref=e312]: 0đ
                  - cell "0" [ref=e313]:
                    - generic [ref=e315]: "0"
                  - cell "Hoạt động" [ref=e317]:
                    - generic [ref=e319]: Hoạt động
                  - cell "Chi tiết" [ref=e320]:
                    - button "Chi tiết" [ref=e321]:
                      - img [ref=e322]
                      - generic [ref=e325]: Chi tiết
                - row "Test Product TEST-1782916781524-86614 Thời trang Shopee Lazada 0đ 0 Hoạt động Chi tiết" [ref=e326] [cursor=pointer]:
                  - cell "Test Product" [ref=e327]:
                    - generic [ref=e328]:
                      - img [ref=e331]
                      - generic [ref=e336]: Test Product
                  - cell "TEST-1782916781524-86614" [ref=e337]:
                    - code [ref=e338]: TEST-1782916781524-86614
                  - cell "Thời trang" [ref=e339]:
                    - generic [ref=e340]: Thời trang
                  - cell "Shopee Lazada" [ref=e341]:
                    - generic [ref=e342]:
                      - generic [ref=e344]: Shopee
                      - generic [ref=e346]: Lazada
                  - cell "0đ" [ref=e347]:
                    - generic [ref=e349]: 0đ
                  - cell "0" [ref=e350]:
                    - generic [ref=e352]: "0"
                  - cell "Hoạt động" [ref=e354]:
                    - generic [ref=e356]: Hoạt động
                  - cell "Chi tiết" [ref=e357]:
                    - button "Chi tiết" [ref=e358]:
                      - img [ref=e359]
                      - generic [ref=e362]: Chi tiết
                - row "Updated E2E Product 1782916889041 E2E-1782247068579-92523 Thời trang Shopee Lazada 199.000đ 0 Hoạt động Chi tiết" [ref=e363] [cursor=pointer]:
                  - cell "Updated E2E Product 1782916889041" [ref=e364]:
                    - generic [ref=e365]:
                      - img [ref=e368]
                      - generic [ref=e373]: Updated E2E Product 1782916889041
                  - cell "E2E-1782247068579-92523" [ref=e374]:
                    - code [ref=e375]: E2E-1782247068579-92523
                  - cell "Thời trang" [ref=e376]:
                    - generic [ref=e377]: Thời trang
                  - cell "Shopee Lazada" [ref=e378]:
                    - generic [ref=e379]:
                      - generic [ref=e381]: Shopee
                      - generic [ref=e383]: Lazada
                  - cell "199.000đ" [ref=e384]:
                    - generic [ref=e386]: 199.000đ
                  - cell "0" [ref=e387]:
                    - generic [ref=e389]: "0"
                  - cell "Hoạt động" [ref=e391]:
                    - generic [ref=e393]: Hoạt động
                  - cell "Chi tiết" [ref=e394]:
                    - button "Chi tiết" [ref=e395]:
                      - img [ref=e396]
                      - generic [ref=e399]: Chi tiết
            - navigation "Phân trang" [ref=e400]:
              - paragraph [ref=e401]:
                - text: Hiển thị
                - strong [ref=e402]: 1-6
                - text: / 12 sản phẩm
              - generic [ref=e403]:
                - button "Trang trước" [disabled] [ref=e404]:
                  - img [ref=e405]
                  - generic [ref=e407]: Trước
                - generic "Trang 1 trên 2" [ref=e408]:
                  - button "1" [ref=e409] [cursor=pointer]
                  - button "2" [ref=e410] [cursor=pointer]
                - button "Trang sau" [ref=e411] [cursor=pointer]:
                  - generic [ref=e412]: Sau
                  - img [ref=e413]
  - region "Notifications Alt+T"
```

# Test source

```ts
  1   | const { test, expect } = require('@playwright/test');
  2   | const { loginAsManager, uniqueSku } = require('../utils/product-helpers');
  3   | 
  4   | test.describe('Product E2E Tests', () => {
  5   | 
  6   |   // =========================================================
  7   |   // A. PRODUCT LISTING PAGE
  8   |   // =========================================================
  9   |   test.describe('Product Listing Page', () => {
  10  | 
  11  |     test.beforeEach(async ({ page }) => {
  12  |       await loginAsManager(page);
  13  |       await page.goto('/products');
> 14  |       await page.waitForLoadState('networkidle');
      |                  ^ Error: page.waitForLoadState: Test timeout of 30000ms exceeded.
  15  |     });
  16  | 
  17  |     test('A1 - Product list page renders correctly', async ({ page }) => {
  18  |       await expect(page.locator('h1:has-text("Sản phẩm")')).toBeVisible();
  19  |       await expect(page.locator('input[placeholder*="Tìm kiếm"], input[placeholder*="Search"]').first()).toBeVisible();
  20  |       // Use more specific locator for table title
  21  |       await expect(page.getByRole('heading', { name: /Danh sách sản phẩm/ })).toBeVisible();
  22  |     });
  23  | 
  24  |     test('A2 - Search product by keyword', async ({ page }) => {
  25  |       const searchInput = page.locator('input[placeholder*="Tìm kiếm"], input[placeholder*="Search"]').first();
  26  |       await searchInput.fill('áo');
  27  |       await page.waitForTimeout(700);
  28  | 
  29  |       // Just verify we're on products page with table
  30  |       await expect(page.getByRole('heading', { name: /Danh sách sản phẩm/ })).toBeVisible();
  31  |     });
  32  | 
  33  |     test('A3 - Filter by status (ACTIVE)', async ({ page }) => {
  34  |       const statusSelect = page.locator('select').nth(1);
  35  |       await statusSelect.selectOption('ACTIVE');
  36  |       await page.waitForTimeout(500);
  37  | 
  38  |       await expect(page.getByRole('heading', { name: /Danh sách sản phẩm/ })).toBeVisible();
  39  |     });
  40  | 
  41  |     test('A4 - Filter by platform (SHOPEE)', async ({ page }) => {
  42  |       const platformSelect = page.locator('select').nth(0);
  43  |       await platformSelect.selectOption('SHOPEE');
  44  |       await page.waitForTimeout(500);
  45  | 
  46  |       await expect(page.getByRole('heading', { name: /Danh sách sản phẩm/ })).toBeVisible();
  47  |     });
  48  | 
  49  |     test('A5 - Pagination navigation works', async ({ page }) => {
  50  |       // Check if pagination exists
  51  |       const paginationNav = page.locator('nav[aria-label="Phân trang"], nav:has-text("Trang")').first();
  52  |       const hasPagination = await paginationNav.count();
  53  |       
  54  |       if (hasPagination > 0) {
  55  |         // Use aria-label for more specific selection
  56  |         const nextBtn = page.locator('button[aria-label="Trang sau"], button[aria-label="Next"]').first();
  57  |         const prevBtn = page.locator('button[aria-label="Trang trước"], button[aria-label="Previous"]').first();
  58  |         
  59  |         const isNextVisible = await nextBtn.isVisible();
  60  |         if (isNextVisible) {
  61  |           await nextBtn.click();
  62  |           await page.waitForTimeout(500);
  63  |         }
  64  |       }
  65  |       // Test passes if no errors occur
  66  |       expect(true).toBeTruthy();
  67  |     });
  68  | 
  69  |     test('A6 - Click Chi tiet button navigates to detail page', async ({ page }) => {
  70  |       await page.waitForTimeout(1000);
  71  | 
  72  |       const rows = page.locator('tbody tr');
  73  |       const count = await rows.count();
  74  | 
  75  |       if (count > 0) {
  76  |         const firstDetailBtn = page.locator('button:has-text("Chi tiết")').first();
  77  |         if (await firstDetailBtn.isVisible()) {
  78  |           await firstDetailBtn.click();
  79  |           await page.waitForURL(/\/products\/.+/);
  80  |           await expect(page).toHaveURL(/\/products\/[a-f0-9-]+/);
  81  |         }
  82  |       }
  83  |     });
  84  | 
  85  |     test('A7 - Navigate to create product page', async ({ page }) => {
  86  |       await page.locator('button:has-text("Thêm sản phẩm mới"), button:has-text("Thêm mới")').click();
  87  |       await page.waitForURL(/\/products\/create/);
  88  |       await expect(page).toHaveURL(/\/products\/create/);
  89  |     });
  90  | 
  91  |     test('A8 - Navigate to product logs', async ({ page }) => {
  92  |       // Try to find product logs button
  93  |       const logsBtn = page.locator('button:has-text("Nhật ký sản phẩm"), button:has-text("Lịch sử"), a:has-text("Nhật ký")').first();
  94  |       const hasLogsBtn = await logsBtn.count();
  95  |       if (hasLogsBtn > 0 && await logsBtn.isVisible()) {
  96  |         await logsBtn.click();
  97  |         await page.waitForTimeout(2000);
  98  |         // Accept any valid navigation
  99  |         const currentUrl = page.url();
  100 |         const isValidPage = currentUrl.includes('/products') || currentUrl.includes('/sync') || currentUrl.includes('/logs');
  101 |         expect(isValidPage).toBeTruthy();
  102 |       } else {
  103 |         // Button not found, skip test
  104 |         test.skip();
  105 |       }
  106 |     });
  107 | 
  108 |     test('A9 - Empty state shows when no products match filter', async ({ page }) => {
  109 |       const searchInput = page.locator('input[placeholder*="Tìm kiếm"], input[placeholder*="Search"]').first();
  110 |       await searchInput.fill('xyznonexistentproduct99999xyz');
  111 |       await page.waitForTimeout(700);
  112 | 
  113 |       // Either empty state or no results message should be visible
  114 |       const hasEmpty = await page.locator('text=Không có sản phẩm, text=Không tìm thấy').count();
```