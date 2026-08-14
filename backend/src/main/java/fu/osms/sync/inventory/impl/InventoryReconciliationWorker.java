package fu.osms.sync.inventory.impl;

import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.inventory.InventoryReconciliationProperties;
import fu.osms.sync.inventory.InventoryReconciliationTask;
import fu.osms.sync.inventory.InventoryReconciliationTaskService;
import fu.osms.sync.inventory.PlatformInventoryException;
import fu.osms.sync.lazada.inventory.LazadaInventoryGateway;
import fu.osms.sync.lazada.inventory.LazadaInventoryKey;
import fu.osms.sync.lazada.inventory.LazadaInventorySetCommand;
import fu.osms.sync.service.InventoryReconciliationSyncLogService;
import fu.osms.sync.service.MarketplaceStockQuantityResolver;
import fu.osms.sync.shopify.inventory.InventoryLocationKey;
import fu.osms.sync.shopify.inventory.ShopifyInventoryGateway;
import fu.osms.sync.shopify.inventory.ShopifyInventorySetCommand;
import fu.osms.sync.tiktok.inventory.impl.TikTokInventoryReconciliationProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryReconciliationWorker {

    private final InventoryReconciliationProperties properties;
    private final InventoryReconciliationTaskService taskService;
    private final ChannelProductVariantRepository mappingRepository;
    private final MarketplaceStockQuantityResolver quantityResolver;
    private final ShopifyInventoryGateway shopifyGateway;
    private final LazadaInventoryGateway lazadaGateway;
    private final TikTokInventoryReconciliationProcessor tikTokProcessor;
    private final InventoryReconciliationSyncLogService syncLogService;

    @Scheduled(fixedDelayString = "${app.inventory-reconciliation.fixed-delay-ms:5000}")
    /** Đọc số lượng từ xa đến hạn và đẩy available tuyệt đối của OSMS khi có sai lệch. */
    public void reconcileDueMappings() {
        if (!properties.isEnabled()) {
            return;
        }
        List<InventoryReconciliationTask> tasks = taskService.claimDueTasks();
        if (tasks.isEmpty()) {
            return;
        }

        Map<UUID, ChannelProductVariant> mappings = mappingRepository
                .findAllWithChannelAndVariantByIdIn(
                        tasks.stream().map(InventoryReconciliationTask::mappingId).toList())
                .stream()
                .collect(Collectors.toMap(ChannelProductVariant::getId, mapping -> mapping));
        Map<UUID, Integer> localAvailable = quantityResolver.resolveAvailableByMappingIds(
                tasks.stream().map(InventoryReconciliationTask::mappingId).toList());

        Map<ChannelPlatformKey, List<InventoryReconciliationTask>> grouped = tasks.stream()
                .collect(Collectors.groupingBy(
                        task -> new ChannelPlatformKey(task.channelId(), task.platform()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        grouped.forEach((key, group) ->
                processGroup(key, group, mappings, localAvailable));
    }

    private void processGroup(
            ChannelPlatformKey groupKey,
            List<InventoryReconciliationTask> tasks,
            Map<UUID, ChannelProductVariant> mappings,
            Map<UUID, Integer> localAvailable
    ) {
        UUID syncLogId = syncLogService.start(groupKey.channelId(), tasks.size());
        int success = 0;
        List<String> errors = new ArrayList<>();
        try {
            if (groupKey.platform() == PlatformType.SHOPIFY) {
                Map<InventoryLocationKey, Integer> remote =
                        readShopify(groupKey.channelId(), tasks, mappings);
                for (InventoryReconciliationTask task : tasks) {
                    if (processShopify(task, mappings.get(task.mappingId()),
                            localAvailable.getOrDefault(task.mappingId(), 0), remote)) {
                        success++;
                    }
                }
            } else if (groupKey.platform() == PlatformType.LAZADA) {
                Map<LazadaInventoryKey, Integer> remote =
                        readLazada(groupKey.channelId(), tasks, mappings);
                for (InventoryReconciliationTask task : tasks) {
                    if (processLazada(task, mappings.get(task.mappingId()),
                            localAvailable.getOrDefault(task.mappingId(), 0), remote)) {
                        success++;
                    }
                }
            } else if (groupKey.platform() == PlatformType.TIKTOK) {
                TikTokInventoryReconciliationProcessor.Result result = tikTokProcessor.process(
                        groupKey.channelId(), tasks, mappings, localAvailable);
                success = result.successCount();
                errors.addAll(result.errors());
            }
        } catch (RuntimeException error) {
            for (InventoryReconciliationTask task : tasks) {
                handleApiError(task, error);
            }
            errors.add(clean(error));
        }

        if (errors.isEmpty() && success == tasks.size()) {
            syncLogService.markSynced(syncLogId, success);
        } else {
            if (errors.isEmpty()) {
                errors.add("One or more inventory mappings could not be reconciled");
            }
            syncLogService.markFailed(syncLogId, tasks.size() - success,
                    String.join("; ", errors));
        }
    }

    private Map<InventoryLocationKey, Integer> readShopify(
            UUID channelId,
            List<InventoryReconciliationTask> tasks,
            Map<UUID, ChannelProductVariant> mappings
    ) {
        List<InventoryLocationKey> keys = tasks.stream()
                .map(task -> shopifyKey(task, mappings.get(task.mappingId())))
                .filter(Objects::nonNull)
                .toList();
        return shopifyGateway.readAvailable(channelId, keys);
    }

    private Map<LazadaInventoryKey, Integer> readLazada(
            UUID channelId,
            List<InventoryReconciliationTask> tasks,
            Map<UUID, ChannelProductVariant> mappings
    ) {
        List<LazadaInventoryKey> keys = tasks.stream()
                .map(task -> lazadaKey(task, mappings.get(task.mappingId())))
                .filter(Objects::nonNull)
                .toList();
        return lazadaGateway.readSellable(channelId, keys);
    }

    private boolean processShopify(
            InventoryReconciliationTask task,
            ChannelProductVariant mapping,
            int local,
            Map<InventoryLocationKey, Integer> remoteByKey
    ) {
        try {
            InventoryLocationKey key = shopifyKey(task, mapping);
            Integer remote = key == null ? null : lookupShopify(remoteByKey, key);
            if (remote == null) {
                throw new PlatformInventoryException(
                        "Shopify inventory level is missing for mapping " + task.mappingId(), false);
            }
            if (remote == local) {
                taskService.markSynced(task.mappingId(), task.cycleId());
                return true;
            }
            if ("VERIFY_REMOTE".equals(task.step()) || task.correctivePushCount() >= 1) {
                taskService.markFailed(task.mappingId(), task.cycleId(),
                        "Shopify quantity remains out of sync: local=" + local + ", remote=" + remote);
                return false;
            }
            shopifyGateway.setAvailable(task.channelId(), List.of(
                    new ShopifyInventorySetCommand(key, local, remote)), task.cycleId());
            taskService.scheduleVerification(task.mappingId(), task.cycleId());
            return true;
        } catch (RuntimeException error) {
            handleApiError(task, error);
            return false;
        }
    }

    private boolean processLazada(
            InventoryReconciliationTask task,
            ChannelProductVariant mapping,
            int local,
            Map<LazadaInventoryKey, Integer> remoteByKey
    ) {
        try {
            LazadaInventoryKey key = lazadaKey(task, mapping);
            Integer remote = key == null ? null : remoteByKey.get(key);
            if (remote == null) {
                throw new PlatformInventoryException(
                        "Lazada sellable quantity is missing for mapping " + task.mappingId(), false);
            }
            if (remote == local) {
                taskService.markSynced(task.mappingId(), task.cycleId());
                return true;
            }
            if ("VERIFY_REMOTE".equals(task.step()) || task.correctivePushCount() >= 1) {
                taskService.markFailed(task.mappingId(), task.cycleId(),
                        "Lazada quantity remains out of sync: local=" + local + ", remote=" + remote);
                return false;
            }
            lazadaGateway.updateSellable(task.channelId(), List.of(
                    new LazadaInventorySetCommand(key, local)));
            taskService.scheduleVerification(task.mappingId(), task.cycleId());
            return true;
        } catch (RuntimeException error) {
            handleApiError(task, error);
            return false;
        }
    }

    private void handleApiError(InventoryReconciliationTask task, RuntimeException error) {
        int nextAttempt = task.apiAttemptCount() + 1;
        boolean retryable = !(error instanceof PlatformInventoryException platformError)
                || platformError.isRetryable();
        if (retryable && nextAttempt < properties.getMaxApiAttempts()) {
            taskService.scheduleRetry(
                    task.mappingId(), task.cycleId(), nextAttempt, clean(error));
        } else {
            taskService.markFailed(task.mappingId(), task.cycleId(), clean(error));
        }
    }

    private InventoryLocationKey shopifyKey(
            InventoryReconciliationTask task,
            ChannelProductVariant mapping
    ) {
        if (mapping == null || mapping.getMetadata() == null) {
            return null;
        }
        Object inventoryItemId = mapping.getMetadata().get("inventory_item_id");
        if (inventoryItemId == null || inventoryItemId.toString().isBlank()) {
            return null;
        }
        String locationId = task.externalStockLocation();
        if (locationId == null || locationId.isBlank()) {
            locationId = shopifyGateway.resolveManagedLocationId(task.channelId());
        }
        return new InventoryLocationKey(inventoryItemId.toString(), locationId);
    }

    private LazadaInventoryKey lazadaKey(
            InventoryReconciliationTask task,
            ChannelProductVariant mapping
    ) {
        if (mapping == null || mapping.getChannelProduct() == null) {
            return null;
        }
        return new LazadaInventoryKey(
                mapping.getChannelProduct().getExternalProductId(),
                mapping.getExternalVariantId(),
                mapping.getExternalSku(),
                task.externalStockLocation()
        );
    }

    private Integer lookupShopify(
            Map<InventoryLocationKey, Integer> values,
            InventoryLocationKey key
    ) {
        Integer direct = values.get(key);
        if (direct != null) {
            return direct;
        }
        return values.entrySet().stream()
                .filter(entry -> stripGid(entry.getKey().inventoryItemId())
                        .equals(stripGid(key.inventoryItemId())))
                .filter(entry -> stripGid(entry.getKey().locationId())
                        .equals(stripGid(key.locationId())))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String stripGid(String value) {
        if (value == null) {
            return "";
        }
        int slash = value.lastIndexOf('/');
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    private String clean(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private record ChannelPlatformKey(UUID channelId, PlatformType platform) {
    }
}
