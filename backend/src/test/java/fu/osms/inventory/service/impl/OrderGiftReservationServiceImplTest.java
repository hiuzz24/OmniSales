package fu.osms.inventory.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.inventory.entity.InventoryIssue;
import fu.osms.inventory.entity.InventoryIssueItem;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.service.InventoryAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderGiftReservationServiceImpl Tests")
class OrderGiftReservationServiceImplTest {

    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryTransactionRepository transactionRepository;
    @Mock private InventoryAlertService inventoryAlertService;
    @Mock private OrderGiftSharedStockSupport sharedStockSupport;

    private OrderGiftReservationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderGiftReservationServiceImpl(
                inventoryItemRepository, transactionRepository, inventoryAlertService, sharedStockSupport);
    }

    private Warehouse warehouse() {
        return Warehouse.builder().id(UUID.randomUUID()).build();
    }

    private InventoryIssue persistedIssue(Warehouse warehouse) {
        return InventoryIssue.builder()
                .id(UUID.randomUUID())
                .issueCode("ISS-001")
                .warehouse(warehouse)
                .build();
    }

    private InventoryIssueItem giftItem(boolean isGift) {
        return InventoryIssueItem.builder()
                .id(UUID.randomUUID())
                .isGift(isGift)
                .build();
    }

    @Test
    @DisplayName("reserveGiftReservations returns empty set when no gift items are passed")
    void reserveGiftReservations_emptyGifts() {
        InventoryIssue issue = persistedIssue(warehouse());
        Collection<InventoryIssueItem> items = List.of(giftItem(false), giftItem(false));

        Set<UUID> result = service.reserveGiftReservations(issue, items, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("reserveGiftReservations returns empty set when giftItems is null")
    void reserveGiftReservations_nullItems() {
        InventoryIssue issue = persistedIssue(warehouse());

        Set<UUID> result = service.reserveGiftReservations(issue, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("reserveGiftReservations throws AppException when issue is not persisted")
    void reserveGiftReservations_issueNotPersisted() {
        InventoryIssue issue = InventoryIssue.builder().build(); // no id

        assertThatThrownBy(() -> service.reserveGiftReservations(issue, List.of(giftItem(true)), null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Phiếu xuất phải được lưu trước");
    }

    @Test
    @DisplayName("reserveGiftReservations throws AppException when gift reservations already exist for the issue (idempotency)")
    void reserveGiftReservations_alreadyReserved() {
        InventoryIssue issue = persistedIssue(warehouse());
        when(transactionRepository.existsByReferenceTypeAndReferenceIdAndType(
                eq("ISSUE_GIFT"), eq(issue.getId()), eq(InvTxnType.ORDER_DEDUCT)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.reserveGiftReservations(issue, List.of(giftItem(true)), null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("đã được giữ trước đó");
    }

    @Test
    @DisplayName("releaseGiftReservations returns empty set when no reservation transactions exist")
    void releaseGiftReservations_noReservations() {
        InventoryIssue issue = persistedIssue(warehouse());
        when(transactionRepository.findByReferenceTypeAndReferenceIdAndType(
                eq("ISSUE_GIFT"), eq(issue.getId()), eq(InvTxnType.ORDER_DEDUCT)))
                .thenReturn(List.of());

        Set<UUID> result = service.releaseGiftReservations(issue, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("releaseGiftReservations returns empty set when a previous ORDER_CANCEL exists (idempotent)")
    void releaseGiftReservations_alreadyCancelled() {
        InventoryIssue issue = persistedIssue(warehouse());
        when(transactionRepository.findByReferenceTypeAndReferenceIdAndType(
                eq("ISSUE_GIFT"), eq(issue.getId()), eq(InvTxnType.ORDER_DEDUCT)))
                .thenReturn(List.of(InventoryTransaction.builder().build()));
        when(transactionRepository.existsByReferenceTypeAndReferenceIdAndType(
                eq("ISSUE_GIFT"), eq(issue.getId()), eq(InvTxnType.ORDER_CANCEL)))
                .thenReturn(true);

        Set<UUID> result = service.releaseGiftReservations(issue, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("releaseGiftReservations throws AppException when the issue is not persisted")
    void releaseGiftReservations_issueNotPersisted() {
        InventoryIssue issue = InventoryIssue.builder().build();

        assertThatThrownBy(() -> service.releaseGiftReservations(issue, null))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("commitGiftReservations is a no-op when the issue has no gift items")
    void commitGiftReservations_noGifts() {
        InventoryIssue issue = persistedIssue(warehouse());
        issue.setItems(List.of(giftItem(false)));

        service.commitGiftReservations(issue, null);

        // No interaction expected.
    }

    @Test
    @DisplayName("commitGiftReservations throws AppException when the issue is not persisted")
    void commitGiftReservations_issueNotPersisted() {
        InventoryIssue issue = InventoryIssue.builder().items(List.of(giftItem(true))).build();

        assertThatThrownBy(() -> service.commitGiftReservations(issue, null))
                .isInstanceOf(AppException.class);
    }
}
