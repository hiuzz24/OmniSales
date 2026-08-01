package fu.osms.inventory.mapper;

import fu.osms.inventory.dto.response.StockDeliveryItemResponse;
import fu.osms.inventory.dto.response.StockDeliveryResponse;
import fu.osms.inventory.entity.InventoryIssue;
import fu.osms.inventory.entity.InventoryIssueItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class StockDeliveryMapper {

    public StockDeliveryResponse toResponse(InventoryIssue issue) {
        if (issue == null) {
            return null;
        }

        List<StockDeliveryItemResponse> itemResponses = issue.getItems() == null
                ? List.of()
                : issue.getItems().stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());
        int totalQuantity = issue.getItems() == null
                ? 0
                : issue.getItems().stream()
                .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
                .sum();

        StockDeliveryResponse.StockDeliveryResponseBuilder builder = StockDeliveryResponse.builder()
                .id(issue.getId())
                .issueCode(issue.getIssueCode())
                .warehouseId(issue.getWarehouse().getId())
                .warehouseName(issue.getWarehouse().getName())
                .orderId(issue.getReferenceId())
                .deliveryType(issue.getIssueType())
                .issueType(issue.getIssueType())
                .issueTypeLabel(getIssueTypeLabel(issue.getIssueType()))
                .recipient(getRecipientText(issue))
                .issuedAt(issue.getCreatedAt())
                .note(issue.getNotes())
                .totalSkuCount(itemResponses.size())
                .totalQuantity(totalQuantity)
                .totalCost(issue.getTotalCost())
                .status(issue.getStatus())
                .confirmedAt(issue.getConfirmedAt())
                .createdAt(issue.getCreatedAt())
                .updatedAt(issue.getUpdatedAt());

        if (issue.getCreatedBy() != null) {
            builder.createdBy(issue.getCreatedBy().getId())
                    .createdByName(issue.getCreatedBy().getFullName());
        }

        if (issue.getApprovedBy() != null) {
            builder.approvedBy(issue.getApprovedBy().getId())
                    .approvedByName(issue.getApprovedBy().getFullName());
        }

        builder.items(itemResponses);

        return builder.build();
    }

    public StockDeliveryItemResponse toItemResponse(InventoryIssueItem item) {
        if (item == null) {
            return null;
        }

        StockDeliveryItemResponse.StockDeliveryItemResponseBuilder builder = StockDeliveryItemResponse.builder()
                .id(item.getId())
                .productVariantId(item.getProductVariant().getId())
                .sku(item.getProductVariant().getSku())
                .productVariantName(item.getProductVariant().getName())
                .quantity(item.getQuantity())
                .unitCost(item.getUnitCost())
                .totalCost(resolveItemTotalCost(item))
                .note(item.getNotes())
                .isGift(Boolean.TRUE.equals(item.getIsGift()));

        if (item.getProductVariant().getProduct() != null) {
            builder.productName(item.getProductVariant().getProduct().getName());
        }

        return builder.build();
    }

    public List<StockDeliveryResponse> toResponseList(List<InventoryIssue> issues) {
        if (issues == null) {
            return null;
        }
        return issues.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private BigDecimal resolveItemTotalCost(InventoryIssueItem item) {
        if (item.getTotalCost() != null) {
            return item.getTotalCost();
        }
        if (item.getQuantity() == null || item.getUnitCost() == null) {
            return BigDecimal.ZERO;
        }
        return item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private String getIssueTypeLabel(String issueType) {
        return switch (issueType) {
            case "ORDER" -> "Xuất bán hàng";
            case "ADJUSTMENT" -> "Xuất dùng";
            case "DISPOSAL" -> "Xuất hủy";
            case "TRANSFER" -> "Trả hàng NCC";
            default -> issueType;
        };
    }

    private String getRecipientText(InventoryIssue issue) {
        if (issue.getRecipient() != null && !issue.getRecipient().isBlank()) {
            return issue.getRecipient();
        }
        if ("ORDER".equals(issue.getIssueType()) && issue.getReferenceId() != null) {
            return "Đơn hàng #" + issue.getReferenceId().toString().substring(0, 8).toUpperCase();
        }
        return switch (issue.getIssueType()) {
            case "ADJUSTMENT" -> "Nội bộ";
            case "DISPOSAL" -> "Thanh lý nội bộ";
            case "TRANSFER" -> "Nhà cung cấp";
            default -> "-";
        };
    }
}
