# Báo cáo thay đổi code - Order Webhook và Manual Pull V1

## 1. Phạm vi

Thay đổi lần này gồm hai phần:

- Refactor webhook Lazada, Shopify và TikTok để tách API, mapping và persistence.
- Thêm chức năng kéo đơn thủ công chạy nền bằng Spring `@Async`.

Manual pull ghi trực tiếp `Order` và `OrderItem`, không tạo `WebhookEvent`, không gọi inventory, không sửa migration và không sửa automated test.

## 2. Luồng hoạt động tổng thể

```text
POST /api/orders/pull
→ OrderPullServiceImpl.start()
→ khóa Channel
→ kiểm tra kết nối và job trùng
→ tạo SyncLog PENDING
→ publish OrderPullRequestedEvent
→ commit transaction
→ OrderPullRequestedListener.onRequested()
→ OrderPullWorkerImpl.processAsync()
→ chọn PlatformOrderImporter
→ gọi API platform
→ Mapper tạo WriteModel
→ Persistence upsert Order và replace OrderItem
→ phát notification event
→ cập nhật SyncLog SYNCED/FAILED
→ frontend polling và refresh danh sách
```

Webhook sử dụng cùng mapper/persistence, nhưng inventory hiện có vẫn chỉ được gọi tại webhook wrapper.

---

## 3. Các file backend đã sửa

### `channel/repository/ChannelRepository.java`

Thêm:

```java
Optional<Channel> findForUpdateById(UUID id);
```

Query dùng `PESSIMISTIC_WRITE` để khóa channel khi tạo job. Nếu hai request cùng kéo một channel, request sau phải đợi request trước kiểm tra và tạo `SyncLog`, nhờ đó không tạo hai job đồng thời.

### `config/AsyncConfig.java`

Thêm:

```java
@Bean(name = "orderPullExecutor")
public Executor orderPullExecutor();
```

Executor có `corePoolSize=1`, `maxPoolSize=3`, `queueCapacity=20` và `AbortPolicy`. Executor riêng giúp job kéo đơn không chiếm thread của email hoặc webhook. Khi queue đầy, hệ thống có thể bắt exception và đánh riêng job bị từ chối thành `FAILED`.

### `order/repository/OrderRepository.java`

Đổi:

```java
void insertWebhookOrderIfAbsent(...);
```

thành:

```java
int insertPlatformOrderIfAbsent(UUID id, UUID channelId, String platform,
                                String channelName, String externalOrderId);
```

Tên mới phù hợp vì method được dùng cho cả webhook và manual pull. Giá trị trả về cho biết insert thành công (`1`) hay order đã tồn tại (`0`). SQL vẫn dùng `INSERT ... ON CONFLICT DO NOTHING`.

### `sync/repository/SyncLogRepository.java`

Thêm:

```java
List<SyncLog> findByJobTypeAndChannelIdAndStatus(...);
List<SyncLog> findActiveJobs(...);
List<SyncLog> findStalePending(...);
Optional<SyncLog> findWithChannelById(UUID id);
```

- `findByJobTypeAndChannelIdAndStatus`: chặn job trùng channel.
- `findActiveJobs`: phục hồi polling khi user quay lại Order List.
- `findStalePending`: tìm job `PENDING` quá hạn.
- `findWithChannelById`: load job kèm channel trong transaction ngắn.

### `sync/tiktok/TikTokOrderApiService.java`

Thêm:

```java
List<Map<String, Object>> getOrderDetails(Channel channel, List<String> orderIds);
OrderSearchPage searchOrders(Channel channel, OffsetDateTime from,
                             OffsetDateTime to, String pageToken);

record OrderSearchPage(List<String> orderIds, String nextPageToken) {}
```

Batch detail phục vụ manual pull, còn `OrderSearchPage` giữ danh sách order ID và page token của TikTok.

### `sync/tiktok/impl/TikTokOrderApiServiceImpl.java`

Các hàm thêm/sửa:

```java
public Map<String, Object> getOrderDetail(Channel channel, String orderId);
public List<Map<String, Object>> getOrderDetails(Channel channel, List<String> orderIds);
public OrderSearchPage searchOrders(Channel channel, OffsetDateTime from,
                                    OffsetDateTime to, String pageToken);
```

