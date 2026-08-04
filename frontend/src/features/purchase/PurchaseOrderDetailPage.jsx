import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft, ClipboardCheck, Loader2, Plus, Printer, ShoppingBag } from 'lucide-react';
import { toast } from 'react-toastify';
import purchaseOrderApi from '../../api/purchaseOrderApi';
import { ROUTES } from '../../app/router/routes';
import { ROLES } from '../auth/constants/roles';
import useAuth from '../auth/hooks/useAuth';
import useConfirmDialog from '../inventory/hooks/useConfirmDialog';
import styles from './PurchaseOrderPage.module.css';

const STATUS = {
  DRAFT: { label: 'Nháp', className: styles.draft },
  SENT_TO_SUPPLIER: { label: 'Đã gửi NCC', className: styles.sent },
  RECEIVING: { label: 'Đang giao hàng', className: styles.receiving },
  INSPECTING: { label: 'Đang kiểm tra', className: styles.inspecting },
  INSPECTED: { label: 'Đã kiểm tra', className: styles.inspected },
  COMPLETED: { label: 'Hoàn thành', className: styles.completed },
  CANCELLED: { label: 'Đã hủy', className: styles.cancelled },
};

const money = (v) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v ?? 0);
const num = (v) => (v == null ? '—' : Number(v).toLocaleString('vi-VN'));
const dt = (v) => v ? new Date(v).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';
const dateOnly = (v) => v ? new Date(v).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '—';

function printOrder(order) {
  const win = window.open('', '_blank');
  if (!win) return;
  const rows = (order.items ?? []).map((item, i) => `<tr>
    <td style="text-align:center;padding:6px 8px;border:1px solid #cbd5e1">${i + 1}</td>
    <td style="padding:6px 8px;border:1px solid #cbd5e1"><strong>${item.productName ?? ''}</strong>${item.variantName ? `<br/><small style="color:#64748b">${item.variantName}</small>` : ''}</td>
    <td style="text-align:center;padding:6px 8px;border:1px solid #cbd5e1">${item.marketplaceSku ?? item.sku ?? ''}</td>
    <td style="text-align:center;padding:6px 8px;border:1px solid #cbd5e1">cái</td>
    <td style="text-align:right;padding:6px 8px;border:1px solid #cbd5e1">${num(item.quantity)}</td>
    <td style="text-align:right;padding:6px 8px;border:1px solid #cbd5e1">${item.actualQuantity != null ? num(item.actualQuantity) : '—'}</td>
    <td style="text-align:right;padding:6px 8px;border:1px solid #cbd5e1">${new Intl.NumberFormat('vi-VN').format(item.unitCost ?? 0)}</td>
    <td style="text-align:right;padding:6px 8px;border:1px solid #cbd5e1">${new Intl.NumberFormat('vi-VN').format(item.totalCost ?? 0)}</td>
  </tr>`).join('');
  const html = `<!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"><title>Phiếu đặt hàng ${order.orderCode}</title>
    <style>body{font-family:'Times New Roman',serif;font-size:13px;margin:0;padding:24px}h2{text-align:center;font-size:16px;margin:8px 0}
    .hi{width:100%;margin-bottom:16px}.hi td{padding:2px 8px;font-size:13px}table.it{width:100%;border-collapse:collapse;margin:12px 0}
    table.it th{background:#f1f5f9;padding:7px 8px;border:1px solid #cbd5e1;text-align:center}
    .sr{display:flex;justify-content:space-around;margin-top:40px;text-align:center}.sr div{flex:1}.sr p{font-weight:700;margin-bottom:4px}
    @media print{body{padding:8px}}</style></head><body>
    <p style="text-align:center;font-size:12px;margin:0">CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM</p>
    <p style="text-align:center;font-size:12px;margin:0;margin-bottom:16px">Độc lập – Tự do – Hạnh phúc</p>
    <h2>PHIẾU ĐẶT HÀNG</h2>
    <p style="text-align:center;color:#64748b;margin-bottom:16px">Mã đơn: <strong>${order.orderCode}</strong></p>
    <table class="hi"><tbody>
      <tr><td><strong>Kính gửi:</strong> ${order.supplierName ?? ''}</td><td><strong>Ngày lập:</strong> ${dt(order.orderDate ?? order.createdAt)}</td></tr>
      <tr><td><strong>Kho nhận hàng:</strong> ${order.warehouseName ?? ''}${order.warehouseAddress ? ` — ${order.warehouseAddress}` : ''}</td><td><strong>Dự kiến nhận:</strong> ${dateOnly(order.expectedReceiptDate)}</td></tr>
      <tr><td><strong>Người lập:</strong> ${order.createdByName ?? ''}</td><td><strong>Thanh toán:</strong> ${order.paymentMethod ?? '—'}</td></tr>
      ${order.notes ? `<tr><td colspan="2"><strong>Ghi chú:</strong> ${order.notes}</td></tr>` : ''}
    </tbody></table>
    <table class="it"><thead><tr><th>STT</th><th>Tên hàng hóa</th><th>SKU</th><th>ĐVT</th><th>SL đặt</th><th>SL thực</th><th>Đơn giá</th><th>Thành tiền</th></tr></thead>
    <tbody>${rows}</tbody></table>
    <p style="text-align:right;font-weight:700">Tổng cộng: ${new Intl.NumberFormat('vi-VN').format(order.totalAmount ?? 0)} VNĐ</p>
    <div class="sr"><div><p>Người lập phiếu</p><small>(Ký, ghi rõ họ tên)</small><br/><br/><br/></div>
    <div><p>Trưởng bộ phận</p><small>(Ký duyệt)</small><br/><br/><br/></div>
    <div><p>Bên nhận đơn hàng</p><small>(Ký xác nhận)</small><br/><br/><br/></div></div>
    </body></html>`;
  win.document.open(); win.document.write(html); win.document.close();
  win.focus(); setTimeout(() => win.print(), 400);
}

