# Báo Cáo Implementation Nghiệp Vụ Trả Hàng V1

## 1. Phạm Vi Đã Thực Hiện

Đã thêm một module nghiệp vụ trả hàng độc lập với `Order`.

Luồng tổng quát:

```text
Webhook platform
-> nhận diện physical return
-> chuẩn hóa thành OrderReturnSnapshot
-> khóa Order và upsert OrderReturn/OrderReturnItem
-> Owner/Sales duyệt hoặc từ chối
-> Operations nhận và kiểm hàng
-> gọi API platform xác nhận đã nhận hàng
-> platform xác nhận refund
-> cập nhật projection PaymentStatus
-> cộng hàng đạt vào kho đúng một lần
-> push available lên các platform
-> COMPLETED
```

Các nguyên tắc đã giữ:

- `Order` gốc không bị đổi thành một status return mới.
- Không cộng hàng hỏng hoặc hàng thiếu vào kho.
- Payment và nhập kho chạy ở hai transaction khác nhau.
- Timeout API không tự gọi lại mù.
- Webhook lặp/cũ không cộng kho hoặc tạo return item trùng.
- Shopify Return dùng GraphQL; order/product/inventory Shopify hiện tại giữ nguyên.
- Không thêm scheduler, RabbitMQ hoặc manual pull return.
- Không sửa code trong package `fu.osms.inventory`.

---

## 2. Database Và External Line-Item Identity

### 2.1 `backend/sql/order_returns_v1.sql`

Đây là script SQL chạy thủ công, không được đăng ký auto-migration.

Script thêm:

```sql
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS external_item_id VARCHAR(200);

CREATE UNIQUE INDEX IF NOT EXISTS uq_order_item_external
    ON order_items(order_id, external_item_id)
    WHERE external_item_id IS NOT NULL;
```

Mục đích:

- Ba order persistence hiện xóa rồi tạo lại `OrderItem`.
- Return từ platform chỉ gửi external line-item ID.
- Cần lưu external ID ổn định để return item tìm lại đúng `OrderItem`.

Script tạo `order_returns` để lưu header/lifecycle và `order_return_items` để lưu từng sản phẩm trả.

Hai unique identity quan trọng:

```sql
UNIQUE (channel_id, external_return_id)
UNIQUE (return_id, external_identity_key)
```

`external_identity_key` được xây:

```text
Có externalReturnItemId -> RETURN:{id}
Không có nhưng có externalOrderItemId -> ORDER:{id}
Thiếu cả hai -> return INVALID/FAILED, không lưu item mơ hồ
```

Constraint kiểm hàng:

```sql
received_quantity = restockable_quantity + damaged_quantity
received_quantity + missing_quantity = approved_quantity
```

Trước khi kiểm hàng, bốn field trên được phép `NULL` để phân biệt “chưa kiểm” với số lượng bằng 0.

### 2.2 `OrderItem.java`

Thêm field:

```java
@Column(name = "external_item_id", length = 200)
private String externalItemId;
```

Field này là identity từ Shopify/Lazada/TikTok, không phải SKU.

### 2.3 `OrderItemRepository.java`

Thêm:

```java
Optional<OrderItem> findByOrderIdAndExternalItemId(UUID orderId, String externalItemId);
```

Method phục vụ liên kết return item với order item local.

### 2.4 `OrderItemMapper.java`

Thêm:

```java
@Mapping(target = "externalItemId", ignore = true)
```

Lý do: đơn thủ công từ UI không được tự gán external platform identity. External ID chỉ được platform-specific persistence ghi.

### 2.5 Ba platform order mapper/persistence

Các file sửa:

- `sync/shopify/order/ShopifyOrderWriteModel.java`
- `sync/shopify/order/impl/ShopifyOrderMapperImpl.java`
- `sync/shopify/order/impl/ShopifyOrderPersistenceServiceImpl.java`
- `sync/lazada/order/LazadaOrderWriteModel.java`
- `sync/lazada/order/impl/LazadaOrderMapperImpl.java`
- `sync/lazada/order/impl/LazadaOrderPersistenceServiceImpl.java`
- `sync/tiktok/order/TikTokOrderWriteModel.java`
- `sync/tiktok/order/impl/TikTokOrderMapperImpl.java`
- `sync/tiktok/order/impl/TikTokOrderPersistenceServiceImpl.java`