- `getOrderDetail`: delegate sang batch method với một ID.
- `getOrderDetails`: nhận tối đa 50 ID, kiểm tra response không có ID thừa/thiếu và trả kết quả đúng thứ tự request.
- `searchOrders`: gọi `/order/202309/orders/search`, gửi khoảng thời gian và đọc `next_page_token`.

Các hàm eligibility, ship package và cancel order cũ được giữ nguyên.

### `sync/webhook/impl/LazadaOrderWebhookProcessor.java`

Processor được viết lại còn các hàm:

```java
public PlatformType getPlatform();
public String process(WebhookEvent event);
private void publishTransitions(OrderImportOutcome outcome, Order order);
```

`process` chỉ điều phối:

```text
đọc trade_order_id
→ tải detail/items
→ tạo LazadaOrderStatusContext.webhook
→ mapper
→ persistence
→ inventory webhook hiện có
→ notification transition
```

Mapping và database logic hơn 300 dòng trước đây được chuyển sang các service riêng.

### `sync/webhook/impl/ShopifyOrderWebhookProcessor.java`

Các hàm:

```java
public PlatformType getPlatform();
public String process(WebhookEvent event);
```

`process` gọi `mapper.mapWebhook(eventType, payload)`, persistence, inventory và notification event. Event type vẫn được truyền vào để cancellation webhook có thể override status.

### `sync/webhook/impl/TikTokOrderWebhookWriter.java`

Các hàm:

```java
public void write(UUID eventId, Map<String, Object> detail);
private Long epoch(Object value);
```

`write` load event, tạo `TikTokOrderWriteContext`, gọi mapper/persistence và chỉ gọi inventory khi snapshot không stale.

Đã xóa:

```java
complete(event);
webhookEventRepository.save(event);
```

`WebhookEventProcessingService` trở thành nơi duy nhất hoàn tất webhook event, tránh update event hai lần.

---

## 4. Shared importing mới

### `sync/order/importing/OrderImportResult.java`

```java
public enum OrderImportResult {
    CREATED,
    UPDATED,
    UNCHANGED,
    SKIPPED_STALE
}
```

Mô tả kết quả ghi order mà không phụ thuộc HTTP hoặc webhook status.

### `sync/order/importing/OrderImportOutcome.java`

```java
public record OrderImportOutcome(
        UUID orderId,
        OrderImportResult result,
        boolean created,
        boolean paymentBecamePaid,
        boolean becameCancelled,
        boolean platformMetadataUpdated
) {}
```

Outcome không chứa JPA entity để tránh detached entity. Các transition flag giúp wrapper quyết định notification nào cần phát.

### `sync/order/importing/OrderUpsertResult.java`

```java
public record OrderUpsertResult(Order order, boolean created) {}
```

Kết quả nội bộ của bước insert và row lock.

### `sync/order/importing/OrderUpsertSupport.java`

```java
OrderUpsertResult ensureAndLock(Channel channel,
                                String externalOrderId,
                                PlatformType platform);
```

Đây là phần dùng chung duy nhất giữa ba persistence, không chứa mapping của platform.

### `sync/order/importing/impl/OrderUpsertSupportImpl.java`

Các hàm:

```java
public OrderUpsertResult ensureAndLock(Channel channel,
                                       String externalOrderId,
                                       PlatformType platform);

private void assertBelongsToChannel(Order order,
                                    Channel channel,
                                    PlatformType platform);
```

`ensureAndLock` validate input, insert-if-absent, query lại bằng `SELECT FOR UPDATE` và xác định order mới/cũ. `assertBelongsToChannel` ngăn ghi order vào sai channel hoặc platform.

---

## 5. Manual pull infrastructure mới

### `sync/order/pull/dto/OrderPullRequest.java`

```java
public record OrderPullRequest(
        @NotEmpty List<UUID> channelIds,
        OffsetDateTime from,
        OffsetDateTime to
) {}
```

Request phải có ít nhất một channel. Nếu không truyền thời gian, backend dùng 24 giờ gần nhất.

