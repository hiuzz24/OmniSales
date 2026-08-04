package fu.osms.inventory.service.impl;

import fu.osms.common.exception.AppException;
import fu.osms.inventory.dto.response.OrderStockDeliveryReadinessResponse;
import fu.osms.inventory.entity.InventoryIssue;
import fu.osms.inventory.repository.InventoryIssueRepository;
import fu.osms.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderStockDeliveryReadinessServiceImpl Tests")
class OrderStockDeliveryReadinessServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private InventoryIssueRepository inventoryIssueRepository;

    private OrderStockDeliveryReadinessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderStockDeliveryReadinessServiceImpl(orderRepository, inventoryIssueRepository);
    }

    @Test
    @DisplayName("getReadiness returns readyForShipment=false when the order has no DRAFT issue")
    void getReadiness_noDraftIssue() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.existsById(orderId)).thenReturn(true);
        when(inventoryIssueRepository.findFirstByReferenceIdAndIssueTypeAndStatus(eq(orderId), eq("ORDER"), eq("DRAFT")))
                .thenReturn(Optional.empty());

        OrderStockDeliveryReadinessResponse response = service.getReadiness(orderId);

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.readyForShipment()).isFalse();
        assertThat(response.stockDeliveryId()).isNull();
        assertThat(response.issueCode()).isNull();
        assertThat(response.status()).isNull();
    }

    @Test
    @DisplayName("getReadiness returns readyForShipment=true with the issue coords when a DRAFT issue exists")
    void getReadiness_draftIssueExists() {
        UUID orderId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        when(orderRepository.existsById(orderId)).thenReturn(true);
        InventoryIssue issue = InventoryIssue.builder()
                .id(issueId)
                .issueCode("ISS-001")
                .status("DRAFT")
                .referenceId(orderId)
                .issueType("ORDER")
                .build();
        when(inventoryIssueRepository.findFirstByReferenceIdAndIssueTypeAndStatus(orderId, "ORDER", "DRAFT"))
                .thenReturn(Optional.of(issue));

        OrderStockDeliveryReadinessResponse response = service.getReadiness(orderId);

        assertThat(response.readyForShipment()).isTrue();
        assertThat(response.stockDeliveryId()).isEqualTo(issueId);
        assertThat(response.issueCode()).isEqualTo("ISS-001");
        assertThat(response.status()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("getReadiness throws AppException when the order does not exist")
    void getReadiness_orderMissing() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.existsById(orderId)).thenReturn(false);

        assertThatThrownBy(() -> service.getReadiness(orderId))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("requireReadyForShipment throws when the order does not exist")
    void requireReadyForShipment_orderMissing() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.existsById(orderId)).thenReturn(false);

        assertThatThrownBy(() -> service.requireReadyForShipment(orderId))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("requireReadyForShipment throws when readiness is false (no DRAFT issue)")
    void requireReadyForShipment_throwsWhenNotReady() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.existsById(orderId)).thenReturn(true);
        when(inventoryIssueRepository.findFirstByReferenceIdAndIssueTypeAndStatus(orderId, "ORDER", "DRAFT"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireReadyForShipment(orderId))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("requireReadyForShipment does not throw when a DRAFT issue exists")
    void requireReadyForShipment_passesWhenReady() {
        UUID orderId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        when(orderRepository.existsById(orderId)).thenReturn(true);
        InventoryIssue issue = InventoryIssue.builder().id(issueId).status("DRAFT").issueType("ORDER").referenceId(orderId).build();
        when(inventoryIssueRepository.findFirstByReferenceIdAndIssueTypeAndStatus(orderId, "ORDER", "DRAFT"))
                .thenReturn(Optional.of(issue));

        service.requireReadyForShipment(orderId);
    }
}