Mỗi `WriteModel.Item` được bổ sung `externalItemId`.

Mapping nguồn:

```text
Shopify: id hoặc line_item_id
Lazada: order_item_id / order_line_id / trade_order_line_id
TikTok: order_line_item_id / line_item_id / id
```

Persistence thêm:

```java
.externalItemId(item.externalItemId())
```

Kết quả: khi order webhook cập nhật và replace item, external identity vẫn được lưu lại để return relink đúng.

---

## 3. Domain Return Mới

### 3.1 `OrderReturnStatus.java`

Các business state:

```java
PENDING_APPROVAL
REJECTED
AWAITING_RETURN
RETURN_IN_TRANSIT
INSPECTED
PLATFORM_PROCESSING
PENDING_STOCK
COMPLETED
FAILED
```

Ý nghĩa:

- `PENDING_APPROVAL`: chờ Owner/Sales duyệt.
- `AWAITING_RETURN`: đã duyệt, chờ khách gửi.
- `RETURN_IN_TRANSIT`: hàng đang hoàn về.
- `INSPECTED`: Operations đã nhập kết quả kiểm.
- `PLATFORM_PROCESSING`: đã gọi sàn xử lý việc nhận/refund.
- `PENDING_STOCK`: platform đã hoàn tất nhưng nhập kho OSMS lỗi.
- `COMPLETED`: payment projection và nhập hàng đạt đã hoàn thành.
- `FAILED`: dữ liệu return không đủ identity hoặc sai quantity.

### 3.2 `ReturnAction.java`

```java
APPROVE
REJECT
PROCESS
```

Đây là thao tác gửi lên platform, tách khỏi business status.

### 3.3 `ReturnActionState.java`

```java
IDLE
PROCESSING
UNKNOWN
FAILED
```

- `UNKNOWN` dùng cho timeout hoặc không chắc platform đã xử lý.
- Khi `UNKNOWN`, hệ thống không tự gọi API lần hai.

### 3.4 `ReturnDataValidationState.java`

```java
VALID
INVALID
```

Return `INVALID` vẫn được lưu để điều tra nhưng không được tính vào quota trả tích lũy và không được duyệt/process.

### 3.5 `OrderReturn.java`

Entity header chứa:

- Liên kết `Order`, `Channel`, kho nhận cố định.
- `externalReturnId`, platform status, event time.
- Business status và validation state.
- Action gần nhất, state, request ID và lỗi.
- `approvedAt`, `inspectedAt`, `refundConfirmedAt`, `inventoryPostedAt`.
- `@Version` để chống lost update ngoài pessimistic lock.

Hai mốc idempotency chính:

```text
lastWebhookEventId -> bỏ webhook trùng
inventoryPostedAt  -> không cộng kho lần hai
```

### 3.6 `OrderReturnItem.java`

Entity item chứa:

- Identity external return item và external order item.
- Liên kết `OrderItem` và `ProductVariant`.
- Quantity yêu cầu/duyệt/nhận/đạt/hỏng/thiếu/hoàn tiền.
- Snapshot SKU, tên, giá bán và giá vốn.

Snapshot giúp vẫn đọc được lịch sử return nếu catalog sau đó đổi tên/giá.

---

## 4. Write Model Và Platform Boundary

### 4.1 `OrderReturnSnapshot.java`

Đây là input chuẩn hóa từ platform vào domain:

```java
public record OrderReturnSnapshot(
    String externalReturnId,
    String externalOrderId,
    String platformStatus,
    OrderReturnStatus status,
    OffsetDateTime platformUpdatedAt,
    String webhookEventId,
    boolean refundOnly,
    boolean refundConfirmed,
    List<Item> items,
    Map<String, Object> metadata
) {}
```