### `sync/order/pull/OrderPullBatchResult.java`

```java
public record OrderPullBatchResult(
        int total,
        int success,
        int failed,
        List<String> errors
) {}
```

Chuẩn hóa kết quả importer để worker cập nhật `SyncLog`.

### `sync/order/pull/PlatformOrderImporter.java`

```java
PlatformType platform();
OrderPullBatchResult pull(Channel channel,
                          OffsetDateTime from,
                          OffsetDateTime to);
```

Worker dispatch importer bằng `PlatformType`, không cần `if/else` phụ thuộc từng platform.

### `sync/order/pull/OrderPullService.java`

```java
List<SyncLogResponse> start(OrderPullRequest request);
SyncLogResponse get(UUID syncLogId);
List<SyncLogResponse> active();
```

Contract tạo job, xem trạng thái một job và lấy các job đang chạy.

### `sync/order/pull/OrderPullController.java`

Các hàm:

```java
public ResponseEntity<ApiResponse<List<SyncLogResponse>>> start(OrderPullRequest request);
public ResponseEntity<ApiResponse<SyncLogResponse>> get(UUID id);
public ResponseEntity<ApiResponse<List<SyncLogResponse>>> active();
```

API:

```text
POST /api/orders/pull
GET  /api/orders/pull/{id}
GET  /api/orders/pull/active
```

Controller reload job sau `start`, vì vậy nếu executor từ chối task thì frontend nhận ngay trạng thái `FAILED`. Chỉ `OWNER`, `OPERATIONS`, `SYSTEM_ADMIN` được gọi.

### `sync/order/pull/impl/OrderPullServiceImpl.java`

Các hàm:

```java
public List<SyncLogResponse> start(OrderPullRequest request);
public SyncLogResponse get(UUID id);
public List<SyncLogResponse> active();
private void validateRange(OffsetDateTime from, OffsetDateTime to);
private void validatePlatform(Channel channel);
private boolean missing(Channel channel, String key);
private void expireStale(UUID channelId);
```

- `start`: mặc định 24 giờ, khóa channel, kiểm tra credential, expire job cũ, chặn job trùng, tạo `SyncLog` và publish event.
- `get`: chỉ cho phép đọc SyncLog thuộc `ORDER_PULL`.
- `active`: trả các job `ORDER_PULL/PENDING`.
- `validateRange`: chặn thời gian đảo ngược hoặc vượt quá 7 ngày.
- `validatePlatform`: chỉ nhận Lazada/Shopify/TikTok và kiểm tra `shopDomain`/`shopCipher`.
- `expireStale`: đánh job `PENDING` quá 30 phút thành `FAILED`.

### `sync/order/pull/OrderPullRequestedEvent.java`

```java
public record OrderPullRequestedEvent(
        UUID syncLogId,
        OffsetDateTime from,
        OffsetDateTime to
) {}
```

Event chỉ mang dữ liệu cần thiết để worker chạy sau commit.

### `sync/order/pull/impl/OrderPullRequestedListener.java`

```java
public void onRequested(OrderPullRequestedEvent event);
```

Dùng `@TransactionalEventListener(AFTER_COMMIT)` để chỉ submit async khi `SyncLog` đã commit. Queue đầy sẽ gọi `markQueueRejected` thay vì làm lỗi toàn bộ HTTP request.

### `sync/order/pull/OrderPullWorker.java`

```java
void processAsync(UUID syncLogId,
                  OffsetDateTime from,
                  OffsetDateTime to);

void markQueueRejected(UUID syncLogId);
```

Interface riêng bảo đảm Spring gọi qua proxy để `@Async` có hiệu lực.

### `sync/order/pull/impl/OrderPullWorkerImpl.java`

Các hàm:

```java
public void processAsync(UUID syncLogId,
                         OffsetDateTime from,
                         OffsetDateTime to);

public void markQueueRejected(UUID syncLogId);
private Map<PlatformType, PlatformOrderImporter> importerMap();
private String summarize(List<String> errors);
private String message(Exception e);
```

