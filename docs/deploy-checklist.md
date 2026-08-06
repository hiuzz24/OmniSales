# OmniSales — Deploy Checklist

> Hướng dẫn từng bước để deploy OmniSales lên môi trường production (VPS / VM / Cloud).
> Đọc kỹ P0 (bảo mật) trước khi làm bất cứ bước nào.

---

## Mục lục

- [A. Bảo mật (P0 — bắt buộc)](#a-bảo-mật-p0--bắt-buộc)
- [B. DNS / Domain (P1 nếu có HTTPS)](#b-dns--domain-p1-nếu-có-https)
- [C. Database (P0)](#c-database-p0)
- [D. Build & Deploy (P0)](#d-build--deploy-p0)
- [E. Post-deploy Verify (P1)](#e-post-deploy-verify-p1)
- [F. Rollback Plan](#f-rollback-plan)
- [H. Render.com (Free Tier Blueprint)](#h-rendercom-free-tier-blueprint)
- [G. RabbitMQ (Deferred)](#g-rabbitmq-deferred)
- [Phụ lục: Lệnh nhanh](#phụ-lục-lệnh-nhanh)

---

## A. Bảo mật (P0 — bắt buộc)

> ⚠️ Repo hiện tại có chứa **secret thật** đang commit (Gmail app password, Shopify/Lazada/TikTok secrets, REST Countries live key, JWT secret). Bạn **PHẢI rotate trước** khi deploy.

### A.1. Rotate tất cả secret

- [ ] **Gmail App Password** — Truy cập https://myaccount.google.com/apppasswords → Revoke tất cả → tạo mới
- [ ] **Shopify Secret** — Settings → Apps → Develop apps → click app → Reset secret
- [ ] **Lazada Secret** — Seller Center → Apps → My Apps (nếu không tự đổi được, contact support)
- [ ] **TikTok Secret** — Partner Center → App Management → Regenerate
- [ ] **REST Countries API Key** — Tạo key mới từ dashboard provider
- [ ] **JWT secret** — Generate random mới (xem A.2)
- [ ] **DB password** — Generate random mới (xem A.2)

### A.2. Generate random secret

**JWT_SECRET** (≥ 48 bytes base64):

```bash
openssl rand -base64 48
```

**DB_PASSWORD** (≥ 16 chars alphanumeric + symbol):

```bash
openssl rand -base64 24 | tr -d '/+=' | cut -c1-20
```

**FRONTEND_URL / DOMAIN** — dùng HTTPS nếu có domain (bắt buộc cho OAuth flow).

### A.3. Tạo `.env` production mới

Tạo file `OmniSales/.env` (KHÔNG dựa vào file hiện tại). Template tại [`OmniSales/.env.example`](../OmniSales/.env.example) là điểm bắt đầu tốt.

Các key bắt buộc phải đổi trước khi deploy:

```bash
DB_PASSWORD=<random-20-chars>
JWT_SECRET=<random-base64-48>
GMAIL_PASSWORD=<new-app-password>
SHOPIFY_API_SECRET=<new-secret>
LAZADA_APP_SECRET=<new-secret>
TIKTOK_APP_SECRET=<new-secret>
REST_COUNTRIES_API_KEY=<new-key>
FRONTEND_URL=https://your-domain.com
VITE_API_BASE_URL=/api
VITE_FRONTEND_URL=https://your-domain.com
```

### A.4. Verify secret không còn trong git history

```bash
# Liệt kê các commit từng chạm .env
git log --all --full-history -- "*.env" | head

# Kiểm tra file .env có đang tracked không
git ls-files | grep -E "\.env$" || echo "OK: .env không tracked"
```

Nếu `.env` đang tracked:

```bash
git rm --cached .env backend/.env
git commit -m "chore: untrack .env files"
```

### A.5. Verify `.gitignore` đã có `.env`

Kiểm tra `OmniSales/.gitignore` chứa:

```gitignore
.env
.env.local
.env.production
backend/.env
frontend/.env
```

---

## B. DNS / Domain (P1 nếu có HTTPS)

OAuth callback của Shopify/Lazada/TikTok **bắt buộc HTTPS**. Nếu deploy không có domain thì các flow này sẽ fail.

- [ ] Domain trỏ về IP server (A record): `app.your-domain.com` → `<VPS_IP>`
- [ ] (Optional) Subdomain riêng cho backend: `api.your-domain.com` → `<VPS_IP>`
- [ ] Config `DOMAIN` trong `.env` (để bật Caddy reverse proxy)
- [ ] (Optional) Config `EMAIL` trong `.env` cho Let's Encrypt notification

---

## C. Database (P0)

### C.1. Quyết định: drop & re-seed hay migrate?

**Nếu lần đầu deploy** (chưa có data):

```bash
# Xóa volume cũ (MẤT DATA nếu có)
docker compose down -v

# Khởi động postgres
docker compose up -d postgres_db

# Đợi healthcheck
docker compose ps postgres_db
# Status phải là "healthy"
```

**Nếu đã có data** (backup-trước-khi-deploy):

```bash
# Backup trước
docker compose exec postgres_db pg_dump -U postgres -Fc OSMS > backup-pre-deploy-$(date +%Y%m%d-%H%M).backup

# Apply migration SQL thủ công (QUAN TRỌNG)
docker compose exec postgres_db psql -U postgres -d OSMS \
  -f /docker-entrypoint-initdb.d/01-schema.sql  # chỉ chạy nếu DB trống

# Apply migration mới nhất
psql -U postgres -h localhost -d OSMS \
  -f backend/database-migrations/20260806_stocktake_detail_page_fields.sql
```

### C.2. Verify schema đầy đủ

```bash
docker compose exec postgres_db psql -U postgres -d OSMS -c "\dt"
```

Các table quan trọng phải tồn tại:

- `users`, `warehouses`, `products`, `orders`, `order_items`
- `stocktake_sessions`, `stocktake_items` (có cột `notes`, `started_by`, `started_at`, `completed_by`, `completed_at`, `cancelled_by`, `cancelled_at`)
- `notifications`, `inventory_movements`, `channels`

### C.3. Test connection

```bash
psql -U postgres -h localhost -d OSMS -c "SELECT version();"
```

Nếu dùng password mạnh, cần tạo `~/.pgpass` hoặc dùng `PGPASSWORD`:

```bash
PGPASSWORD=$(grep DB_PASSWORD .env | cut -d= -f2) psql -U postgres -d OSMS -c "SELECT 1;"
```

---

## D. Build & Deploy (P0)

### D.1. Verify env file

```bash
docker compose --env-file .env config > /dev/null && echo "OK"
```

Nếu lỗi "variable not set" → kiểm tra `.env` đã điền đủ chưa.

### D.2. Build images

```bash
docker compose --env-file .env build --no-cache
```

Build output: 2 images — `osms-backend`, `osms-frontend`.

### D.3. Up stack

```bash
docker compose --env-file .env up -d
```

Các container sẽ start theo thứ tự:
1. `osms_postgres` (chờ healthy)
2. `osms_backend` (depends on postgres healthy)
3. `osms_frontend` (depends on backend)

### D.4. Verify logs

```bash
docker compose logs -f backend
```

Tìm dòng:

```
Started OSMS in X.XXX seconds
Tomcat started on port 8080
```

Nếu thấy lỗi:

| Lỗi | Nguyên nhân | Fix |
|---|---|---|
| `Schema-validation: missing column ...` | Migration chưa chạy | Apply SQL migration (xem C.1) |
| `Connection refused: postgres_db:5432` | Postgres chưa ready | Đợi healthcheck |
| `FATAL: password authentication failed` | DB_PASSWORD sai | So sánh `.env` vs `docker-compose.yml` |
| `JWT_SECRET must be provided` | Env không inject | Check `depends_on` + env block |

### D.5. Backend healthcheck

```bash
curl -fsS http://localhost:8080/api/address/countries
```

Response 200 + JSON array → backend OK.

### D.6. Frontend hit

```bash
curl -fsS http://localhost:80/
```

Response 200 + HTML → frontend OK.

### D.7. Truy cập trình duyệt

Mở `http://<VPS_IP>/` hoặc `https://your-domain.com/` → login thử với admin seed.

---

## E. Post-deploy Verify (P1)

### E.1. Functional smoke test

- [ ] **Login** với admin account (admin@osms.vn / 11111111 từ `hibernate-schema.sql`)
- [ ] **Tạo warehouse** mới
- [ ] **Tạo product** mới
- [ ] **Tạo stocktake session** (kiểm tra schema mới: `notes`, `started_by`, etc.)
- [ ] **Tạo order** test
- [ ] **Check notification** trong DB

### E.2. OAuth flow (nếu có domain)

- [ ] **Shopify**: connect → callback → store token
- [ ] **Lazada**: connect → callback → store token
- [ ] **TikTok**: connect → callback → store token

Nếu callback fail về `localhost` → check `FRONTEND_URL` trong `.env` đã trỏ về domain chưa.

### E.3. Resource check

```bash
# Disk usage
docker system df
df -h /

# Memory
docker stats --no-stream

# Backend log size
du -sh /var/lib/docker/containers/osms_backend*
```

### E.4. Test backup

```bash
docker compose exec backend ls -la /app/backups
docker compose exec backend pg_dump -U postgres OSMS > /tmp/test-backup.sql
ls -la /tmp/test-backup.sql
```

### E.5. Check logs for errors

```bash
docker compose logs --since="5m ago" | grep -i error
```

---

## F. Rollback Plan

### F.1. Backup TRƯỚC deploy

```bash
docker compose exec postgres_db pg_dump -U postgres -Fc OSMS > backup-$(date +%Y%m%d-%H%M).backup
```

Lưu file `.backup` ra ngoài server (S3, OneDrive, USB...) — **KHÔNG chỉ để trong VPS**.

### F.2. Rollback steps

```bash
# 1. Stop stack
docker compose down

# 2. Restore DB (nếu migrate sai)
docker compose up -d postgres_db
docker compose exec -T postgres_db pg_restore -U postgres -d OSMS --clean --if-exists < backup-XXXX.backup

# 3. Revert code (nếu cần)
git checkout <previous-commit>
docker compose --env-file .env build --no-cache
docker compose up -d

# 4. Verify
docker compose logs -f backend
```

### F.3. Emergency rollback (chỉ xóa stack, giữ data)

```bash
docker compose down --remove-orphans
# Data vẫn còn trong volume postgres_data
```

Để xóa hẳn data:
```bash
docker compose down -v  # ⚠️ MẤT TOÀN BỘ DATA
```

---

## H. Render.com (Free Tier Blueprint)

> Phương án thay thế cho VPS — deploy cả stack lên Render.com với **$0/tháng** (Postgres 90 ngày, Web + Static free vĩnh viễn). Phù hợp cho đồ án 1 tháng, demo trước hội đồng.
>
> Hướng dẫn đầy đủ: [docs/deploy-render.md](deploy-render.md).

### H.1. Tại sao chọn Render?

- 1 cú click deploy cả Postgres + Backend + Frontend qua `render.yaml` Blueprint.
- HTTPS auto (cần cho OAuth Shopify/Lazada/TikTok).
- Không cần config Caddy / Let's Encrypt.
- Sleep sau 15 phút idle → warm up bằng `scripts/render-warmup.sh`.

### H.2. Pre-flight check

```bash
bash scripts/render-deploy.sh
```

Script verify:

- Branch = `main`
- `render.yaml` parse OK
- `backend/Dockerfile` có `JAVA_TOOL_OPTIONS`
- Không có `.env` bị git track

### H.3. Tạo Blueprint

1. Push code lên GitLab/GitHub (branch `main`).
2. Vào https://dashboard.render.com/blueprints → **New Blueprint Instance**.
3. Chọn repo + branch `main`.
4. Render tạo 3 resource:
   - `omnisales-db` (Postgres free, 90 ngày)
   - `api-osms` (Web service, Docker)
   - `app-osms` (Static site, Vite)
5. Vào `api-osms` → **Environment** → điền các biến `sync: false` (Gmail, Shopify, Lazada, TikTok, Cloudinary upload preset).

### H.4. Seed dữ liệu

Render free tier **không tự chạy** `hibernate-schema.sql`. Cách nhanh nhất:

```bash
# Lấy External Connection String từ Render DB dashboard
export PGPASSWORD='<password>'

# Connect & seed
psql -h <host>.oregon-postgres.render.com -U postgres -d OSMS \
  -f backend/hibernate-schema.sql

# Apply migrations
psql -h <host>.oregon-postgres.render.com -U postgres -d OSMS \
  -f backend/database-migrations/20260806_stocktake_detail_page_fields.sql
```

### H.5. Verify

```bash
# Backend health
curl -fsS https://api-osms.onrender.com/api/address/countries

# Frontend health
curl -fsS https://app-osms.onrender.com/

# OAuth / CORS check
bash scripts/render-oauth-test.sh
```

### H.6. Warm up trước demo

```bash
bash scripts/render-warmup.sh
```

### H.7. CORS — backend SecurityConfig

Free tier dùng **cross-origin** (frontend & backend ở 2 subdomain khác nhau). Cần thêm origin Render vào `backend/src/main/java/fu/osms/config/SecurityConfig.java`:

```java
corsConfiguration.setAllowedOriginPatterns(List.of(
    "http://localhost:517*",
    "http://localhost:300*",
    "http://127.0.0.1:517*",
    "http://127.0.0.1:300*",
    "https://app-osms.onrender.com"   // <-- thêm
));
```

### H.8. Free tier limits & workarounds

| Limit | Workaround |
|---|---|
| Web service sleep sau 15 phút idle | `scripts/render-warmup.sh` trước demo |
| Postgres free chỉ 90 ngày | OK cho 1 tháng demo. Backup trước khi hết hạn. |
| Build 10-15 phút | Lần đầu chậm. Cache cho lần sau. |
| 0.5 CPU / 512MB RAM | `JAVA_TOOL_OPTIONS="-Xmx384m -Xms192m"` |
| Không persistent volume | Backup Postgres qua external connection string |

---

## G. RabbitMQ (Deferred)

Hiện tại (Aug 2026), kiến trúc messaging chưa được wire-up:
- `spring-boot-starter-amqp` đã có trong `pom.xml`
- `messaging/config/RabbitMQConfig.java` là class rỗng
- `messaging/constants/RabbitMQConstants.java` đã có 13 routing key + 7 queue name
- Chưa có `spring.rabbitmq.*` config trong `application.yaml`
- Chưa có RabbitMQ service trong `docker-compose.yml`
- Listener vẫn dùng in-process `@EventListener` (5 file)

**Current state**: 5 listener trong `notification/listener/` đang dùng `ApplicationEventPublisher` + `@EventListener`. Hoạt động ổn, chỉ thiếu:
- Không persist nếu backend crash mid-process
- Không retry tự động
- Không thể scale horizontal

**Wire-up plan** (4 giai đoạn, ước tính 15-20 giờ code):

| Giai đoạn | Mô tả | Effort |
|---|---|---|
| 1 | Thêm RabbitMQ service vào docker-compose, config connection + topology (`RabbitMQConfig.java`) | 4-6h |
| 2 | Migrate `OrderCreatedNotificationListener` → `@RabbitListener` (test 1 listener) | 4h |
| 3 | Migrate 4 listener còn lại: `OrderPaid`, `OrderCancelled`, `Stock`, `LowStock` | 3h |
| 4 | DLQ + retry config + monitoring dashboard | 4-6h |

**Lưu ý**: Hiện tại KHÔNG cần wire-up để deploy. App chạy bình thường với `@EventListener`. RabbitMQ là **nice-to-have** cho đồ án, ưu tiên thấp hơn so với bảo mật P0.

Khi ready, follow `docs/rabbitmq-architecture.md` (TBD).

---

## Phụ lục: Lệnh nhanh

### Reset toàn bộ (XÓA DATA)

```bash
docker compose down -v
docker compose --env-file .env up -d --build
```

### Restart một service

```bash
docker compose restart backend
docker compose logs -f --tail=100 backend
```

### Xem logs theo service

```bash
docker compose logs -f postgres_db
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f proxy  # nếu dùng Caddy
```

### Shell vào container

```bash
docker compose exec backend sh
docker compose exec postgres_db psql -U postgres -d OSMS
```

### Lệnh backup tự động (cron)

```bash
# /etc/cron.d/osms-backup
0 2 * * * cd /opt/OmniSales && /usr/local/bin/docker compose exec -T postgres_db pg_dump -U postgres -Fc OSMS > /opt/backups/osms-$(date +\%Y\%m\%d).backup
```

### Lệnh restore

```bash
docker compose exec -T postgres_db pg_restore -U postgres -d OSMS --clean --if-exists < backup-20260806-0200.backup
```

---

## Liên hệ

Nếu gặp lỗi không có trong checklist:
1. Check logs: `docker compose logs -f <service>`
2. Check GitHub Issues
3. Liên hệ team lead
