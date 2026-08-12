# Deploy OmniSales lên Render.com (Free Tier)

> Hướng dẫn deploy toàn bộ stack OmniSales (PostgreSQL + Backend Java + Frontend React) lên Render.com trong vòng **15-20 phút**, hoàn toàn miễn phí (Postgres 90 ngày, Web + Static free vĩnh viễn).

---

## Mục lục

- [1. Tại sao chọn Render](#1-tại-sao-chọn-render)
- [2. Chuẩn bị](#2-chuẩn-bị)
- [3. Tạo Blueprint (1 cú click)](#3-tạo-blueprint-1-cú-click)
- [4. Cấu hình env vars](#4-cấu-hình-env-vars)
- [5. Đợi build](#5-đợi-build)
- [6. Seed dữ liệu DB](#6-seed-dữ-liệu-db)
- [7. Verify](#7-verify)
- [8. Warm up trước demo](#8-warm-up-trước-demo)
- [9. Test OAuth](#9-test-oauth)
- [10. Free tier limits & workarounds](#10-free-tier-limits--workarounds)
- [11. Rollback / Cleanup](#11-rollback--cleanup)
- [Phụ lục: Troubleshooting](#phụ-lục-troubleshooting)

---

## 1. Tại sao chọn Render

| Tiêu chí | Render free | VPS tự host |
|---|---|---|
| Giá | $0 (tháng đầu) | ~$5-10/tháng |
| HTTPS auto | ✅ | Phải tự cài Caddy |
| Setup time | 15 phút | 2-4 giờ |
| Sleep sau idle | 15 phút (có thể warm up) | Không |
| Scale-down khi không dùng | Tự động | Không |
| OAuth (HTTPS bắt buộc) | ✅ Auto | Phải tự cấu hình |

**Phù hợp cho**: đồ án 1 tháng, demo trước hội đồng, không cần scale lớn.

---

## 2. Chuẩn bị

Cần có sẵn:

- [x] **Tài khoản Render** — đăng ký miễn phí tại https://render.com (dùng email hoặc GitHub).
- [x] **GitLab/GitHub repo** đã push code lên nhánh `main`.
- [x] **Không có secret thật** trong git history (đã rotate trước đó — xem `docs/deploy-checklist.md` mục A).

### 2.1. Pre-flight check (local)

```bash
bash scripts/render-deploy.sh
```

Script sẽ verify:

- Working tree clean
- Branch = `main`
- `render.yaml` parse OK
- `backend/Dockerfile` có `JAVA_TOOL_OPTIONS`
- Không có `.env` bị git track

> Nếu có bất kỳ warning nào → fix trước khi tiếp.

---

## 3. Tạo Blueprint (1 cú click)

1. Vào https://dashboard.render.com/blueprints.
2. Click **"New Blueprint Instance"**.
3. Chọn repo (GitLab / GitHub) chứa OmniSales.
4. Render sẽ tự detect file `render.yaml` ở root.
5. Click **"Apply"**.

Render sẽ tạo **3 resource**:

| Resource | Type | URL |
|---|---|---|
| `omnisales-db` | Postgres free | (internal only) |
| `api-osms` | Web service (Docker) | `https://api-osms.onrender.com` |
| `app-osms` | Static site | `https://app-osms.onrender.com` |

> ⏳ Mất ~2-3 phút để Render provision DB và tạo service skeleton.

---

## 4. Cấu hình env vars

Vài biến được đánh `sync: false` — bạn phải nhập tay trên Render UI.

### 4.1. Bắt buộc cho OAuth / integrations

Vào **api-osms service** → **Environment** → điền các giá trị:

| Key | Cách nhập |
|---|---|
| `GMAIL_PASSWORD` | App password từ https://myaccount.google.com/apppasswords |
| `SHOPIFY_API_KEY` / `SHOPIFY_API_SECRET` | Shopify Partners → App → API credentials |
| `LAZADA_APP_KEY` / `LAZADA_APP_SECRET` | Lazada Seller Center → My Apps |
| `TIKTOK_APP_KEY` / `TIKTOK_APP_SECRET` | TikTok Partner Center → App Management |
| `REST_COUNTRIES_API_KEY` | Từ provider dashboard |

Sau khi điền → click **"Save Changes"** → Render tự động redeploy.

### 4.2. Tùy chọn

| Key | Mặc định | Có thể đổi |
|---|---|---|
| `NOTIFICATION_EMAIL_ENABLED` | `false` | `true` sau khi có `GMAIL_PASSWORD` |
| `JAVA_TOOL_OPTIONS` | `-Xmx384m -Xms192m -XX:+UseG1GC` | Tăng lên nếu OOM |

### 4.3. Không cần đổi

`JWT_SECRET` được Render tự generate. `DB_*` được inject từ `omnisales-db`.

---

## 5. Đợi build

Build mất **~10-15 phút** cho backend (Maven tải deps + compile + package). Frontend build ~3 phút.

### Theo dõi progress

Vào **api-osms** → **Logs** → kéo xuống xem:

```
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  X.XXX s
```

Sau đó:

```
   ____          ___          ___
  / __/__ ____ / _ \___ ____/ _ \___
 _\ \/ _ `/ -_) ___/ _ `/ _  ___/
/___/\_,_/_/ |_/  \_,_/_//_/_/

Started OSMS in X.XXX seconds
Tomcat started on port 8080
```

### Service status

| Status | Ý nghĩa |
|---|---|
| `Live` | Service đang chạy OK, có thể nhận request |
| `Building` | Đang build image, chờ |
| `Deploy failed` | Xem logs để biết lỗi |

---

## 6. Seed dữ liệu DB

Postgres của Render **không tự động** chạy `hibernate-schema.sql` (chỉ compose stack mới có `docker-entrypoint-initdb.d` mount).

Bạn cần 1 trong 2 cách:

### Cách A: Dùng Render Shell (khuyến nghị)

1. Vào **omnisales-db** → **Shell** (hoặc dùng `psql` local với external connection string).
2. Copy nội dung `backend/hibernate-schema.sql` → paste vào.
3. Copy nội dung các file migration `backend/database-migrations/*.sql` → paste tiếp (theo thứ tự thời gian).

### Cách B: Dùng JOB scheduled migration (tự động)

Tạo Render Job service (cũng miễn phí) chạy `psql` lúc startup. Xem [Phụ lục B](#phụ-lục-b-tự-động-chạy-migration-với-render-job).

### Cách C: Kết nối từ local (nhanh nhất lần đầu)

Lấy **External Connection String** từ Render DB dashboard:

```
postgresql://postgres:<password>@<host>.oregon-postgres.render.com:5432/OSMS
```

Rồi từ local:

```bash
# Set password
export PGPASSWORD='<password-from-render>'

# Connect
psql -h <host>.oregon-postgres.render.com -U postgres -d OSMS \
  -f backend/hibernate-schema.sql

# Apply migrations
psql -h <host>.oregon-postgres.render.com -U postgres -d OSMS \
  -f backend/database-migrations/20260806_stocktake_detail_page_fields.sql

# Verify
psql -h <host>.oregon-postgres.render.com -U postgres -d OSMS -c "\dt"
```

> 💡 Nếu chưa có `psql` local, dùng **Render Shell** (chọn `bash` → cài qua `apt-get install postgresql-client`).

### Verify schema

```sql
\dt
```

Phải thấy các table: `users`, `warehouses`, `products`, `orders`, `order_items`, `stocktake_sessions`, `stocktake_items`, `notifications`, `channels`, ...

---

## 7. Verify

Mở 4 tab song song:

### Tab 1: Backend health

```
https://api-osms.onrender.com/api/address/countries
```

→ JSON array ~250 quốc gia → backend OK.

### Tab 2: Frontend

```
https://app-osms.onrender.com/
```

→ Render UI login → ✅

### Tab 3: Backend logs

Vào Render dashboard → **api-osms** → **Logs** → xem có lỗi runtime không.

### Tab 4: DB ping

```
psql -h <host>.oregon-postgres.render.com -U postgres -d OSMS -c "SELECT count(*) FROM users;"
```

→ Phải trả > 0 (admin user từ seed).

### Login test

Username: `admin@osms.vn`  
Password: `11111111`

---

## 8. Warm up trước demo

Free tier **sleep sau 15 phút idle**. Trước buổi demo, chạy:

```bash
bash scripts/render-warmup.sh
```

Lần đầu hit sẽ mất 30-60 giây (cold start). Script sẽ:

1. Probe `/api/address/countries` (backend wake up)
2. Probe `/` (frontend wake up)
3. Retry 5 lần nếu timeout

Tùy chọn:

```bash
# Chỉ wake frontend
bash scripts/render-warmup.sh --frontend-only

# Chỉ wake backend
bash scripts/render-warmup.sh --backend-only

# Custom timeout (90s mặc định)
WARMUP_TIMEOUT=120 bash scripts/render-warmup.sh
```

---

## 9. Test OAuth

```bash
bash scripts/render-oauth-test.sh
```

Script sẽ kiểm tra:

| Check | Mô tả |
|---|---|
| URL format | HTTPS, không trailing slash |
| Shopify callback | `OPTIONS /api/channels/shopify/callback` → 200/204 |
| Lazada callback | `OPTIONS /api/channels/lazada/callback` → 200/204 |
| TikTok callback | `OPTIONS /api/channels/tiktok/callback` → 200/204 |
| Backend health | `GET /api/address/countries` → 200 |
| CORS | Response có `Access-Control-Allow-Origin: https://app-osms.onrender.com` |
| Frontend SPA | `GET /` → HTML có `<body>` |

Nếu CORS fail, sửa `backend/src/main/java/fu/osms/config/SecurityConfig.java`:

```java
corsConfiguration.setAllowedOriginPatterns(List.of(
    "http://localhost:517*",
    "http://localhost:300*",
    "http://127.0.0.1:517*",
    "http://127.0.0.1:300*",
    "https://app-osms.onrender.com"   // <-- add
));
```

Rồi commit + push → Render auto-deploy.

---

## 10. Free tier limits & workarounds

| Limit | Workaround |
|---|---|
| **Web service sleep sau 15 phút idle** | `scripts/render-warmup.sh` trước demo. Hoặc upgrade lên plan trả phí $7/tháng. |
| **Postgres free chỉ 90 ngày** | OK cho 1 tháng demo. Sau 30 ngày → backup + tạo DB mới (chạy lại blueprint). |
| **Build ~10-15 phút** | Lần đầu chậm. Sau đó layer cache, build nhanh hơn ~5 phút. |
| **0.5 CPU / 512MB RAM** | `JAVA_TOOL_OPTIONS="-Xmx384m -Xms192m"` đã tối ưu cho 512MB. |
| **Không có persistent volume** | Backup Postgres qua external connection string hoặc Render Job chạy `pg_dump` daily. |
| **Không có custom domain HTTPS** | OK cho demo. `*.onrender.com` cũng có HTTPS auto. |
| **Webhook Shopify/Lazada** | Webhook URL public nên Shopify có thể reach → chỉ cần URL đúng. |

---

## 11. Rollback / Cleanup

### Rollback 1 service

Vào service → **Manual Deploy** → chọn commit cũ.

### Xóa toàn bộ stack

Vào **Dashboard** → **Blueprint** → **Settings** → **Delete Blueprint**.

> ⚠️ Postgres free tier sẽ bị xóa cùng. Backup trước nếu cần data.

### Backup Postgres

```bash
export PGPASSWORD='<password-from-render>'
pg_dump -h <host>.oregon-postgres.render.com -U postgres -Fc OSMS \
  > backup-osms-$(date +%Y%m%d).backup
```

Lưu file ra ngoài (Google Drive, S3, USB).

### Restore Postgres

```bash
export PGPASSWORD='<password-from-render>'
pg_restore -h <host>.oregon-postgres.render.com -U postgres -d OSMS \
  --clean --if-exists backup-osms-20260806.backup
```

---

## Phụ lục

### Phụ lục A: URL routing trên Render

Vì SPA cần gọi API, có 3 pattern phổ biến:

#### Pattern A: Cross-origin (đang dùng)

```
Frontend: https://app-osms.onrender.com
Backend:  https://api-osms.onrender.com
```

→ Frontend gọi `fetch('https://api-osms.onrender.com/api/...')`  
→ Cần CORS allow `app-osms.onrender.com` ở backend.  
→ Cookie cross-origin (nếu dùng) cần `SameSite=None; Secure`.

#### Pattern B: Render reverse proxy (custom domain)

Không dùng được trên free tier (Render chỉ proxy qua DNS riêng).

#### Pattern C: Render `_headers` (chỉ header)

Static site → dùng `_headers` rule để inject CORS, không proxy.

### Phụ lục B: Tự động chạy migration với Render Job

Tạo Job service mới (miễn phí):

1. Render dashboard → **New +** → **Cron Job**.
2. Schedule: `0 0 * * *` (mỗi ngày, hoặc `@reboot` chỉ chạy 1 lần lúc migrate).
3. Command:
   ```bash
   PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -U $DB_USERNAME -d OSMS -f /etc/secrets/01-schema.sql
   ```
4. Mount file `backend/hibernate-schema.sql` qua Secret File.

### Phụ lục C: Monitoring free

Free tier Render có metrics cơ bản:

- Vào service → **Metrics**: CPU, RAM, HTTP req/s, response time.
- Logs live stream (không persist sau 24h).

### Phụ lục D: Custom domain (optional, không khuyến nghị)

Nếu muốn dùng `omnisales.your-domain.com`:

1. Mua domain (~$10-15/năm).
2. Vào service → **Settings** → **Custom Domains**.
3. Render cung cấp CNAME target.
4. DNS provider: thêm CNAME.

> Free tier KHÔNG hỗ trợ custom domain với HTTPS auto. Phải upgrade lên plan trả phí ($0 extra nhưng domain vẫn $10-15/năm).

---

## Troubleshooting

| Lỗi | Nguyên nhân | Fix |
|---|---|---|
| `404 on /api/...` từ frontend | Static site không proxy `/api` | Đổi `VITE_API_BASE_URL=https://api-osms.onrender.com/api` (đã set) |
| CORS error | SecurityConfig thiếu origin Render | Sửa SecurityConfig, redeploy |
| `JWT_SECRET must be provided` | Env chưa inject | Verify biến xuất hiện trong Render UI |
| Backend quá chậm 60s | Cold start free tier | `scripts/render-warmup.sh` trước demo |
| OutOfMemoryError | Java heap lớn hơn 384MB | Giảm `JAVA_TOOL_OPTIONS="-Xmx256m"` |
| `Schema-validation: missing column ...` | Migration chưa chạy | Chạy SQL qua Render Shell (xem mục 6) |
| Connection timeout DB | DB ở region khác với backend | Cả 2 đều `oregon` trong render.yaml |
| OAuth callback fail → localhost | `SHOPIFY_REDIRECT_URI` trỏ localhost | Update env: `https://api-osms.onrender.com/api/channels/shopify/callback` |
| Logs `Hibernate: alter table ... add constraint` | Auto-DDL bị bật | OK nếu schema empty; nếu data tồn tại thì kiểm tra |

---

## Liên hệ

- Render Discord: https://discord.gg/render
- OmniSales issues: xem `docs/deploy-checklist.md` hoặc tạo issue trong repo.