`processAsync` chạy bằng `orderPullExecutor`, load job, chọn importer, chạy pull và cập nhật `SYNCED/FAILED`. `summarize` giới hạn 20 lỗi và tối đa 4000 ký tự. Worker không inject inventory.

### `sync/order/pull/OrderPullJobContext.java`

```java
public record OrderPullJobContext(UUID syncLogId, Channel channel) {}
```

Chứa dữ liệu tối thiểu worker cần sau transaction đọc.

### `sync/order/pull/OrderPullJobStore.java`

```java
OrderPullJobContext load(UUID id);
void complete(UUID id, SyncStatus status,
              int total, int success, int failed,
              String error);
```

Tách transaction quản lý job khỏi worker async.

### `sync/order/pull/impl/OrderPullJobStoreImpl.java`

Các hàm:

```java
public OrderPullJobContext load(UUID id);
public void complete(UUID id, SyncStatus status,
                     int total, int success, int failed,
                     String error);
```

`load` dùng transaction đọc ngắn và fetch channel. `complete` dùng `REQUIRES_NEW`, nên trạng thái job vẫn được commit nếu transaction import order trước đó rollback.

### `sync/order/pull/ManualOrderPostImportService.java`

```java
void publish(OrderImportOutcome outcome);
```

Contract phát notification sau khi persistence đã hoàn tất.

### `sync/order/pull/impl/ManualOrderPostImportServiceImpl.java`

```java
public void publish(OrderImportOutcome outcome);
```

Load order theo UUID và phát `OrderCreatedEvent`, `OrderPaidEvent`, `OrderCancelledEvent` dựa trên transition flags. Service không gọi inventory.

---

## 6. Lazada order package mới

### `sync/lazada/order/LazadaOrderWriteModel.java`

```java
public record LazadaOrderWriteModel(...) {
    public record Item(String externalVariantId,
                       String sku,
                       String name,
                       int quantity,
                       BigDecimal unitPrice,
                       BigDecimal discountAmount) {}
}
```

Write model là snapshot đã chuẩn hóa. Mapper tạo model, persistence mới được phép truy cập database.

### `sync/lazada/order/LazadaOrderStatusContext.java`

Các hàm:

```java
public static LazadaOrderStatusContext webhook(Map<String, Object> payload);
public static LazadaOrderStatusContext manual(Map<String, Object> orderData);
private static void add(List<String> target, Object value);
```

Webhook đọc `data.reverse_status/order_status`; manual đọc `orderData.statuses`. Việc tách context tránh manual pull đọc nhầm cấu trúc webhook.

### `sync/lazada/order/LazadaOrderApiService.java`

```java
Map<String, Object> getOrder(Channel channel, String orderId);
List<Map<String, Object>> getOrderItems(Channel channel, String orderId);
List<String> listOrderIds(Channel channel,
                          OffsetDateTime from,
                          OffsetDateTime to,
                          int offset,
                          int limit);
```

Contract tương ứng `/order/get`, `/order/items/get`, `/orders/get`.

### `sync/lazada/order/impl/LazadaOrderApiServiceImpl.java`

Các hàm:

```java
public Map<String, Object> getOrder(Channel channel, String orderId);
public List<Map<String, Object>> getOrderItems(Channel channel, String orderId);
public List<String> listOrderIds(Channel channel, OffsetDateTime from,
                                 OffsetDateTime to, int offset, int limit);
private Map<String, Object> get(Channel channel, String path,
                                Map<String, String> params);
private List<Map<String, Object>> maps(Object value);
private String text(Object value);
```

Ba hàm public gọi API tương ứng. `get` dùng authorized client để nhận token hợp lệ và validate response code. Các helper chuẩn hóa JSON.

### `sync/lazada/order/LazadaOrderMapper.java`

```java
LazadaOrderWriteModel map(LazadaOrderStatusContext context,
                          Map<String, Object> orderData,
                          List<Map<String, Object>> items);
```

### `sync/lazada/order/impl/LazadaOrderMapperImpl.java`

Các hàm chính:

