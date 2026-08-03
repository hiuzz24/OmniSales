package fu.osms.orderreturn.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.orderreturn.enums.ReturnAction;
import fu.osms.orderreturn.model.OrderReturnSnapshot;
import fu.osms.orderreturn.model.ReturnActionContext;
import fu.osms.orderreturn.model.ReturnPlatformActionResult;
import fu.osms.orderreturn.model.ReturnRejectCommand;
import fu.osms.orderreturn.model.ReturnRejectOptions;
import fu.osms.orderreturn.service.OrderReturnPersistenceService;
import fu.osms.orderreturn.service.OrderReturnPlatformGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderReturnActionServiceImpl Tests")
class OrderReturnActionServiceImplTest {

    @Mock private OrderReturnActionStateService stateService;
    @Mock private OrderReturnPlatformGateway lazadaGateway;
    @Mock private OrderReturnPlatformGateway shopifyGateway;
    @Mock private OrderReturnPlatformGateway tiktokGateway;
    @Mock private ChannelRepository channelRepository;
    @Mock private OrderReturnPersistenceService persistenceService;

    private OrderReturnActionServiceImpl service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(lazadaGateway.platform()).thenReturn(PlatformType.LAZADA);
        org.mockito.Mockito.lenient().when(shopifyGateway.platform()).thenReturn(PlatformType.SHOPIFY);
        org.mockito.Mockito.lenient().when(tiktokGateway.platform()).thenReturn(PlatformType.TIKTOK);
        service = new OrderReturnActionServiceImpl(
                stateService,
                List.of(lazadaGateway, shopifyGateway, tiktokGateway),
                channelRepository,
                persistenceService);
    }

    private ReturnActionContext context(PlatformType platform, ReturnAction action) {
        UUID returnId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        return new ReturnActionContext(
                returnId, channelId, platform, "EXT-RET-1",
                action, requestId, null, List.of()
        );
    }

    private Channel channel(UUID id) {
        return Channel.builder().id(id).platform(PlatformType.LAZADA).build();
    }

    private OrderReturnSnapshot snapshot() {
        return new OrderReturnSnapshot("ext", "order", "PENDING", null, null, null, false, false, List.of(), null);
    }

    private void stubChannelFound(ReturnActionContext ctx) {
        when(channelRepository.findById(ctx.channelId())).thenReturn(Optional.of(channel(ctx.channelId())));
    }

    @Test
    @DisplayName("execute APPROVE: routes to the gateway matching the channel's platform and persists the result when applied")
    void execute_approve_happyPath() {
        ReturnActionContext ctx = context(PlatformType.LAZADA, ReturnAction.APPROVE);
        when(stateService.beginNew(eq(ctx.returnId()), eq(ReturnAction.APPROVE), any())).thenReturn(ctx);
        OrderReturnSnapshot snap = snapshot();
        when(lazadaGateway.approve(ctx)).thenReturn(new ReturnPlatformActionResult(snap, true));
        stubChannelFound(ctx);

        service.execute(ctx.returnId(), ReturnAction.APPROVE, null);

        verify(lazadaGateway).approve(ctx);
        verify(stateService, times(1)).markIdle(ctx.returnId());
        verify(persistenceService).upsert(any(), eq(snap));
    }

    @Test
    @DisplayName("execute REJECT: passes the reject command to the matching gateway")
    void execute_reject_passesCommand() {
        ReturnActionContext ctx = context(PlatformType.TIKTOK, ReturnAction.REJECT);
        ReturnRejectCommand command = new ReturnRejectCommand("reason-1", null);
        // The stateService returns a context that carries the command into the gateway call.
        ReturnActionContext ctxWithCommand = new ReturnActionContext(
                ctx.returnId(), ctx.channelId(), ctx.platform(), ctx.externalReturnId(),
                ctx.action(), ctx.requestId(), command, ctx.items());
        when(stateService.beginNew(eq(ctx.returnId()), eq(ReturnAction.REJECT), eq(command)))
                .thenReturn(ctxWithCommand);
        when(tiktokGateway.reject(ctxWithCommand, command))
                .thenReturn(new ReturnPlatformActionResult(null, true));

        service.execute(ctx.returnId(), ReturnAction.REJECT, command);

        verify(tiktokGateway).reject(ctxWithCommand, command);
        verify(stateService).markIdle(ctx.returnId());
    }

    @Test
    @DisplayName("execute PROCESS: routes to gateway.process")
    void execute_process() {
        ReturnActionContext ctx = context(PlatformType.SHOPIFY, ReturnAction.PROCESS);
        when(stateService.beginNew(eq(ctx.returnId()), eq(ReturnAction.PROCESS), any())).thenReturn(ctx);
        when(shopifyGateway.process(ctx)).thenReturn(new ReturnPlatformActionResult(null, true));

        service.execute(ctx.returnId(), ReturnAction.PROCESS, null);

        verify(shopifyGateway).process(ctx);
    }

    @Test
    @DisplayName("execute marks the return as failed when no gateway matches the platform (no network error)")
    void execute_noGatewayForPlatform_marksFailed() {
        // Build service with only LAZADA gateway to force a "no gateway" case for a TikTok return.
        service = new OrderReturnActionServiceImpl(stateService,
                List.of(lazadaGateway), channelRepository, persistenceService);
        ReturnActionContext ctx = context(PlatformType.TIKTOK, ReturnAction.APPROVE);
        when(stateService.beginNew(eq(ctx.returnId()), eq(ReturnAction.APPROVE), any())).thenReturn(ctx);

        service.execute(ctx.returnId(), ReturnAction.APPROVE, null);

        verify(stateService).markFailed(eq(ctx.returnId()), any());
    }

    @Test
    @DisplayName("execute does not crash when the service is built with an empty gateway list")
    void execute_emptyGatewayList_doesNotCrash() {
        service = new OrderReturnActionServiceImpl(stateService,
                List.of(), channelRepository, persistenceService);
        ReturnActionContext ctx = context(PlatformType.LAZADA, ReturnAction.APPROVE);
        when(stateService.beginNew(eq(ctx.returnId()), eq(ReturnAction.APPROVE), any())).thenReturn(ctx);

        service.execute(ctx.returnId(), ReturnAction.APPROVE, null);

        verify(stateService).markFailed(eq(ctx.returnId()), any());
    }

    @Test
    @DisplayName("execute marks failed when the gateway throws a non-unknown exception")
    void execute_marksFailedOnException() {
        ReturnActionContext ctx = context(PlatformType.LAZADA, ReturnAction.APPROVE);
        when(stateService.beginNew(eq(ctx.returnId()), eq(ReturnAction.APPROVE), any())).thenReturn(ctx);
        when(lazadaGateway.approve(ctx)).thenThrow(new RuntimeException("kaboom"));

        service.execute(ctx.returnId(), ReturnAction.APPROVE, null);

        verify(stateService).markFailed(eq(ctx.returnId()), any());
    }

    @Test
    @DisplayName("getRejectOptions: returns options from the gateway matching the platform")
    void getRejectOptions_returnsFromGateway() {
        ReturnActionContext ctx = context(PlatformType.LAZADA, ReturnAction.APPROVE);
        when(stateService.contextForRejectOptions(ctx.returnId())).thenReturn(ctx);
        ReturnRejectOptions options = new ReturnRejectOptions(
                true, false, List.of(new ReturnRejectOptions.Option("code-1", "Damaged")), null);
        when(lazadaGateway.rejectOptions(ctx)).thenReturn(options);

        ReturnRejectOptions result = service.getRejectOptions(ctx.returnId());

        assertThat(result.requiresReasonCode()).isTrue();
        assertThat(result.options()).hasSize(1);
        assertThat(result.options().get(0).code()).isEqualTo("code-1");
    }

    @Test
    @DisplayName("validateInspection: delegates to the matching gateway and propagates any exception")
    void validateInspection_propagatesException() {
        ReturnActionContext ctx = context(PlatformType.LAZADA, ReturnAction.APPROVE);
        when(stateService.contextForRefresh(ctx.returnId())).thenReturn(ctx);
        doThrow(new RuntimeException("bad")).when(lazadaGateway).validateInspection(ctx);

        assertThatThrownBy(() -> service.validateInspection(ctx.returnId()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("refresh: delegates to gateway.refresh and persists the snapshot when present")
    void refresh_persistsSnapshot() {
        ReturnActionContext ctx = context(PlatformType.LAZADA, ReturnAction.APPROVE);
        when(stateService.contextForRefresh(ctx.returnId())).thenReturn(ctx);
        OrderReturnSnapshot snap = snapshot();
        when(lazadaGateway.refresh(ctx)).thenReturn(new ReturnPlatformActionResult(snap, true));
        stubChannelFound(ctx);

        service.refresh(ctx.returnId());

        verify(lazadaGateway).refresh(ctx);
        verify(persistenceService).upsert(any(), eq(snap));
    }

    @Test
    @DisplayName("refresh: persists nothing when the snapshot is null")
    void refresh_nullSnapshot() {
        ReturnActionContext ctx = context(PlatformType.LAZADA, ReturnAction.APPROVE);
        when(stateService.contextForRefresh(ctx.returnId())).thenReturn(ctx);
        when(lazadaGateway.refresh(ctx)).thenReturn(new ReturnPlatformActionResult(null, true));

        service.refresh(ctx.returnId());

        verify(persistenceService, never()).upsert(any(), any());
    }

    @Test
    @DisplayName("check: persists+marks idle when the platform returns applied=true")
    void check_applied() {
        ReturnActionContext ctx = context(PlatformType.LAZADA, ReturnAction.APPROVE);
        when(stateService.contextForCheck(ctx.returnId())).thenReturn(ctx);
        OrderReturnSnapshot snap = snapshot();
        when(lazadaGateway.check(ctx)).thenReturn(new ReturnPlatformActionResult(snap, true));
        stubChannelFound(ctx);

        service.check(ctx.returnId());

        verify(persistenceService).upsert(any(), eq(snap));
        verify(stateService).markIdle(ctx.returnId());
    }

    @Test
    @DisplayName("check: marks failed when the platform returns retrySafe=true (not applied)")
    void check_marksFailed() {
        ReturnActionContext ctx = context(PlatformType.LAZADA, ReturnAction.APPROVE);
        when(stateService.contextForCheck(ctx.returnId())).thenReturn(ctx);
        when(lazadaGateway.check(ctx))
                .thenReturn(new ReturnPlatformActionResult(null, false, true, null));

        service.check(ctx.returnId());

        verify(stateService).markFailed(eq(ctx.returnId()), any());
    }

    @Test
    @DisplayName("check: marks unknown when the gateway throws and the exception is unknown")
    void check_marksUnknownOnUnknownException() {
        ReturnActionContext ctx = context(PlatformType.LAZADA, ReturnAction.APPROVE);
        when(stateService.contextForCheck(ctx.returnId())).thenReturn(ctx);
        when(lazadaGateway.check(ctx))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));

        service.check(ctx.returnId());

        verify(stateService).markUnknown(eq(ctx.returnId()), any());
    }
}