Domain persistence không đọc raw Shopify/Lazada/TikTok payload.

### 4.2 `ReturnActionContext.java`

Context immutable được chụp trước khi gọi platform API:

```java
returnId
channelId
platform
externalReturnId
action
requestId
inspection items
```

API được gọi ngoài DB transaction nên không truyền JPA entity ra ngoài transaction.

### 4.3 `ReturnPlatformActionResult.java`

```java
public record ReturnPlatformActionResult(
    OrderReturnSnapshot snapshot,
    boolean applied
) {}
```

Gateway trả snapshot mới nhất và cho biết action đã được platform áp dụng hay chưa.

### 4.4 `OrderReturnPlatformGateway.java`

Interface chung:

```java
PlatformType platform();
ReturnPlatformActionResult approve(ReturnActionContext context);
ReturnPlatformActionResult reject(ReturnActionContext context, String reason);
ReturnPlatformActionResult process(ReturnActionContext context);
ReturnPlatformActionResult check(ReturnActionContext context);
```

Service orchestration chỉ chọn gateway theo platform; GraphQL/REST field không rò vào domain.

---

## 5. Persistence Và Validation

### `OrderReturnPersistenceServiceImpl.java`

#### `upsert(Channel, OrderReturnSnapshot)`

Đây là hàm ghi return trung tâm.

Luồng:

```text
refundOnly=true
-> log UNSUPPORTED_REFUND_ONLY
-> không tạo return, không sửa kho/payment

physical return
-> validate externalReturnId/externalOrderId
-> lock Order theo channel + externalOrderId
-> lock hoặc tạo OrderReturn
-> duplicate/stale check
-> merge header
-> map toàn bộ items trong memory
-> validate identity và quantity tích lũy
-> replace item snapshot
-> save state
-> publish OrderReturnChangedEvent
```

#### `mapItems(...)`

Nhiệm vụ:

1. Tạo stable identity `RETURN:` hoặc `ORDER:`.
2. Tìm `OrderItem` theo `externalItemId`.
3. Nếu thiếu external match, fallback SKU chỉ khi SKU tìm được đúng một item.
4. Giữ inspection cũ khi webhook replace snapshot.
5. Thiếu identity/mapping thì trả validation error, không gán bừa.

Đoạn chính:

```java
String identity = identity(
    source.externalReturnItemId(),
    source.externalOrderItemId()
);

OrderItem orderItem = byExternalId.get(source.externalOrderItemId());
```

#### `cumulativeValidationError(...)`

Tính tổng `approvedQuantity` của các return hợp lệ theo từng `OrderItem`.

```text
Tổng của từng item <= OrderItem.quantity
```

Không so tổng toàn order vì SKU A dư và SKU B thiếu vẫn có thể cho cùng tổng sai.

#### `applyTransition(...)`

- Chỉ cho tiến trạng thái theo rank.
- `REJECTED` và `COMPLETED` là terminal.
- Webhook thường không thể làm terminal state lùi.
- Khi vào `AWAITING_RETURN`, ghi `approvedAt` một lần.

#### `isDuplicate(...)` và `isStale(...)`

```text
same webhookEventId -> bỏ qua
incoming platformUpdatedAt < stored -> bỏ qua
```

---

## 6. Action Orchestration Và Manual Retry

### 6.1 `OrderReturnActionStateService.java`

#### `beginNew(returnId, action)`

Chạy `REQUIRES_NEW`:

```text
lock return
-> validate action phù hợp business state
-> chặn PROCESSING/UNKNOWN đang tồn tại
-> lastAction=action
-> actionState=PROCESSING
-> actionRequestId=UUID
-> commit
```

DB commit trước khi gọi platform. Nếu backend chết sau API call, request ID vẫn còn để user kiểm tra lại.

#### `beginRetry(returnId)`

- Chỉ retry khi action đang `FAILED`.
- Giữ nguyên `actionRequestId`.
- Không sinh idempotency key mới cho cùng một action.