```java
public LazadaOrderWriteModel map(...);
private LazadaOrderWriteModel.Item mapItem(...);
private OrderStatus status(...);
private String paymentStatus(...);
private Map<String, Object> shippingAddress(...);
private BigDecimal subtotal(...);
private BigDecimal discount(...);
private String currency(...);
private String tracking(...);
```

- `map`: validate order ID/items và tạo snapshot.
- `status`: ưu tiên reverse cancel, sau đó xét order/item statuses.
- `paymentStatus`: xét `stage_pay_status`, COD, refund và fulfillment.
- Các hàm còn lại chuẩn hóa địa chỉ, tiền, currency và tracking.

Mapper không inject repository.

### `sync/lazada/order/LazadaOrderPersistenceService.java`

```java
OrderImportOutcome write(Channel channel, LazadaOrderWriteModel model);
Order getOrder(OrderImportOutcome outcome);
```

### `sync/lazada/order/impl/LazadaOrderPersistenceServiceImpl.java`

Các hàm:

```java
public OrderImportOutcome write(Channel channel, LazadaOrderWriteModel model);
public Order getOrder(OrderImportOutcome outcome);
private ResolvedItem resolve(Channel channel, LazadaOrderWriteModel.Item item);
private void setTextIfPresent(String value, Consumer<String> setter);
private boolean shouldReplaceAddress(Order order, Map<String, Object> incoming);
```

`write` resolve toàn bộ item trước khi delete, insert/lock order, chỉ đổi `statusChangedAt` khi status thực sự đổi, giữ dữ liệu cũ nếu incoming rỗng/masked, save order rồi replace items trong cùng transaction.

### `sync/lazada/order/LazadaOrderImporter.java`

Marker interface kế thừa `PlatformOrderImporter`, giữ interface và implementation ở package riêng.

### `sync/lazada/order/impl/LazadaOrderImporterImpl.java`

Các hàm:

```java
public PlatformType platform();
public OrderPullBatchResult pull(Channel channel,
                                 OffsetDateTime from,
                                 OffsetDateTime to);
private String message(Exception e);
```

`pull` phân trang 100 order ID, sau đó gọi detail/items cho từng order. Lỗi page dùng `[PAGE]`; lỗi một order dùng `[ORDER]` và không dừng toàn bộ job.

---

## 7. Shopify order package mới

### `sync/shopify/order/ShopifyOrderWriteModel.java`

Record chứa snapshot Shopify và nested `Item`, không chứa JPA entity.

### `sync/shopify/order/ShopifyOrderPage.java`

```java
public record ShopifyOrderPage(
        List<Map<String, Object>> orders,
        String nextPageInfo
) {}
```

Giữ body và cursor từ HTTP `Link` header.

### `sync/shopify/order/ShopifyOrderApiClient.java`

```java
ShopifyOrderPage firstPage(Channel channel,
                           OffsetDateTime from,
                           OffsetDateTime to);
ShopifyOrderPage nextPage(Channel channel, String pageInfo);
```

### `sync/shopify/order/impl/ShopifyOrderApiClientImpl.java`

Các hàm:

```java
public ShopifyOrderPage firstPage(...);
public ShopifyOrderPage nextPage(...);
private ShopifyOrderPage execute(Channel channel, URI uri);
private String baseUrl(Channel channel);
private String nextPageInfo(String link);
private List<Map<String, Object>> maps(Object value);
```

Trang đầu gửi status/time/limit; trang sau chỉ gửi `page_info` và limit. `execute` lấy access token qua `ChannelTokenService`. `nextPageInfo` parse `rel="next"`. `baseUrl` dùng Shopify domain normalizer hiện có.

### `sync/shopify/order/ShopifyOrderMapper.java`

```java
ShopifyOrderWriteModel mapWebhook(String eventType,
                                  Map<String, Object> payload);
ShopifyOrderWriteModel mapManual(Map<String, Object> payload);
```

### `sync/shopify/order/impl/ShopifyOrderMapperImpl.java`

Các hàm chính:

```java
public ShopifyOrderWriteModel mapWebhook(...);
public ShopifyOrderWriteModel mapManual(...);
private ShopifyOrderWriteModel map(...);
private ShopifyOrderWriteModel.Item item(...);
private OrderStatus status(...);
private boolean hasDeliveredFulfillment(...);
private String payment(...);
private String buyerName(...);
private String buyerPhone(...);
private BigDecimal shippingFee(...);
private String tracking(...);
```

