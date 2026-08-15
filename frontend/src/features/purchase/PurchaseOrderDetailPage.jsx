import { useEffect, useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ImagePlus, Loader2, PackageCheck, PenLine, Plus, Printer, Send, ShoppingBag, Truck, X, XCircle } from 'lucide-react';
import { toast } from 'react-toastify';
import purchaseOrderApi from '../../api/purchaseOrderApi';
import { uploadImageToCloudinary } from '../../api/cloudinaryApi';
import { ROUTES } from '../../app/router/routes';
import { ROLES } from '../auth/constants/roles';
import useAuth from '../auth/hooks/useAuth';
import useConfirmDialog from '../inventory/hooks/useConfirmDialog';
import styles from './PurchaseOrderPage.module.css';

const STATUS = {
  DRAFT: { label: 'Nháp', className: styles.draft },
  SENT_TO_SUPPLIER: { label: 'Đã gửi NCC', className: styles.sent },
  RECEIVING: { label: 'Đang giao hàng', className: styles.receiving },
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
  const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
  const amount = Number(order.totalAmount ?? 0);
  const amountText = new Intl.NumberFormat('vi-VN').format(amount);
  const paymentMethods = { COD: 'Tiền mặt', BANK_TRANSFER: 'Chuyển khoản', CREDIT: 'Công nợ' };
  const threeDigitsToWords = (value, full) => {
    const digits = ['không', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín'];
    const hundreds = Math.floor(value / 100);
    const tens = Math.floor((value % 100) / 10);
    const ones = value % 10;
    let result = '';
    if (full || hundreds) result += `${digits[hundreds]} trăm`;
    if (tens > 1) result += `${result ? ' ' : ''}${digits[tens]} mươi${ones === 1 ? ' mốt' : ones === 5 ? ' lăm' : ones ? ` ${digits[ones]}` : ''}`;
    else if (tens === 1) result += `${result ? ' ' : ''}mười${ones === 5 ? ' lăm' : ones ? ` ${digits[ones]}` : ''}`;
    else if (ones) result += `${result ? (hundreds ? ' lẻ ' : ' ') : ''}${digits[ones]}`;
    return result.trim();
  };
  const amountInWords = (value) => {
    if (!Number.isFinite(value) || value === 0) return 'Không đồng';
    const units = ['', ' nghìn', ' triệu', ' tỷ'];
    const groups = [];
    let remainder = Math.floor(Math.abs(value));
    while (remainder) { groups.push(remainder % 1000); remainder = Math.floor(remainder / 1000); }
    const words = groups.map((group, index) => group ? `${threeDigitsToWords(group, index < groups.length - 1)}${units[index]}` : '').filter(Boolean).reverse().join(' ');
    return `${words.charAt(0).toUpperCase()}${words.slice(1)} đồng`;
  };
  const rows = (order.items ?? []).map((item, i) => `<tr>
    <td>${i + 1}</td>
    <td>${escapeHtml([item.productName, item.variantName].filter(Boolean).join(' - '))}</td>
    <td>cái</td>
    <td>${num(item.quantity)}</td>
    <td>${new Intl.NumberFormat('vi-VN').format(item.unitCost ?? 0)}</td>
    <td>${new Intl.NumberFormat('vi-VN').format(item.totalCost ?? 0)}</td>
  </tr>`).join('');
  const html = `<!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"><title>Phiếu đặt hàng</title>
    <style>
      @page{size:A4 portrait;margin:2.54cm}*{box-sizing:border-box}body{font-family:'Times New Roman',serif;font-size:13pt;line-height:1.15;margin:0;color:#000}.national{text-align:center;font-weight:700;margin:0}.motto{text-align:center;margin:2px 0 24px}.title{text-align:center;font-size:16pt;font-weight:700;margin:0 0 24px}.line{margin:0 0 9px}.items{width:100%;border-collapse:collapse;margin:12px 0 10px}.items th,.items td{border:1px solid #000;padding:5px 6px}.items th{text-align:center;font-weight:700;vertical-align:middle}.items td:nth-child(1),.items td:nth-child(3),.items td:nth-child(4){text-align:center}.items td:nth-child(5),.items td:nth-child(6){text-align:right}.items th:nth-child(1){width:7%}.items th:nth-child(2){width:37%}.items th:nth-child(3){width:13%}.items th:nth-child(4){width:12%}.items th:nth-child(5),.items th:nth-child(6){width:15.5%}.total{text-align:right;margin:0 0 9px}.signatures{display:flex;margin-top:38px;text-align:center}.signatures>div{width:33.333%}.signatures p{font-weight:700;margin:0 0 3px}.signatures small{font-size:11pt}@media print{body{font-size:13pt}}
    </style></head><body>
    <p class="national">CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM</p>
    <p class="motto">Độc lập – Tự do – Hạnh phúc</p>
    <p class="title">PHIẾU ĐẶT HÀNG</p>
    <p class="line">Kính gửi: Công ty ${escapeHtml(order.supplierName)}</p>
    <p class="line">MST: ${escapeHtml(order.supplierTaxCode)}</p>
    <p class="line">Nội dung đặt hàng như sau:</p>
    <table class="items"><thead><tr><th>STT</th><th>Tên hàng hóa/dịch vụ</th><th>Đơn vị tính</th><th>Số lượng</th><th>Đơn giá (VNĐ)</th><th>Thành tiền (VNĐ)</th></tr></thead><tbody>${rows}</tbody></table>
    <p class="total">Tổng cộng: ${amountText} VNĐ</p>
    <p class="line">(Bằng chữ): ${amountInWords(amount)}</p>
    <p class="line">Thời gian giao hàng: ${escapeHtml(dt(order.completedAt))}</p>
    <p class="line">Địa điểm giao hàng: ${escapeHtml([order.warehouseName, order.warehouseAddress].filter(Boolean).join(' - '))}</p>
    <p class="line">Hình thức thanh toán: ${escapeHtml(paymentMethods[order.paymentMethod] ?? order.paymentMethod)}</p>
    <p class="line">Ghi chú thêm: ${escapeHtml(order.notes)}</p>
    <div class="signatures"><div><p>Người lập phiếu</p><small>(Ký, ghi rõ họ tên)</small></div><div><p>Trưởng bộ phận</p><small>(Ký duyệt)</small></div><div><p>Bên nhận đơn hàng</p><small>(Ký xác nhận)</small></div></div>
    </body></html>`;
  win.document.open(); win.document.write(html); win.document.close();
  win.focus(); setTimeout(() => win.print(), 400);
}

export default function PurchaseOrderDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();

  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [uploadingEvidence, setUploadingEvidence] = useState(false);

  const canAct = [ROLES.SALES, ROLES.OWNER].includes(user?.role);
  const canReceive = [ROLES.OPERATIONS, ROLES.OWNER].includes(user?.role);
  const { confirm, ConfirmDialog } = useConfirmDialog();

  const reload = useCallback(() => {
    setLoading(true);
    purchaseOrderApi.getById(id)
      .then((data) => setOrder(data))
      .catch(() => toast.error('Không thể tải thông tin đơn đặt hàng.'))
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => {
    const timer = window.setTimeout(reload, 0);
    return () => window.clearTimeout(timer);
  }, [reload]);

  // Gửi NCC
  const handleSend = async () => {
    const confirmed = await confirm({
      title: 'Gửi đơn cho nhà cung cấp?',
      message: 'Đơn hàng sẽ chuyển sang trạng thái "Đã gửi NCC". Bạn sẽ không thể chỉnh sửa sau khi gửi.',
      confirmLabel: 'Gửi NCC',
      tone: 'warning',
    });
    if (!confirmed) return;
    setSaving(true);
    try {
      await purchaseOrderApi.send(id);
      toast.success('Đã gửi đơn cho nhà cung cấp.');
      reload();
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Không thể gửi đơn.');
    } finally { setSaving(false); }
  };

  // Xác nhận bên NCC đang giao hàng
  const handleConfirmShipping = async () => {
    const confirmed = await confirm({
      title: 'Xác nhận bên NCC đang giao hàng?',
      message: 'Đơn sẽ chuyển sang trạng thái "Đang giao hàng".',
      confirmLabel: 'Xác nhận',
      tone: 'warning',
    });
    if (!confirmed) return;
    setSaving(true);
    try {
      await purchaseOrderApi.confirmShipping(id);
      toast.success('Đã xác nhận bên NCC đang giao hàng.');
      reload();
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Không thể xác nhận.');
    } finally { setSaving(false); }
  };

  // Upload chứng từ (ảnh phiếu giao/phiếu nhập từ NCC)
  const handleUploadEvidence = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setUploadingEvidence(true);
    try {
      const url = await uploadImageToCloudinary(file);
      await purchaseOrderApi.updateEvidence(id, url);
      toast.success('Đã lưu chứng từ.');
      reload();
    } catch (err) {
      toast.error(err?.message || 'Không thể upload chứng từ.');
    } finally { setUploadingEvidence(false); }
  };

  // Xóa chứng từ
  const handleRemoveEvidence = async () => {
    setUploadingEvidence(true);
    try {
      await purchaseOrderApi.updateEvidence(id, null);
      toast.success('Đã xóa chứng từ.');
      reload();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Không thể xóa chứng từ.');
    } finally { setUploadingEvidence(false); }
  };

  // Hủy đơn
  const handleCancel = async () => {
    const confirmed = await confirm({
      title: 'Hủy đơn đặt hàng?',
      message: `Bạn chắc chắn muốn hủy đơn ${order?.orderCode}?\nThao tác này không thể hoàn tác.`,
      confirmLabel: 'Hủy đơn',
      tone: 'danger',
    });
    if (!confirmed) return;
    setSaving(true);
    try {
      await purchaseOrderApi.cancel(id);
      toast.success('Đã hủy đơn đặt hàng.');
      navigate(ROUTES.PURCHASE_ORDERS);
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Không thể hủy đơn.');
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
                Chi tiết đơn đặt hàng
              </h1>
              <span style={{ fontFamily: 'monospace', fontWeight: 700, fontSize: 15, color: '#2563eb' }}>{order.orderCode}</span>
              <span className={`${styles.badge} ${statusCfg.className}`}>{statusCfg.label}</span>
            </div>
            <p className={styles.subtitle}>
              Xem và kiểm tra đơn đặt hàng từ nhà cung cấp
            </p>
          </div>
        </div>
        {/* Header right: only Print button — inspection buttons moved to bottom */}
        <div className={styles.actions}>
          <button className={styles.secondaryButton} onClick={() => printOrder(order)}
            style={{ minHeight: 36, paddingInline: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <Printer size={15} /> In phiếu
          </button>
          {/* Sửa đơn / Gửi lại NCC — DRAFT / SENT_TO_SUPPLIER */}
          {canAct && ['DRAFT', 'SENT_TO_SUPPLIER'].includes(order.status) && (
            <button className={styles.secondaryButton}
              onClick={() => navigate(`${ROUTES.PURCHASE_ORDER_EDIT.replace(':id', order.id)}`)}
              style={{ minHeight: 36, paddingInline: 14, display: 'inline-flex', alignItems: 'center', gap: 6, color: '#0369a1', borderColor: '#bae6fd' }}>
              {order.status === 'SENT_TO_SUPPLIER' ? <Send size={15} /> : <PenLine size={15} />}
              {order.status === 'SENT_TO_SUPPLIER' ? 'Gửi lại NCC' : 'Sửa đơn'}
            </button>
          )}
          {/* Gửi NCC — DRAFT */}
          {canAct && order.status === 'DRAFT' && (
            <button className={styles.actionButton} disabled={saving} onClick={handleSend}
              style={{ minHeight: 36, paddingInline: 14, display: 'inline-flex', alignItems: 'center', gap: 6, color: '#0369a1', borderColor: '#bae6fd' }}>
              <Send size={15} /> Gửi NCC
            </button>
          )}
          {/* Xác nhận đang giao hàng — SENT_TO_SUPPLIER */}
          {canAct && order.status === 'SENT_TO_SUPPLIER' && (
            <button className={styles.actionButton} disabled={saving} onClick={handleConfirmShipping}
              style={{ minHeight: 36, paddingInline: 14, display: 'inline-flex', alignItems: 'center', gap: 6, color: '#0369a1', borderColor: '#bae6fd' }}>
              <Truck size={15} /> Xác nhận bên NCC đang giao hàng
            </button>
          )}
          {/* Tạo phiếu nhập kho — RECEIVING */}
          {canReceive && order.status === 'RECEIVING' && (
            <button className={styles.primaryButton}
              onClick={() => navigate(`${ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE}?purchaseOrderId=${order.id}`)}
              style={{ minHeight: 36, paddingInline: 14, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
              <Plus size={15} /> Tạo phiếu nhập kho
            </button>
          )}
          {/* Hủy đơn */}
          {canAct && order.status !== 'COMPLETED' && order.status !== 'CANCELLED' && order.status !== 'RECEIVING' && (
            <button className={styles.actionButton} disabled={saving} onClick={handleCancel}
              style={{ minHeight: 36, paddingInline: 14, display: 'inline-flex', alignItems: 'center', gap: 6, color: '#b91c1c', borderColor: '#fecaca' }}>
              <XCircle size={15} /> Hủy đơn
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
            ...(order.completedAt ? [['Hoàn thành', dt(order.completedAt)]] : []),
            ...(order.receipts?.length ? [['Số phiếu nhập', `${order.receipts.length} phiếu`]] : []),
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
        {/* Chứng từ đơn hàng */}
        <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 12, padding: '16px 20px', gridColumn: '1 / -1', display: 'flex', gap: 16, alignItems: 'flex-start' }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em', paddingTop: 4, minWidth: 120 }}>Chứng từ</div>
          <div style={{ flex: 1 }}>
            {order.evidenceUrl ? (
              <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start', flexWrap: 'wrap' }}>
                <a href={order.evidenceUrl} target="_blank" rel="noreferrer" title="Mở ảnh chứng từ">
                  <img src={order.evidenceUrl} alt="Chứng từ" style={{ width: 220, maxHeight: 150, objectFit: 'cover', borderRadius: 8, border: '1px solid #e2e8f0', background: '#fff' }} />
                </a>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'flex-start' }}>
                  <span style={{ fontSize: 12, color: '#64748b' }}>Ảnh phiếu giao hàng / phiếu nhập từ nhà cung cấp.</span>
                  {canAct && (
                    <div style={{ display: 'flex', gap: 8 }}>
                      <label className={styles.secondaryButton} style={{ minHeight: 34, paddingInline: 12, display: 'inline-flex', alignItems: 'center', gap: 6, cursor: uploadingEvidence ? 'not-allowed' : 'pointer', opacity: uploadingEvidence ? .6 : 1 }}>
                        <input type="file" accept="image/*" hidden onChange={handleUploadEvidence} disabled={uploadingEvidence} />
                        {uploadingEvidence ? <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> : <ImagePlus size={14} />}
                        {uploadingEvidence ? 'Đang tải...' : 'Thay ảnh khác'}
                      </label>
                      <button className={styles.actionButton} disabled={uploadingEvidence} onClick={handleRemoveEvidence}
                        style={{ minHeight: 34, paddingInline: 12, display: 'inline-flex', alignItems: 'center', gap: 6, color: '#b91c1c', borderColor: '#fecaca' }}>
                        <X size={14} /> Xóa
                      </button>
                    </div>
                  )}
                </div>
              </div>
            ) : canAct ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'flex-start' }}>
                <span style={{ fontSize: 12, color: '#94a3b8', fontStyle: 'italic' }}>Chưa có chứng từ. Tải lên ảnh phiếu giao hàng / phiếu nhập từ nhà cung cấp để lưu hồ sơ đơn hàng.</span>
                <label className={styles.secondaryButton} style={{ minHeight: 34, paddingInline: 12, display: 'inline-flex', alignItems: 'center', gap: 6, cursor: uploadingEvidence ? 'not-allowed' : 'pointer', opacity: uploadingEvidence ? .6 : 1 }}>
                  <input type="file" accept="image/*" hidden onChange={handleUploadEvidence} disabled={uploadingEvidence} />
                  {uploadingEvidence ? <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> : <ImagePlus size={14} />}
                  {uploadingEvidence ? 'Đang tải...' : 'Tải lên chứng từ'}
                </label>
              </div>
            ) : (
              <span style={{ fontSize: 12, color: '#94a3b8', fontStyle: 'italic' }}>Chưa có chứng từ.</span>
            )}
          </div>
        </div>
      </div>

      {/* ── Phiếu nhập kho ── */}
      <div style={{ background: '#fff', border: '1px solid #e2e8f0', borderRadius: 12, overflow: 'hidden', marginBottom: 20 }}>
        <div style={{ padding: '13px 20px', borderBottom: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontWeight: 700, fontSize: 15, color: '#0f172a' }}>Phiếu nhập kho
            {order.receipts?.length > 0 && <span style={{ fontWeight: 400, fontSize: 12, color: '#94a3b8', marginLeft: 6 }}>{order.receipts.length} phiếu</span>}
          </span>
          {canReceive && order.status === 'RECEIVING' && (
            <button className={styles.secondaryButton}
              onClick={() => navigate(`${ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE}?purchaseOrderId=${order.id}`)}
              style={{ minHeight: 32, paddingInline: 12, display: 'inline-flex', alignItems: 'center', gap: 6, color: '#0369a1', borderColor: '#bae6fd' }}>
              <Plus size={14} /> Tạo thêm phiếu nhập
            </button>
          )}
        </div>
        <div style={{ padding: 14 }}>
          {order.receipts?.length ? (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
              {order.receipts.map((receipt) => (
                <button key={receipt.id} className={styles.secondaryButton}
                  onClick={() => navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPT_DETAIL.replace(':id', receipt.id))}
                  style={{ minHeight: 36, paddingInline: 14, display: 'inline-flex', alignItems: 'center', gap: 8, fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontWeight: 700, fontSize: 12.5, color: '#2563eb', borderColor: '#bfdbfe' }}>
                  <PackageCheck size={15} style={{ color: '#2563eb' }} /> {receipt.receiptCode}
                </button>
              ))}
            </div>
          ) : (
            <div style={{ padding: '14px 4px', color: '#94a3b8', fontSize: 13, fontStyle: 'italic' }}>
              Đơn này chưa có phiếu nhập kho.
              {canReceive && order.status === 'RECEIVING' && ' Ấn "Tạo phiếu nhập kho" để tạo phiếu đầu tiên.'}
            </div>
          )}
        </div>
      </div>

      {/* ── Items table ── */}
      <div style={{ background: '#fff', border: '1px solid #e2e8f0', borderRadius: 12, overflow: 'hidden', marginBottom: 20 }}>
        <div style={{ padding: '13px 20px', borderBottom: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontWeight: 700, fontSize: 15, color: '#0f172a' }}>Danh sách sản phẩm <span style={{ fontWeight: 400, fontSize: 12, color: '#94a3b8', marginLeft: 6 }}>{order.items?.length ?? 0} sản phẩm</span></span>
        </div>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead>
              <tr style={{ background: '#f8fafc' }}>
                {['STT','Tên sản phẩm','SKU','Đơn vị','Số lượng','Đơn giá (₫)','Thành tiền (₫)'].map((h, i) => (
                  <th key={h} style={{ padding: '9px 10px', textAlign: i >= 4 ? 'right' : i === 0 ? 'center' : 'left', border: '1px solid #e2e8f0', fontWeight: 700, color: '#334155', whiteSpace: 'nowrap' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(order.items ?? []).map((item, i) => (
                <tr key={item.id ?? i} style={{ borderBottom: '1px solid #f1f5f9' }}>
                  <td style={{ padding: '9px 10px', textAlign: 'center', border: '1px solid #e2e8f0', color: '#64748b' }}>{i + 1}</td>
                  <td style={{ padding: '9px 10px', border: '1px solid #e2e8f0' }}>
                    <div style={{ fontWeight: 600 }}>{item.productName}</div>
                    {item.variantName && <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 2 }}>{item.variantName}</div>}
                  </td>
                  <td style={{ padding: '9px 10px', border: '1px solid #e2e8f0', fontFamily: 'monospace', fontSize: 12, color: '#0369a1' }}>{item.marketplaceSku ?? item.sku ?? '—'}</td>
                  <td style={{ padding: '9px 10px', border: '1px solid #e2e8f0', textAlign: 'center', color: '#64748b' }}>cái</td>
                  <td style={{ padding: '9px 10px', border: '1px solid #e2e8f0', textAlign: 'right', fontWeight: 700 }}>{num(item.quantity)}</td>
                  <td style={{ padding: '9px 10px', border: '1px solid #e2e8f0', textAlign: 'right' }}>{money(item.unitCost)}</td>
                  <td style={{ padding: '9px 10px', border: '1px solid #e2e8f0', textAlign: 'right', fontWeight: 700, color: '#2563eb' }}>{money(item.totalCost)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={6} style={{ padding: 10, textAlign: 'right', fontWeight: 700, fontSize: 14, border: '1px solid #e2e8f0', borderTop: '2px solid #e2e8f0', color: '#334155' }}>Tổng cộng:</td>
                <td style={{ padding: 10, textAlign: 'right', fontWeight: 800, fontSize: 15, border: '1px solid #e2e8f0', borderTop: '2px solid #e2e8f0', color: '#2563eb' }}>{money(total)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>

      {ConfirmDialog}
    </main>
  );
}