#### `contextForCheck(returnId)`

Chụp context cho nút **Kiểm tra lại**, không thay state trước API read.

#### `markIdle`, `markFailed`, `markUnknown`

Chạy transaction riêng để lưu kết quả API.

Nếu PROCESS lỗi chắc chắn:

```text
actionState=FAILED
business status trở về INSPECTED
```

### 6.2 `OrderReturnActionServiceImpl.java`

#### `execute(returnId, action, reason)`

```text
stateService.beginNew()
-> commit action cycle
-> chọn gateway theo platform
-> gọi API ngoài transaction
-> upsert snapshot
-> markIdle
```

#### `check(returnId)`

Khi action `UNKNOWN`:

```text
gateway.check()
-> platform đã áp dụng: persist snapshot + IDLE
-> platform chưa áp dụng: FAILED, cho phép retry
-> vẫn timeout/không rõ: giữ UNKNOWN
```

#### `retry(returnId)`

```text
beginRetry() giữ requestId cũ
-> luôn check platform trước
-> đã xử lý: không gọi action lần hai
-> chưa xử lý: mới gọi lại action
```

#### `isUnknown(Throwable)`

`ResourceAccessException` hoặc `SocketTimeoutException` được phân loại `UNKNOWN`, không phải lỗi chắc chắn.

### 6.3 Phân quyền retry động

Trong `OrderReturnServiceImpl.requireActionRole()`:

```text
APPROVE/REJECT -> OWNER hoặc SALES
PROCESS        -> OWNER hoặc OPERATIONS
```

Controller có broad pre-authorization, service kiểm tra action cụ thể để Postman cũng không vượt quyền.

---

## 7. Inspection, Payment Và Inventory

### 7.1 `OrderReturnServiceImpl.inspect(...)`

Luồng transaction:

```text
lock return
-> chỉ nhận AWAITING_RETURN/RETURN_IN_TRANSIT
-> yêu cầu request có đủ mọi return item
-> validate:
   received = restockable + damaged
   received + missing = approved
-> resolve Kho mặc định đa sàn
-> lưu cố định warehouse vào return
-> save INSPECTED
-> publish OrderReturnInspectedEvent
```

Kho được lưu tại thời điểm inspect, không resolve lại khi restock.

### 7.2 `OrderReturnInspectionListener.onInspected(...)`

Chạy `AFTER_COMMIT`:

```java
actionService.execute(event.returnId(), ReturnAction.PROCESS, null);
```

API platform không được gọi khi transaction inspection chưa commit.

### 7.3 `OrderReturnPaymentServiceImpl.projectPayment(...)`

Chạy `REQUIRES_NEW`.

Luồng:

```text
lock Return
-> chỉ chạy khi refundConfirmedAt có giá trị
-> lock Order
-> lấy mọi valid return item đã được platform xác nhận refund
-> cộng refundedQuantity theo từng OrderItem
-> mọi OrderItem hoàn đủ mới set PaymentStatus.REFUNDED
```

Partial return vẫn giữ payment `PAID`.

Payment commit độc lập, nên nhập kho lỗi không rollback sự thật tài chính từ platform.

### 7.4 `PaymentStatus.java` và `OrderServiceImpl.updatePaymentStatus()`

Thêm:

```java
REFUNDED
```

Chặn user set thủ công:

```java
if (paymentStatus == PaymentStatus.REFUNDED) {
    throw new AppException(ErrorCode.ORDER_REFUND_SYSTEM_MANAGED);
}
```

Chỉ return workflow được projection sang `REFUNDED`.

### 7.5 `OrderReturnInventoryPostingServiceImpl.postIfReady(...)`

Chạy `REQUIRES_NEW`.

Điều kiện:

```text
inspectedAt != null
refundConfirmedAt != null
inventoryPostedAt == null
chưa có INBOUND referenceType=ORDER_RETURN
```

Luồng mỗi item đạt:

```text
lock InventoryItem theo warehouse + variant
-> before = quantityOnHand
-> after = before + restockableQuantity
-> save InventoryItem
-> tạo InventoryTransaction INBOUND
   referenceType=ORDER_RETURN
   referenceId=returnId
```

Sau tất cả item:

```text
inventoryPostedAt=now
status=COMPLETED
-> schedulePushAvailableStock(changedVariantIds)
```

Không thay `reservedQuantity`.
Không cộng `damagedQuantity` hoặc `missingQuantity`.

#### `markPending(returnId, error)`

Nếu restock lỗi:

```text
status=PENDING_STOCK
lastSyncError=SKU/kho/variant gây lỗi
```

Operations có thể sửa mapping/inventory rồi bấm **Thử nhập kho**.

### 7.6 `OrderReturnWorkflowListener.onChanged(...)`

Chạy `AFTER_COMMIT` sau mỗi snapshot:

```text
Transaction A: projectPayment()
Transaction B: postIfReady()
```

Inventory lỗi được bắt và chuyển `PENDING_STOCK`; payment đã commit không bị rollback.

---

## 8. Shopify Return GraphQL

### 8.1 `ShopifyReturnGraphQlClient.java`

Public methods:

```java
Map<String, Object> getReturn(UUID channelId, String returnGid);
Map<String, Object> approve(UUID channelId, String returnGid);
Map<String, Object> decline(UUID channelId, String returnGid, String reason);
Map<String, Object> process(UUID channelId, String returnGid, ReturnActionContext context);
```

### 8.2 `ShopifyReturnGraphQlClientImpl.java`

Tái sử dụng:

- `ShopifyApiClient.executeGraphQl()`.
- `ChannelTokenService`.
- `ShopifyShopDomainNormalizer`.
- Channel metadata `shopDomain`.

Không tạo HTTP/token client mới.

Các GraphQL operation:

```text
return(id: ...)
returnApproveRequest
returnDeclineRequest
returnProcess
```

`process()` gửi return line item và quantity đã kiểm. Không gửi `RESTOCKED`; OSMS tự nhập tồn.

`execute(...)` parse `userErrors`. Có `userErrors` thì throw lỗi chắc chắn để action thành `FAILED`.

### 8.3 `ShopifyReturnSnapshotMapper.java`

Mapping:

```text
Return.id -> externalReturnId
Order.legacyResourceId -> externalOrderId
ReturnLineItem.id -> externalReturnItemId
LineItem.id/legacyResourceId -> externalOrderItemId
```

Shopify status:

```text
DECLINED/CANCELED -> REJECTED
OPEN              -> AWAITING_RETURN
CLOSED/COMPLETED  -> PLATFORM_PROCESSING + refundConfirmed
khác              -> PENDING_APPROVAL
```

### 8.4 `ShopifyOrderReturnGateway.java`

- `approve()` gọi `returnApproveRequest`.
- `reject()` gọi `returnDeclineRequest`.
- `process()` gọi `returnProcess`.
- `check()` gọi query return và đối chiếu remote status với action gần nhất.

Sau `returnProcess`, code không tự coi refund hoàn tất. Chỉ remote status hoặc refund webhook mới xác nhận.

### 8.5 `ShopifyReturnWebhookProcessor.java`

`supports()` chỉ kiểm tra platform/topic, không gọi API.

`process()`:

```text
extract return GID
-> GraphQL getReturn
-> mapper.map
-> persistenceService.upsert
```

Shopify thực tế cũng có thể gửi return bên trong webhook:

```text
eventType = ORDERS_UPDATED
payload.returns = [...]
```

Với dạng này processor:

```text
chạy ShopifyOrderWebhookProcessor trước
-> đọc toàn bộ payload.returns[]
-> đổi numeric return ID thành gid://shopify/Return/{id}
-> query GraphQL từng return
-> upsert OrderReturn
```

Do đó vẫn giữ cập nhật order cũ và đồng thời tạo return.

### 8.6 `ShopifyWebhookSubscriptionServiceImpl.java`

Giữ REST subscriptions order/product/inventory.