Hai entry point dùng chung `map`, nhưng chỉ webhook truyền event type để cancellation topic override. Manual dựa vào `cancelled_at`, fulfillment và financial status. Rule `partial → SHIPPED` của code cũ được giữ nguyên.

### `sync/shopify/order/ShopifyOrderPersistenceService.java`

```java
OrderImportOutcome write(Channel channel, ShopifyOrderWriteModel model);
Order getOrder(OrderImportOutcome outcome);
```

### `sync/shopify/order/impl/ShopifyOrderPersistenceServiceImpl.java`

Các hàm:

```java
public OrderImportOutcome write(...);
public Order getOrder(...);
private ResolvedItem resolve(...);
private void setText(...);
private boolean shouldReplaceAddress(...);
```

Logic transaction tương tự Lazada nhưng resolve variant bằng Shopify external variant ID. Snapshot cost price lấy từ local variant. Không thêm stale guard để giữ behavior Shopify hiện tại.

### `sync/shopify/order/ShopifyOrderImporter.java`

Marker interface kế thừa `PlatformOrderImporter`.

### `sync/shopify/order/impl/ShopifyOrderImporterImpl.java`

Các hàm:

```java
public PlatformType platform();
public OrderPullBatchResult pull(Channel channel,
                                 OffsetDateTime from,
                                 OffsetDateTime to);
private String message(Exception e);
```

`pull` đọc first page và lần lượt các cursor. Lỗi một order không dừng page; lỗi page sau vẫn giữ đúng success count của các page đã commit.

---

## 8. TikTok order package mới

### `sync/tiktok/order/TikTokOrderWriteSource.java`

```java
public enum TikTokOrderWriteSource {
    WEBHOOK,
    MANUAL_PULL
}
```

Cho persistence biết stale snapshot có reverse metadata từ webhook cần merge hay không.

### `sync/tiktok/order/TikTokOrderWriteContext.java`

```java
public record TikTokOrderWriteContext(
        Channel channel,
        TikTokOrderWriteSource source,
        String webhookEventType,
        Map<String, Object> reverseData,
        Long webhookTimestamp
) {
    public static TikTokOrderWriteContext manual(Channel channel);
}
```

Manual context không giả lập `WebhookEvent`. Reverse map được copy để tránh caller thay đổi sau khi tạo context.

### `sync/tiktok/order/TikTokOrderWriteModel.java`

Record chứa snapshot order, raw status, update time, metadata và nested item.

### `sync/tiktok/order/TikTokOrderMapper.java`

```java
TikTokOrderWriteModel map(Map<String, Object> detail);
```

### `sync/tiktok/order/impl/TikTokOrderMapperImpl.java`

Các hàm chính:

```java
public TikTokOrderWriteModel map(...);
private TikTokOrderWriteModel.Item item(...);
private OrderStatus status(...);
private String paymentStatus(...);
private BigDecimal subtotal(...);
private BigDecimal discount(...);
private String tracking(...);
private boolean hasAddress(...);
```

`map` validate ID/items và chuẩn hóa payment/address/packages. `status` map raw TikTok status sang OSMS. `subtotal` ưu tiên original product total, fallback tổng item rồi mới dùng sub-total cộng discount.

### `sync/tiktok/order/TikTokOrderMetadataMapper.java`

```java
Map<String, Object> merge(Map<String, Object> existing,
                          TikTokOrderWriteContext context,
                          TikTokOrderWriteModel model,
                          boolean includeSnapshot);
```

### `sync/tiktok/order/impl/TikTokOrderMetadataMapperImpl.java`

Các hàm:

```java
public Map<String, Object> merge(...);
private String text(Map<String, Object> value, String... keys);
```

`merge` deep-merge nhánh `platformMetadata.tiktok`. Reverse webhook cập nhật cancellation timeline. Snapshot lưu raw status, update time, payment, packages và trạng thái địa chỉ. `CANCELLED` sẽ xóa pending confirmation.

