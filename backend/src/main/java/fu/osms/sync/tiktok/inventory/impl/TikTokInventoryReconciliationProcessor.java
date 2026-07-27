package fu.osms.sync.tiktok.inventory.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.sync.inventory.InventoryReconciliationProperties;
import fu.osms.sync.inventory.InventoryReconciliationTask;
import fu.osms.sync.inventory.InventoryReconciliationTaskService;
import fu.osms.sync.inventory.PlatformInventoryException;
import fu.osms.sync.tiktok.inventory.TikTokInventoryGateway;
import fu.osms.sync.tiktok.inventory.TikTokInventorySetCommand;
import fu.osms.sync.tiktok.inventory.TikTokInventoryTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TikTokInventoryReconciliationProcessor {

    private final TikTokInventoryGateway gateway;
    private final InventoryReconciliationTaskService taskService;
    private final InventoryReconciliationProperties properties;

    public Result process(
            UUID channelId,
            List<InventoryReconciliationTask> tasks,
            Map<UUID, ChannelProductVariant> mappings,
            Map<UUID, Integer> localAvailable
    ) {
        List<String> skuIds = tasks.stream()
                .map(task -> mappings.get(task.mappingId()))
                .map(mapping -> mapping == null ? null : mapping.getExternalVariantId())
                .filter(this::hasText)
                .distinct()
                .toList();

        String shopCipher;
        Map<String, Integer> remoteBySku;
        try {
            shopCipher = shopCipher(tasks, mappings);
            remoteBySku = gateway.readAvailable(channelId, shopCipher, skuIds);
        } catch (RuntimeException error) {
            tasks.forEach(task -> handleApiError(task, error));
            return new Result(0, List.of(clean(error)));
        }

        int success = 0;
        List<String> errors = new ArrayList<>();
        for (InventoryReconciliationTask task : tasks) {
            try {
                ChannelProductVariant mapping = mappings.get(task.mappingId());
                TikTokInventoryTarget target = target(task, mapping);
                Integer remote = remoteBySku.get(target.skuId());
                int local = localAvailable.getOrDefault(task.mappingId(), 0);
                if (remote == null) {
                    throw new PlatformInventoryException(
                            "TikTok inventory is missing for SKU " + target.skuId(), false);
                }
                if (remote == local) {
                    taskService.markSynced(task.mappingId(), task.cycleId());
                    success++;
                    continue;
                }
                if ("VERIFY_REMOTE".equals(task.step()) || task.correctivePushCount() >= 1) {
                    String message = "TikTok quantity remains out of sync: local="
                            + local + ", remote=" + remote + ", skuId=" + target.skuId();
                    taskService.markFailed(task.mappingId(), task.cycleId(), message);
                    errors.add(message);
                    continue;
                }

                gateway.setAvailable(task.channelId(), shopCipher, List.of(
                        new TikTokInventorySetCommand(target, local)));
                taskService.scheduleVerification(task.mappingId(), task.cycleId());
                success++;
            } catch (RuntimeException error) {
                handleApiError(task, error);
                errors.add(clean(error));
            }
        }
        return new Result(success, errors);
    }

    private TikTokInventoryTarget target(
            InventoryReconciliationTask task,
            ChannelProductVariant mapping
    ) {
        if (mapping == null || mapping.getChannelProduct() == null) {
            throw new PlatformInventoryException(
                    "TikTok inventory mapping " + task.mappingId() + " was not found", false);
        }
        String productId = mapping.getChannelProduct().getExternalProductId();
        String skuId = mapping.getExternalVariantId();
        if (!hasText(productId) || !hasText(skuId)) {
            throw new PlatformInventoryException(
                    "TikTok inventory mapping is missing product or SKU ID", false);
        }

        Set<String> warehouseIds = new LinkedHashSet<>();
        if (mapping.getMetadata() != null
                && mapping.getMetadata().get("tiktokWarehouseIds") instanceof List<?> values) {
            values.stream()
                    .map(String::valueOf)
                    .filter(this::hasText)
                    .forEach(warehouseIds::add);
        }
        Channel channel = mapping.getChannelProduct().getChannel();
        String configured = firstText(
                channel == null ? null : channel.getMetadata(),
                "tiktokWarehouseId",
                "defaultTikTokWarehouseId"
        );
        if (hasText(configured)) {
            warehouseIds.add(configured);
        }
        String primary = hasText(configured)
                ? configured
                : firstText(task.externalStockLocation(), warehouseIds.stream().findFirst().orElse(null));
        if (!hasText(primary) || warehouseIds.isEmpty()) {
            throw new PlatformInventoryException(
                    "TikTok inventory mapping has no warehouse ID for SKU " + skuId, false);
        }
        if (!warehouseIds.contains(primary)) {
            warehouseIds.add(primary);
        }
        return new TikTokInventoryTarget(
                productId,
                skuId,
                primary,
                List.copyOf(warehouseIds)
        );
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

    private String shopCipher(
            List<InventoryReconciliationTask> tasks,
            Map<UUID, ChannelProductVariant> mappings
    ) {
        for (InventoryReconciliationTask task : tasks) {
            ChannelProductVariant mapping = mappings.get(task.mappingId());
            Channel channel = mapping == null || mapping.getChannelProduct() == null
                    ? null
                    : mapping.getChannelProduct().getChannel();
            String value = firstText(
                    channel == null ? null : channel.getMetadata(),
                    "shopCipher",
                    "shop_cipher",
                    "cipher"
            );
            if (hasText(value)) {
                return value;
            }
        }
        throw new PlatformInventoryException(
                "TikTok channel is missing shopCipher metadata", false);
    }

    private String firstText(Map<String, Object> values, String... keys) {
        if (values == null) {
            return null;
        }
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null && hasText(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String clean(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }

    public record Result(int successCount, List<String> errors) {
    }
}