Thêm riêng:

```java
registerGraphQlReturnWebhooks(...)
graphQlWebhookSubscriptions(...)
deleteGraphQlWebhook(...)
graphQlReturnTopics()
```

`registerGraphQlReturnWebhooks()`:

1. Query subscription hiện có.
2. Không tạo trùng topic/address.
3. Dùng `webhookSubscriptionCreate`.
4. Lưu GraphQL GID vào webhook metadata.

`unregisterWebhooks()` nhận biết GID và dùng `webhookSubscriptionDelete`.

---

## 9. Lazada Return

### 9.1 `LazadaReturnSnapshotMapper.java`

`map(...)` hỗ trợ alias snake/camel case:

```text
reverse_order_id / reverseOrderId
reverse_order_line_id / reverseOrderLineId
trade_order_id / tradeOrderId
trade_order_line_id / tradeOrderLineId
```

`isPhysicalReturn(...)` phân biệt return vật lý với cancel/refund-only bằng reverse type/status.

### 9.2 `LazadaReturnWebhookProcessor.java`

Chỉ nhận:

```text
platform=LAZADA
eventType=REVERSE_ORDER
payload được mapper xác định là physical return
```

Reverse cancellation không bị processor này giữ lại; nó tiếp tục đi vào Lazada order processor cũ.

### 9.3 `LazadaOrderReturnGateway.java`

Tái sử dụng `LazadaAuthorizedApiClient`.

Methods:

- `approve()` -> action `APPROVE`.
- `reject()` -> action `REJECT`.
- `process()` -> action `CONFIRM_RECEIVED`.
- `check()` -> đọc `/order/reverse/get`.

`actionRequestId` được gửi dưới `request_id` để retry cùng action giữ identity.

---

## 10. TikTok Return

### 10.1 `TikTokWebhookHandler.java`

Trước đây chỉ whitelist type `1`, `2`, `68`.

Đã thêm:

```java
RETURN_STATUS_CHANGE
RETURN_STATUS_CHANGED
```

Nếu không thêm, webhook return dạng tên sẽ bị `shouldIgnore()` bỏ trước khi vào business processor.

### 10.2 `TikTokReturnSnapshotMapper.java`

Chuẩn hóa:

```text
return_id
return_line_item_id
order_id
order_line_item_id
return_quantity
return_status
update_time
```

`isPhysicalReturn()` giúp type `2` cancellation cũ không bị route nhầm sang return.

### 10.3 `TikTokReturnWebhookProcessor.java`

Nhận:

```text
TIKTOK_REVERSE_STATUS_UPDATE
RETURN_STATUS_CHANGE
RETURN_STATUS_CHANGED
```

Nhưng chỉ support nếu mapper xác nhận physical return. Type `2` cancellation khác vẫn đi vào `TikTokOrderWebhookProcessor`.

### 10.4 `TikTokOrderReturnGateway.java`

Tái sử dụng:

- `TikTokAuthorizedApiClient`.
- `ChannelRepository` để lấy `shopCipher`.
- `ObjectMapper` để serialize body.

Action:

```text
APPROVE_RETURN
REJECT_RETURN
APPROVE_RECEIVED_PACKAGE
```

`actionRequestId` được gửi thành `idempotency_key`.

`check()` đọc return detail trước khi cho retry để tránh platform xử lý hai lần.

---

## 11. Webhook Router

### 11.1 `PlatformReturnWebhookProcessor.java`

Interface:

```java
boolean supports(WebhookEvent event);
String process(WebhookEvent event);
```

`supports()` chỉ nhận diện payload/event, không gọi platform API.

### 11.2 `WebhookBusinessProcessorImpl.java`

Thêm:

```java
private final List<PlatformReturnWebhookProcessor> returnWebhookProcessors;
```

Thứ tự routing mới:

```text
1. Tìm return processor supports event.
2. Nếu có -> process return.
3. Nếu không -> giữ catalog/order routing cũ.
```