### `sync/tiktok/order/TikTokOrderPersistenceService.java`

```java
OrderImportOutcome write(TikTokOrderWriteContext context,
                         TikTokOrderWriteModel model);
Order getOrder(OrderImportOutcome outcome);
```

### `sync/tiktok/order/impl/TikTokOrderPersistenceServiceImpl.java`

Các hàm:

```java
public OrderImportOutcome write(...);
public Order getOrder(...);
private ResolvedItem resolve(...);
private void setText(...);
private boolean shouldReplaceAddress(...);
private Long epoch(Object value);
```

`write` insert/lock order và stale-check bằng `lastOrderUpdateTime`:

```text
incoming < stored
→ không replace Order/OrderItem
→ webhook reverse vẫn được merge metadata

incoming == stored
→ map lại để bổ sung field thiếu

incoming > stored
→ update snapshot đầy đủ
```

Variant được tìm theo external ID rồi fallback seller SKU. Toàn bộ item được resolve trước khi xóa snapshot cũ.

### `sync/tiktok/order/TikTokOrderImporter.java`

Marker interface kế thừa `PlatformOrderImporter`.

### `sync/tiktok/order/impl/TikTokOrderImporterImpl.java`

Các hàm:

```java
public PlatformType platform();
public OrderPullBatchResult pull(Channel channel,
                                 OffsetDateTime from,
                                 OffsetDateTime to);
private List<String> loadIds(Channel channel,
                             OffsetDateTime from,
                             OffsetDateTime to);
private String message(Exception e);
```

`loadIds` search theo page token, loại ID trùng và chặn token lặp. `pull` chia batch tối đa 50 ID, luôn gọi Get Order Detail rồi import từng order. Manual không bypass stale guard và không gọi inventory.

---

## 9. Frontend

### `frontend/src/api/orderApi.js`

Thêm:

```javascript
pullOrders(data)
getPullJob(id)
getActivePullJobs()
```

Ba hàm lần lượt gọi POST tạo job, GET một job và GET danh sách active job.

### `frontend/src/features/order/components/PullOrdersModal.jsx`

Các hàm:

```javascript
const localDateTime = (date) => ...
const PullOrdersModal = ({ open, channels, submitting, onClose, onSubmit }) => ...
const toggle = (id) => ...
const submit = (event) => ...
```

Modal lọc channel `CONNECTED` của ba platform, mặc định 24 giờ, cho chọn nhiều channel, chuyển datetime-local thành ISO và chặn submit khi chưa chọn channel.

### `frontend/src/features/order/components/PullOrdersModal.module.css`

File mới chỉ chứa CSS cho overlay, modal, channel checkbox, datetime range, footer và responsive mobile. Không chứa logic nghiệp vụ.

### `frontend/src/features/order/pages/OrderListPage.jsx`

Các phần logic thêm:

```javascript
const recover = async () => ...
const handlePullOrders = async (payload) => ...
useEffect(() => polling mỗi 5 giây, ...)
```

- `recover`: merge job ID trong `sessionStorage` với `/orders/pull/active`.
- Polling: cập nhật trạng thái, dừng sau ba lỗi liên tiếp hoặc sau 35 phút nhưng không hủy backend job.
- `handlePullOrders`: tạo job, lưu state, đóng modal và hiển thị toast.
- Khi job terminal, refresh order list và statistics.
- Header có nút “Kéo đơn” và số job đang `PENDING`.

### `frontend/src/features/order/pages/OrderListPage.module.css`

Git hiện báo file modified nhưng `git diff` hiện tại không có thay đổi nội dung. Manual pull không thêm class vào file này vì modal sử dụng CSS Module riêng.

---

## 10. Những phần không thay đổi

- Không sửa package `fu.osms.inventory`.
- Manual importer/persistence không inject `PlatformOrderInventoryService`.
- Không tạo `WebhookEvent` cho manual pull.
- Không sửa migration database.
- Không tạo hoặc sửa automated test.

## 11. Xác minh

- Backend Maven compile thành công.
- Frontend `npm run build` thành công.
- Vite chỉ còn cảnh báo bundle lớn hơn 500 kB.