export default function PurchaseOrderDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { user } = useAuth();

  // inspect=true means we're in inspection mode (came from "Kiểm tra" button)
  const inspectMode = searchParams.get('inspect') === 'true';

  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [actualQty, setActualQty] = useState({});
  const [notes, setNotes] = useState({});

  const canInspect = [ROLES.SALES, ROLES.OWNER].includes(user?.role);
  const { confirm, ConfirmDialog } = useConfirmDialog();
  // Show inspection UI only when explicitly in inspect mode AND order is in an inspectable state
  const isInspectable = inspectMode && canInspect && order &&
    (order.status === 'RECEIVING' || order.status === 'INSPECTING');
  const isCompleted = order?.status === 'COMPLETED' || order?.status === 'INSPECTED';

  const reload = () => {
    setLoading(true);
    purchaseOrderApi.getById(id)
      .then((data) => {
        setOrder(data);
        const init = {};
        const initNotes = {};
        (data.items ?? []).forEach((item) => {
          if (item.actualQuantity != null) init[item.variantId] = item.actualQuantity;
          else init[item.variantId] = item.quantity; // default = ordered qty
          initNotes[item.variantId] = item.surplusNote ?? '';
        });
        setActualQty(init);
        setNotes(initNotes);

        // Auto-transition RECEIVING → INSPECTING as soon as user opens the inspect page
        if (inspectMode && canInspect && data.status === 'RECEIVING') {
          const payload = (data.items ?? []).map((item) => ({
            variantId: item.variantId,
            actualQuantity: item.actualQuantity != null ? item.actualQuantity : item.quantity,
            surplusNote: null,
          }));
          purchaseOrderApi.saveInspection(id, payload)
            .then((updated) => setOrder(updated))
            .catch(() => {}); // silent — status shown will auto-refresh
        }
      })
      .catch(() => toast.error('Không thể tải thông tin đơn mua hàng.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { reload(); }, [id]);

  const buildPayload = () =>
    (order?.items ?? []).map((item) => {
      const actual = actualQty[item.variantId] != null ? Number(actualQty[item.variantId]) : item.quantity;
      const diff = actual - item.quantity;
      // Auto-generate note if user hasn't typed one
      let autoNote = notes[item.variantId] ?? '';
      if (!autoNote) {
        if (diff > 0) autoNote = `Thừa ${diff} sản phẩm so với số lượng đặt (${item.quantity})`;
        else if (diff < 0) autoNote = `Thiếu ${Math.abs(diff)} sản phẩm so với số lượng đặt (${item.quantity})`;
      }
      return {
        variantId: item.variantId,
        actualQuantity: actual,
        surplusNote: autoNote || null,
      };
    });

  // "Tiếp tục kiểm tra" — save draft, stay in INSPECTING, then go back to list
  const handleSave = async () => {
    setSaving(true);
    try {
      await purchaseOrderApi.saveInspection(id, buildPayload());
      toast.success('Đã lưu tiến độ kiểm tra. Trạng thái: Đang kiểm tra.');
      navigate(ROUTES.PURCHASE_ORDERS);
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Không thể lưu kiểm tra.');
    } finally { setSaving(false); }
  };

  // "Hoàn thành kiểm tra" — confirm → finalize → INSPECTED
  const handleComplete = async () => {
    const confirmed = await confirm({
      title: 'Hoàn thành kiểm tra?',
      message: 'Sau khi hoàn thành kiểm tra, đơn sẽ chuyển sang trạng thái Chờ nhập kho. Thông tin thừa/thiếu đã ghi chú sẽ được lưu lại.',
      confirmLabel: 'Hoàn thành kiểm tra',
      tone: 'warning',
    });
    if (!confirmed) return;

    setSaving(true);
    try {
      await purchaseOrderApi.completeInspection(id, buildPayload());
      toast.success('Hoàn thành kiểm tra. Trạng thái: Chờ nhập kho.');
      reload();
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Không thể hoàn thành kiểm tra.');
    } finally { setSaving(false); }
  };

  const statusCfg = STATUS[order?.status] ?? STATUS.DRAFT;
  const total = (order?.items ?? []).reduce((s, i) => s + Number(i.totalCost ?? 0), 0);

  if (loading) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, minHeight: 320, color: '#94a3b8' }}>
      <Loader2 size={20} style={{ animation: 'spin 1s linear infinite' }} /> Đang tải...
    </div>
  );

  return (
    <main className={`${styles.page} product-workspace`}>
      {/* ── Header ── */}
      <div className={styles.header}>
        <div className={styles.titleGroup}>
          <button className={styles.secondaryButton} onClick={() => navigate(ROUTES.PURCHASE_ORDERS)}
            style={{ minHeight: 36, paddingInline: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <ArrowLeft size={16} /> Quay lại
          </button>
          <div className={styles.iconBox}><ShoppingBag size={20} /></div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <h1 className={styles.title} style={{ fontSize: 19 }}>
                {inspectMode ? 'Kiểm tra đơn mua hàng' : 'Chi tiết đơn mua hàng'}
              </h1>
              <span style={{ fontFamily: 'monospace', fontWeight: 700, fontSize: 15, color: '#2563eb' }}>{order.orderCode}</span>
              <span className={`${styles.badge} ${statusCfg.className}`}>{statusCfg.label}</span>
            </div>
            <p className={styles.subtitle}>
              {inspectMode ? 'Nhập số lượng thực tế nhận được từ nhà cung cấp' : 'Xem và kiểm tra đơn đặt hàng từ nhà cung cấp'}
            </p>
          </div>
        </div>
        {/* Header right: only Print button — inspection buttons moved to bottom */}
        <div className={styles.actions}>
          <button className={styles.secondaryButton} onClick={() => printOrder(order)}
            style={{ minHeight: 36, paddingInline: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <Printer size={15} /> In phiếu
          </button>
          {/* Create receipt only when INSPECTED */}
          {order.status === 'INSPECTED' && !order.receiptId && (
            <button className={styles.primaryButton}
              onClick={() => navigate(`${ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE}?purchaseOrderId=${order.id}`)}
              style={{ minHeight: 36, paddingInline: 14, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
              <Plus size={15} /> Tạo phiếu nhập kho
            </button>
          )}
        </div>
      </div>

      {/* ── Info cards ── */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 20 }}>
        <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 12, padding: '16px 20px' }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: '#64748b', textTransform: 'uppercase', marginBottom: 10, letterSpacing: '.05em' }}>Thông tin đơn hàng</div>
          {[
            ['Mã đơn', order.orderCode],
            ['Ngày lập', dt(order.orderDate ?? order.createdAt)],
            ['Người tạo', order.createdByName ?? '—'],
            ['Ngày dự kiến nhận', dateOnly(order.expectedReceiptDate)],
            ['Hình thức thanh toán', order.paymentMethod ?? '—'],
            ...(order.sentAt ? [['Đã gửi NCC', dt(order.sentAt)]] : []),
            ...(order.receivingAt ? [['Bắt đầu giao hàng', dt(order.receivingAt)]] : []),
            ...(order.inspectingAt ? [['Bắt đầu kiểm tra', dt(order.inspectingAt)]] : []),
            ...(order.inspectedAt ? [['Hoàn thành kiểm tra', dt(order.inspectedAt)]] : []),
            ...(order.completedAt ? [['Hoàn thành', dt(order.completedAt)]] : []),
            ...(order.receiptCode ? [['Phiếu nhập kho', order.receiptCode]] : []),
            ...(order.notes ? [['Ghi chú', order.notes]] : []),
          ].map(([label, val]) => (
            <div key={label} style={{ display: 'flex', gap: 8, fontSize: 13, marginBottom: 5 }}>
              <span style={{ color: '#64748b', minWidth: 180, flexShrink: 0 }}>{label}:</span>
              <span style={{ fontWeight: 600, color: '#0f172a' }}>{val}</span>
            </div>
          ))}
        </div>
        <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 12, padding: '16px 20px' }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: '#64748b', textTransform: 'uppercase', marginBottom: 10, letterSpacing: '.05em' }}>Nhà cung cấp &amp; Kho nhập</div>
          {[
            ['Nhà cung cấp', order.supplierName],
            ['Kho nhập', order.warehouseName],
            ...(order.warehouseAddress ? [['Địa chỉ kho', order.warehouseAddress]] : []),
          ].map(([label, val]) => (
            <div key={label} style={{ display: 'flex', gap: 8, fontSize: 13, marginBottom: 5 }}>
              <span style={{ color: '#64748b', minWidth: 180, flexShrink: 0 }}>{label}:</span>
              <span style={{ fontWeight: 600, color: '#0f172a' }}>{val}</span>
            </div>
          ))}
        </div>
      </div>

      {/* ── Items table ── */}
      <div style={{ background: '#fff', border: '1px solid #e2e8f0', borderRadius: 12, overflow: 'hidden', marginBottom: 20 }}>
        <div style={{ padding: '13px 20px', borderBottom: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontWeight: 700, fontSize: 15, color: '#0f172a' }}>Danh sách sản phẩm <span style={{ fontWeight: 400, fontSize: 12, color: '#94a3b8', marginLeft: 6 }}>{order.items?.length ?? 0} sản phẩm</span></span>
          {isInspectable && <span style={{ fontSize: 12, color: '#7c3aed', fontWeight: 600 }}>✏️ Nhập số lượng thực tế nhận được từ NCC</span>}
        </div>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead>
              <tr style={{ background: '#f8fafc' }}>
                {['STT','Tên sản phẩm','SKU','Đơn vị','SL đặt','SL thực tế','Ghi chú','Đơn giá (₫)','Thành tiền (₫)'].map((h, i) => (
                  <th key={h} style={{ padding: '9px 10px', textAlign: i >= 4 && i <= 5 ? 'right' : i === 0 ? 'center' : 'left', border: '1px solid #e2e8f0', fontWeight: 700, color: '#334155', whiteSpace: 'nowrap' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(order.items ?? []).map((item, i) => {
                const currentActual = actualQty[item.variantId] != null ? actualQty[item.variantId] : (item.actualQuantity ?? item.quantity);
                const displayActual = isInspectable ? currentActual : (item.actualQuantity ?? null);
                const actualNum = displayActual != null ? Number(displayActual) : null;
                const isSurplus = actualNum != null && actualNum > item.quantity;
                const isShort = actualNum != null && actualNum < item.quantity;
                return (
                  <tr key={item.id ?? i} style={{ borderBottom: '1px solid #f1f5f9', background: isSurplus ? '#fdf4ff' : isShort ? '#fff7ed' : undefined }}>
                    <td style={{ padding: '9px 10px', textAlign: 'center', border: '1px solid #e2e8f0', color: '#64748b' }}>{i + 1}</td>
                    <td style={{ padding: '9px 10px', border: '1px solid #e2e8f0' }}>
                      <div style={{ fontWeight: 600 }}>{item.productName}</div>
                      {item.variantName && <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 2 }}>{item.variantName}</div>}
                      {item.surplusNote && <div style={{ fontSize: 11, color: '#d97706', marginTop: 2 }}>📝 {item.surplusNote}</div>}
                    </td>
                    <td style={{ padding: '9px 10px', border: '1px solid #e2e8f0', fontFamily: 'monospace', fontSize: 12, color: '#0369a1' }}>{item.marketplaceSku ?? item.sku ?? '—'}</td>
                    <td style={{ padding: '9px 10px', border: '1px solid #e2e8f0', textAlign: 'center', color: '#64748b' }}>cái</td>
                    <td style={{ padding: '9px 10px', border: '1px solid #e2e8f0', textAlign: 'right', fontWeight: 700 }}>{num(item.quantity)}</td>
                    <td style={{ padding: '9px 10px', border: '1px solid #e2e8f0', textAlign: 'right' }}>
                      {isInspectable ? (
                        <input type="number" min="0" step="1"
                          value={actualQty[item.variantId] ?? (item.actualQuantity ?? item.quantity)}
                          onChange={(e) => setActualQty((prev) => ({ ...prev, [item.variantId]: e.target.value }))}
                          style={{ width: 80, padding: '4px 8px', borderRadius: 6, textAlign: 'right', fontSize: 13, fontWeight: 700,
                            border: `1px solid ${isSurplus ? '#a855f7' : isShort ? '#f97316' : '#cbd5e1'}` }} />
                      ) : (
                        <span style={{ fontWeight: 700, color: isSurplus ? '#7c3aed' : isShort ? '#f97316' : '#059669' }}>
                          {displayActual != null ? num(displayActual) : '—'}
                          {isSurplus && <span style={{ fontSize: 10, marginLeft: 4, color: '#7c3aed' }}>▲+{Number(displayActual) - item.quantity}</span>}
                          {isShort && <span style={{ fontSize: 10, marginLeft: 4, color: '#f97316' }}>▼-{item.quantity - Number(displayActual)}</span>}
                        </span>
                      )}
                    </td>
                    {/* Note column */}
                    <td style={{ padding: '9px 10px', border: '1px solid #e2e8f0', minWidth: 180 }}>
                      {isInspectable ? (
                        <input
                          type="text"
                          value={notes[item.variantId] ?? ''}
                          onChange={(e) => setNotes((prev) => ({ ...prev, [item.variantId]: e.target.value }))}
                          placeholder={
                            isSurplus ? `Thừa ${Number(actualQty[item.variantId] ?? item.quantity) - item.quantity} sp...` :
                            isShort  ? `Thiếu ${item.quantity - Number(actualQty[item.variantId] ?? item.quantity)} sp...` :
                            'Ghi chú...'
                          }
                          style={{ width: '100%', padding: '4px 8px', borderRadius: 6, fontSize: 12,
                            border: `1px solid ${isSurplus ? '#ddd6fe' : isShort ? '#fed7aa' : '#e2e8f0'}`,
                            background: isSurplus ? '#fdf4ff' : isShort ? '#fff7ed' : '#fff' }}
                        />
                      ) : (
                        <span style={{ fontSize: 12, color: item.surplusNote ? '#d97706' : '#94a3b8', fontStyle: item.surplusNote ? 'normal' : 'italic' }}>
                          {item.surplusNote || '—'}
                        </span>
                      )}
                    </td>
                    <td style={{ padding: '9px 10px', border: '1px solid #e2e8f0', textAlign: 'right' }}>{money(item.unitCost)}</td>
                    <td style={{ padding: '9px 10px', border: '1px solid #e2e8f0', textAlign: 'right', fontWeight: 700, color: '#2563eb' }}>{money(item.totalCost)}</td>
                  </tr>
                );
              })}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={8} style={{ padding: 10, textAlign: 'right', fontWeight: 700, fontSize: 14, border: '1px solid #e2e8f0', borderTop: '2px solid #e2e8f0', color: '#334155' }}>Tổng cộng:</td>
                <td style={{ padding: 10, textAlign: 'right', fontWeight: 800, fontSize: 15, border: '1px solid #e2e8f0', borderTop: '2px solid #e2e8f0', color: '#2563eb' }}>{money(total)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>

      {/* ── Inspection action buttons (only in inspect mode) ── */}
      {isInspectable && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, padding: '8px 0 16px' }}>
          <button className={styles.secondaryButton} disabled={saving} onClick={handleSave}
            style={{ minHeight: 40, paddingInline: 18, display: 'inline-flex', alignItems: 'center', gap: 6, borderColor: '#7c3aed', color: '#7c3aed' }}>
            {saving ? <Loader2 size={15} style={{ animation: 'spin 1s linear infinite' }} /> : <ClipboardCheck size={15} />}
            {saving ? 'Đang lưu...' : 'Tiếp tục kiểm tra'}
          </button>
          <button className={styles.primaryButton} disabled={saving} onClick={handleComplete}
            style={{ minHeight: 40, paddingInline: 20, display: 'inline-flex', alignItems: 'center', gap: 6, background: '#7c3aed', boxShadow: '0 4px 14px rgba(124,58,237,0.25)' }}>
            {saving ? <Loader2 size={15} style={{ animation: 'spin 1s linear infinite' }} /> : <ClipboardCheck size={15} />}
            {saving ? 'Đang xử lý...' : 'Hoàn thành kiểm tra'}
          </button>
        </div>
      )}
      {ConfirmDialog}
    </main>
  );
}