Điểm quan trọng: Lazada/TikTok return processor tự giới hạn physical return, nên reverse cancellation cũ không bị phá.

---

## 12. API Backend

### `OrderReturnController.java`

Endpoint:

```text
GET  /api/order-returns
GET  /api/order-returns/{id}
POST /api/order-returns/{id}/approve
POST /api/order-returns/{id}/reject
POST /api/order-returns/{id}/inspect
POST /api/order-returns/{id}/check-action
POST /api/order-returns/{id}/retry-action
POST /api/order-returns/{id}/retry-stock
POST /api/webhook-events/{id}/reprocess
```

Role:

```text
approve/reject: OWNER, SALES
inspect/retry stock: OWNER, OPERATIONS
check/retry action: broad controller guard + service kiểm theo lastAction
reprocess webhook: OWNER, SYSTEM_ADMIN
```

### DTO

- `OrderReturnRejectRequest`: reason từ chối.
- `OrderReturnInspectionRequest`: toàn bộ quantity kiểm hàng từng item.
- `OrderReturnResponse`: header, lifecycle, action, payment, kho, lỗi và items.
- `OrderReturnItemResponse`: quantity chi tiết từng dòng.

### ErrorCode

Thêm các lỗi rõ nghĩa:

```text
ORDER_REFUND_SYSTEM_MANAGED
ORDER_RETURN_NOT_FOUND
ORDER_RETURN_INVALID_STATE
ORDER_RETURN_ACTION_IN_PROGRESS
ORDER_RETURN_ACTION_NOT_RETRYABLE
ORDER_RETURN_ITEM_IDENTITY_MISSING
ORDER_RETURN_QUANTITY_INVALID
ORDER_RETURN_STOCK_PENDING
```

---

## 13. Frontend

### 13.1 `frontend/src/api/orderReturnApi.js`

Mỗi backend endpoint có một method tương ứng:

```javascript
getAll
getById
approve
reject
inspect
checkAction
retryAction
retryStock
```

Mọi method trả trực tiếp `response.data.data`, cùng convention với `orderApi`.

### 13.2 `OrderReturnListPage.jsx`

Hàm `load()`:

```text
GET /order-returns?page={page}&size=5
-> set result
-> lỗi hiển thị toast
```

Màn hình hiển thị:

- Return ID, order ID.
- Platform/channel.
- Business status.
- Action status và lỗi.
- Pagination 5 bản ghi.
- Nút icon mở chi tiết.

### 13.3 `OrderReturnDetailPage.jsx`

#### `load()`

Tải chi tiết và items theo route ID.

#### `run(operation, successMessage)`

Wrapper thống nhất:

```text
disable button
-> gọi API
-> cập nhật data mới
-> toast success/error
-> mở lại button
```

#### `openInspection()`

Khởi tạo form:

```text
received = approved
restockable = approved
damaged = 0
missing = 0
```

Operations sửa số thực tế nếu có hỏng/thiếu.

#### `submitInspection()`

Frontend kiểm:

```text
received = restockable + damaged
received + missing = approved
```

Sau đó gửi backend. Backend vẫn kiểm lại, frontend không phải lớp bảo mật cuối.

#### Hiển thị action theo role/state

```text
OWNER/SALES + PENDING_APPROVAL -> Duyệt/Từ chối
OWNER/OPERATIONS + AWAITING_RETURN/RETURN_IN_TRANSIT -> Nhận & kiểm hàng
UNKNOWN + đúng role action -> Kiểm tra lại
FAILED + đúng role action -> Thử lại API
PENDING_STOCK + OWNER/OPERATIONS -> Thử nhập kho
```

### 13.4 `OrderReturn.module.css`

Thiết kế:

- Khung bo 14px, control 10px.
- Màu xanh xám nhẹ, status có màu riêng nhưng không quá rực.
- Table nằm trong viewport desktop.
- Mobile cho cuộn ngang bảng và modal chuyển grid một cột.
- Focus ring, disabled và hover state đầy đủ.

### 13.5 Router và sidebar

`routes.js` thêm:

```javascript
ORDER_RETURNS: '/order-returns'
ORDER_RETURN_DETAIL: '/order-returns/:id'
```

`AppRouter.jsx` đăng ký hai page.

`MainLayout.jsx` thêm mục **Trả hàng** cho:

```text
OWNER, SALES, OPERATIONS
```

---

## 14. Luồng Gọi Hàm End-To-End

### 14.1 Platform gửi return webhook

```text
WebhookController
-> WebhookReceiverServiceImpl.receive()
-> WebhookEventProcessingService
-> WebhookBusinessProcessorImpl.process()
-> {Platform}ReturnWebhookProcessor.supports()
-> {Platform}ReturnWebhookProcessor.process()
-> {Platform}ReturnSnapshotMapper.map()
-> OrderReturnPersistenceServiceImpl.upsert()
-> OrderReturnChangedEvent
-> OrderReturnWorkflowListener.onChanged()
```

Nếu mới chỉ là yêu cầu return thì listener chưa nhập kho vì chưa có `inspectedAt/refundConfirmedAt`.

### 14.2 Owner/Sales duyệt

```text
OrderReturnDetailPage
-> orderReturnApi.approve()
-> OrderReturnController.approve()
-> OrderReturnServiceImpl.approve()
-> OrderReturnActionServiceImpl.execute(APPROVE)
-> OrderReturnActionStateService.beginNew()
-> platform gateway approve()
-> platform mapper
-> OrderReturnPersistenceServiceImpl.upsert()
-> action state IDLE
```

### 14.3 Operations kiểm hàng

```text
OrderReturnDetailPage.submitInspection()
-> OrderReturnController.inspect()
-> OrderReturnServiceImpl.inspect()
-> save warehouse + quantities + INSPECTED
-> OrderReturnInspectedEvent AFTER_COMMIT
-> OrderReturnInspectionListener.onInspected()
-> OrderReturnActionServiceImpl.execute(PROCESS)
-> platform gateway process()
```

### 14.4 Platform xác nhận refund

```text
platform snapshot refundConfirmed=true
-> OrderReturnPersistenceServiceImpl.upsert()
-> refundConfirmedAt=now
-> OrderReturnChangedEvent AFTER_COMMIT
-> OrderReturnPaymentServiceImpl.projectPayment()
-> OrderReturnInventoryPostingServiceImpl.postIfReady()
```

### 14.5 Nhập kho

```text
postIfReady()
-> lock OrderReturn
-> idempotency check
-> lock InventoryItem theo warehouse + variant
-> quantityOnHand += restockableQuantity
-> InventoryTransaction INBOUND/ORDER_RETURN
-> inventoryPostedAt
-> COMPLETED
-> MarketplaceInventoryPropagationService.schedulePushAvailableStock()
```

### 14.6 Timeout và retry thủ công

```text
API timeout
-> actionState=UNKNOWN
-> user bấm Kiểm tra lại
-> gateway.check()

Đã xử lý:
-> persist remote snapshot, IDLE

Chưa xử lý:
-> FAILED
-> user bấm Thử lại
-> check platform lần nữa
-> chỉ khi chưa xử lý mới gọi action với actionRequestId cũ
```

---

## 15. Verification Đã Chạy

Backend:

```text
mvn -DskipTests compile
BUILD SUCCESS
705 source files
```

Frontend:

```text
npm run build
976 modules transformed
build success
```

Không sửa automated test.

`backend/src/main/java/fu/osms/inventory/**` không có diff.

Còn cảnh báo Vite bundle lớn hơn 500 kB và các MapStruct warning cũ; không có compile error.

## 16. Việc Cần Làm Trước Khi Chạy Runtime

Phải chạy thủ công:

```text
backend/sql/order_returns_v1.sql
```

Nếu chưa chạy script, backend có thể compile thành công nhưng runtime sẽ lỗi do thiếu bảng/cột.

Ngoài ra cần bảo đảm app Shopify đã có scope `write_returns` và webhook callback URL hợp lệ.
