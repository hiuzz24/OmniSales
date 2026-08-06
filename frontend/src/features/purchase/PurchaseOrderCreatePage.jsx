import {
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Building2,
  Check,
  ChevronDown,
  Loader2,
  Package,
  Plus,
  Search,
  ShoppingBag,
  Trash2,
  X,
} from 'lucide-react';
import { toast } from 'react-toastify';
import purchaseOrderApi from '../../api/purchaseOrderApi';
import { ROUTES } from '../../app/router/routes';
import useConfirmDialog from '../inventory/hooks/useConfirmDialog';
import styles from './PurchaseOrderPage.module.css';

const SUPPLIER_PAGE_SIZE = 20;
const PRODUCT_GROUP_BATCH_SIZE = 50;

const money = (value) => new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
}).format(value ?? 0);

const PLATFORM_LABELS = {
  SHOPIFY: 'Shopify',
  LAZADA: 'Lazada',
  TIKTOK: 'TikTok Shop',
};

const sourcePlatforms = (sources = []) => [...new Set(
  sources.map((source) => source.platform).filter(Boolean),
)];

const sourceNames = (sources = []) => sourcePlatforms(sources)
  .map((platform) => PLATFORM_LABELS[platform] || platform)
  .join(', ');

function PlatformBadges({ sources = [], platforms = [] }) {
  const values = sources.length ? sourcePlatforms(sources) : [...new Set(platforms)];
  if (!values.length) return <span className={styles.platformMuted}>Nội bộ</span>;
  return (
    <span className={styles.platformBadges}>
      {values.map((platform) => (
        <span key={platform} className={`${styles.platformBadge} ${styles[`platform${platform}`] ?? ''}`}>
          {PLATFORM_LABELS[platform] || platform}
        </span>
      ))}
    </span>
  );
}

function SupplierCreateModal({ open, onClose, onCreated }) {
  const [form, setForm] = useState({
    name: '',
    contactName: '',
    phone: '',
    taxCode: '',
    email: '',
    address: '',
  });
  const [saving, setSaving] = useState(false);
  const [nameError, setNameError] = useState('');
  const [phoneError, setPhoneError] = useState('');
  const [taxCodeError, setTaxCodeError] = useState('');

  useEffect(() => {
    if (!open) return undefined;
    const onKeyDown = (event) => {
      if (event.key === 'Escape' && !saving) onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose, open, saving]);

  if (!open) return null;

  const update = (field) => (event) => {
    setForm((current) => ({ ...current, [field]: event.target.value }));
    if (field === 'name') setNameError('');
    if (field === 'phone') setPhoneError('');
    if (field === 'taxCode') setTaxCodeError('');
  };

  const submit = async (event) => {
    event.preventDefault();
    let valid = true;
    if (!form.name.trim()) { setNameError('Vui lòng nhập tên nhà cung cấp.'); valid = false; }
    if (!form.phone.trim()) { setPhoneError('Số điện thoại là bắt buộc.'); valid = false; }
    if (!form.taxCode.trim()) { setTaxCodeError('Mã số thuế (MST) là bắt buộc.'); valid = false; }
    if (!valid) return;
    setSaving(true);
    try {
      const created = await purchaseOrderApi.createSupplier({
        ...form,
        name: form.name.trim(),
        contactName: form.contactName.trim() || null,
        phone: form.phone.trim(),
        taxCode: form.taxCode.trim(),
        email: form.email.trim() || null,
        address: form.address.trim() || null,
        isActive: true,
      });
      onCreated(created);
      toast.success('Đã thêm và chọn nhà cung cấp mới.');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Không thể thêm nhà cung cấp. Vui lòng thử lại.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div
      className={styles.modalBackdrop}
      role="presentation"
      onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) onClose(); }}
    >
      <section className={`${styles.modal} ${styles.supplierCreateModal}`} role="dialog" aria-modal="true" aria-labelledby="supplier-create-title">
        <div className={styles.modalHeader}>
          <div>
            <h2 id="supplier-create-title" className={styles.modalTitle}>Thêm nhà cung cấp</h2>
            <p className={styles.modalSubtitle}>Mã nhà cung cấp sẽ được hệ thống tạo tự động.</p>
          </div>
          <button type="button" className={styles.modalCloseButton} onClick={onClose} disabled={saving} aria-label="Đóng">
            <X size={18} />
          </button>
        </div>
        <form onSubmit={submit}>
          <div className={styles.supplierFormGrid}>
            <div className={styles.field}>
              <label htmlFor="new-supplier-name">Tên nhà cung cấp *</label>
              <input
                id="new-supplier-name"
                autoFocus
                className={`${styles.input} ${nameError ? styles.inputInvalid : ''}`}
                value={form.name}
                onChange={update('name')}
                aria-invalid={Boolean(nameError)}
                aria-describedby={nameError ? 'supplier-name-error' : undefined}
                placeholder="Ví dụ: Công ty Thời Trang Việt"
              />
              {nameError && <p id="supplier-name-error" className={styles.fieldError} role="alert">{nameError}</p>}
            </div>
            <div className={styles.field}>
              <label htmlFor="new-supplier-taxcode">Mã số thuế (MST) *</label>
              <input
                id="new-supplier-taxcode"
                className={`${styles.input} ${taxCodeError ? styles.inputInvalid : ''}`}
                value={form.taxCode}
                onChange={update('taxCode')}
                aria-invalid={Boolean(taxCodeError)}
                aria-describedby={taxCodeError ? 'supplier-taxcode-error' : undefined}
                placeholder="Ví dụ: 0123456789"
              />
              {taxCodeError && <p id="supplier-taxcode-error" className={styles.fieldError} role="alert">{taxCodeError}</p>}
            </div>
            <div className={styles.field}>
              <label htmlFor="new-supplier-contact">Người liên hệ</label>
              <input id="new-supplier-contact" className={styles.input} value={form.contactName} onChange={update('contactName')} placeholder="Nguyễn Văn A" />
            </div>
            <div className={styles.field}>
              <label htmlFor="new-supplier-phone">Số điện thoại *</label>
              <input
                id="new-supplier-phone"
                type="tel"
                autoComplete="tel"
                className={`${styles.input} ${phoneError ? styles.inputInvalid : ''}`}
                value={form.phone}
                onChange={update('phone')}
                aria-invalid={Boolean(phoneError)}
                aria-describedby={phoneError ? 'supplier-phone-error' : undefined}
                placeholder="0901 234 567"
              />
              {phoneError && <p id="supplier-phone-error" className={styles.fieldError} role="alert">{phoneError}</p>}
            </div>
            <div className={styles.field}>
              <label htmlFor="new-supplier-email">Email</label>
              <input id="new-supplier-email" type="email" autoComplete="email" className={styles.input} value={form.email} onChange={update('email')} placeholder="contact@company.vn" />
            </div>
            <div className={`${styles.field} ${styles.fieldFull}`}>
              <label htmlFor="new-supplier-address">Địa chỉ</label>
              <input id="new-supplier-address" autoComplete="street-address" className={styles.input} value={form.address} onChange={update('address')} placeholder="Số nhà, tên đường, quận/huyện, tỉnh/thành" />
            </div>
          </div>
          <div className={styles.modalFooter}>
            <button type="button" className={styles.secondaryButton} onClick={onClose} disabled={saving}>Hủy</button>
            <button type="submit" className={styles.primaryButton} disabled={saving}>
              {saving ? <Loader2 size={17} className={styles.spin} /> : <Check size={17} />}
              {saving ? 'Đang lưu...' : 'Lưu nhà cung cấp'}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function SupplierLazySelect({ selected, onChange }) {
  const listboxId = useId();
  const rootRef = useRef(null);
  const requestIdRef = useRef(0);
  const [open, setOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [options, setOptions] = useState([]);
  const [page, setPage] = useState(0);
  const [last, setLast] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const loadPage = useCallback(async (pageIndex, replace, searchText) => {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError('');
    try {
      const result = await purchaseOrderApi.getSuppliers({
        page: pageIndex,
        size: SUPPLIER_PAGE_SIZE,
        keyword: searchText.trim(),
      });
      if (requestId !== requestIdRef.current) return;
      const content = result?.content ?? [];
      setOptions((current) => {
        const merged = replace ? content : [...current, ...content];
        return [...new Map(merged.map((item) => [item.id, item])).values()];
      });
      setPage(result?.page ?? pageIndex);
      setLast(Boolean(result?.last) || content.length < SUPPLIER_PAGE_SIZE);
    } catch {
      if (requestId === requestIdRef.current) setError('Không tải được nhà cung cấp.');
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!open) return undefined;
    const timer = window.setTimeout(() => loadPage(0, true, keyword), 280);
    return () => window.clearTimeout(timer);
  }, [keyword, loadPage, open]);

  useEffect(() => {
    const handlePointerDown = (event) => {
      if (rootRef.current && !rootRef.current.contains(event.target)) setOpen(false);
    };
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, []);

  const handleScroll = (event) => {
    const element = event.currentTarget;
    if (!loading && !last && element.scrollHeight - element.scrollTop - element.clientHeight < 56) {
      loadPage(page + 1, false, keyword);
    }
  };

  const handleCreated = (supplier) => {
    setOptions((current) => [supplier, ...current.filter((item) => item.id !== supplier.id)]);
    onChange(supplier);
    setCreateOpen(false);
    setKeyword('');
  };

  return (
    <>
      <div className={styles.supplierSelect} ref={rootRef}>
        <button
          id="supplier"
          type="button"
          className={`${styles.supplierTrigger} ${open ? styles.supplierTriggerOpen : ''}`}
          role="combobox"
          aria-haspopup="listbox"
          aria-expanded={open}
          aria-controls={listboxId}
          aria-label="Chọn nhà cung cấp"
          onClick={() => setOpen((current) => !current)}
        >
          <span className={styles.supplierTriggerContent}>
            {selected ? (
              <>
                <span className={styles.supplierTriggerName}>{selected.name}</span>
                <span className={styles.supplierTriggerMeta}>{[selected.code, selected.contactName, selected.phone].filter(Boolean).join(' · ') || 'Nhà cung cấp đang hoạt động'}</span>
              </>
            ) : <span className={styles.supplierPlaceholder}>Chọn nhà cung cấp</span>}
          </span>
          <ChevronDown size={17} className={open ? styles.chevronOpen : ''} />
        </button>

        {open && (
          <div className={styles.supplierDropdown}>
            <div className={styles.supplierSearch}>
              <Search size={16} />
              <input
                autoFocus
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                placeholder="Tìm tên, mã, người liên hệ, SĐT..."
                aria-label="Tìm nhà cung cấp"
              />
            </div>
            <div id={listboxId} className={styles.supplierOptionScroller} role="listbox" onScroll={handleScroll}>
              {options.map((supplier) => {
                const active = selected?.id === supplier.id;
                return (
                  <button
                    type="button"
                    role="option"
                    aria-selected={active}
                    className={`${styles.supplierOption} ${active ? styles.supplierOptionActive : ''}`}
                    key={supplier.id}
                    onClick={() => { onChange(supplier); setOpen(false); setKeyword(''); }}
                  >
                    <span className={styles.supplierOptionIcon}><Building2 size={17} /></span>
                    <span className={styles.supplierOptionText}>
                      <strong>{supplier.name}</strong>
                      <small>{[supplier.code, supplier.contactName, supplier.phone].filter(Boolean).join(' · ') || 'Chưa có thông tin liên hệ'}</small>
                    </span>
                    {active && <Check size={17} className={styles.supplierCheck} />}
                  </button>
                );
              })}
              {loading && (
                <div className={styles.dropdownStatus} role="status"><Loader2 size={16} className={styles.spin} /> Đang tải...</div>
              )}
              {!loading && error && (
                <button type="button" className={styles.dropdownRetry} onClick={() => loadPage(0, true, keyword)}>{error} Thử lại</button>
              )}
              {!loading && !error && !options.length && (
                <div className={styles.dropdownStatus}>Không tìm thấy nhà cung cấp phù hợp.</div>
              )}
            </div>
            <button
              type="button"
              className={styles.supplierAddFixed}
              onClick={() => { setOpen(false); setCreateOpen(true); }}
            >
              <span><Plus size={17} /></span>
              <strong>Thêm nhà cung cấp</strong>
            </button>
          </div>
        )}
      </div>
      <SupplierCreateModal key={String(createOpen)} open={createOpen} onClose={() => setCreateOpen(false)} onCreated={handleCreated} />
    </>
  );
}

function ProductPickerModal({ onClose, onConfirm, catalog, existingGroupKeys }) {
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState({});
  const [visibleCount, setVisibleCount] = useState(PRODUCT_GROUP_BATCH_SIZE);
  const existingKeys = useMemo(() => new Set(existingGroupKeys), [existingGroupKeys]);

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  const results = useMemo(() => {
    const search = keyword.trim().toLowerCase();
    if (!search) return catalog;
    return catalog.filter((group) => [
      group.sku,
      group.productName,
      ...(group.platforms ?? []),
      ...(group.variants ?? []).flatMap((variant) => [
        variant.sku,
        variant.marketplaceSku,
        variant.productName,
        variant.variantName,
        sourceNames(variant.marketplaceSources),
      ]),
    ].filter(Boolean).join(' ').toLowerCase().includes(search));
  }, [catalog, keyword]);

  const toggleGroup = (group) => {
    if (existingKeys.has(group.groupKey)) return;
    setSelected((current) => {
      const next = { ...current };
      if (next[group.groupKey]) delete next[group.groupKey];
      else next[group.groupKey] = group;
      return next;
    });
  };

  const selectedCount = Object.keys(selected).length;
  const visibleResults = results.slice(0, visibleCount);

  return (
    <div className={styles.modalBackdrop} role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section className={`${styles.modal} ${styles.productPickerModal}`} role="dialog" aria-modal="true" aria-labelledby="product-picker-title">
        <div className={styles.modalHeader}>
          <div>
            <h2 id="product-picker-title" className={styles.modalTitle}>Chọn sản phẩm</h2>
            <p className={styles.modalSubtitle}>Mỗi dòng là một sản phẩm con; các liên kết sàn dùng chung SKU được gộp để chỉ nhập số lượng một lần.</p>
          </div>
          <button type="button" className={styles.modalCloseButton} onClick={onClose} aria-label="Đóng"><X size={18} /></button>
        </div>
        <div className={styles.productPickerSearch}>
          <Search size={17} />
          <input
            autoFocus
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value);
              setVisibleCount(PRODUCT_GROUP_BATCH_SIZE);
            }}
            placeholder="Tìm theo tên sản phẩm, SKU hoặc sàn..."
          />
          <span>{results.length} nhóm SKU</span>
        </div>
        <div
          className={styles.productPickerList}
          onScroll={(event) => {
            const element = event.currentTarget;
            if (element.scrollHeight - element.scrollTop - element.clientHeight < 80) {
              setVisibleCount((count) => Math.min(count + PRODUCT_GROUP_BATCH_SIZE, results.length));
            }
          }}
        >
          {visibleResults.map((group) => {
            const variants = group.variants ?? [];
            const disabled = existingKeys.has(group.groupKey);
            const checked = Boolean(selected[group.groupKey]);
            const variantNames = [...new Set(variants.map((variant) => variant.variantName).filter(Boolean))];
            return (
              <article className={styles.productPickerGroup} key={group.groupKey}>
                <button type="button" className={styles.productPickerGroupHeader} onClick={() => toggleGroup(group)} disabled={disabled}>
                  <input type="checkbox" tabIndex={-1} readOnly checked={checked} disabled={disabled} aria-hidden="true" />
                  <span className={styles.groupSku}>{group.sku || 'Không có SKU'}</span>
                  <span className={styles.groupInfo}>
                    <strong>{group.productName}</strong>
                    <small>
                      {variantNames.join(' · ') || 'Mặc định'} · {variants.length} liên kết variant
                    </small>
                  </span>
                  <PlatformBadges platforms={group.platforms ?? []} />
                  {disabled && <span className={styles.alreadyAdded}>Đã có</span>}
                </button>
              </article>
            );
          })}
          {!results.length && (
            <div className={styles.productPickerEmpty}>
              <Package size={24} />
              <strong>Không tìm thấy sản phẩm</strong>
              <span>Thử tên sản phẩm, SKU hoặc tên sàn khác.</span>
            </div>
          )}
          {visibleCount < results.length && <div className={styles.dropdownStatus}>Cuộn xuống để tải thêm...</div>}
        </div>
        <div className={styles.modalFooter}>
          <span className={styles.selectedCount}>{selectedCount ? `Đã chọn ${selectedCount} sản phẩm con` : 'Chưa chọn sản phẩm nào'}</span>
          <div className={styles.modalFooterActions}>
            <button type="button" className={styles.secondaryButton} onClick={onClose}>Hủy</button>
            <button type="button" className={styles.primaryButton} disabled={!selectedCount} onClick={() => onConfirm(Object.values(selected))}>
              <Plus size={17} /> Thêm ({selectedCount})
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}

export default function PurchaseOrderCreatePage() {
  const navigate = useNavigate();
  const { confirm, ConfirmDialog } = useConfirmDialog();
  const today = new Date().toISOString().slice(0, 10);
  const [orderCode, setOrderCode] = useState('');
  const [supplier, setSupplier] = useState(null);
  const [expectedDate, setExpectedDate] = useState(today);
  const [paymentMethod, setPaymentMethod] = useState('COD');
  const [notes, setNotes] = useState('');
  const [warehouse, setWarehouse] = useState(null);
  const [catalog, setCatalog] = useState([]);
  const [items, setItems] = useState([]);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [loadingOptions, setLoadingOptions] = useState(true);
  const purchaseTime = useMemo(() => new Date().toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }), []);

  useEffect(() => {
    purchaseOrderApi.getFormOptions()
      .then((options) => {
        setOrderCode(options?.orderCode ?? '');
        setWarehouse(options?.warehouse ?? null);
        setCatalog(options?.productGroups ?? []);
      })
      .catch(() => toast.error('Không thể tải dữ liệu tạo đơn mua hàng.'))
      .finally(() => setLoadingOptions(false));
  }, []);

  const totalQuantity = useMemo(
    () => items.reduce((sum, item) => sum + Number(item.quantity || 0), 0),
    [items],
  );
  const totalAmount = useMemo(
    () => items.reduce((sum, item) => sum + Number(item.quantity || 0) * Number(item.unitCost || 0), 0),
    [items],
  );
  const warehousePlatformCount = useMemo(
    () => new Set((warehouse?.marketplaceWarehouses ?? []).map((mapping) => mapping.platform)).size,
    [warehouse],
  );

  const addProducts = (groups) => {
    setItems((current) => {
      const currentKeys = new Set(current.map((item) => item.groupKey));
      const additions = groups
        .filter((group) => !currentKeys.has(group.groupKey))
        .map((group) => {
          const variants = group.variants ?? [];
          const marketplaceSources = variants.flatMap((variant) => variant.marketplaceSources ?? []);
          const variantNames = [...new Set(variants.map((variant) => variant.variantName).filter(Boolean))];
          const unitPrice = variants.find((variant) => Number(variant.unitPrice ?? variant.price) > 0)?.unitPrice
            ?? variants.find((variant) => Number(variant.price) > 0)?.price
            ?? variants[0]?.unitPrice
            ?? variants[0]?.price
            ?? 0;
          const canonicalSku = String(group.sku ?? '').trim().toLowerCase();
          const representative = variants.find(
            (variant) => String(variant.sku ?? '').trim().toLowerCase() === canonicalSku,
          ) ?? variants[0];
          return {
            groupKey: group.groupKey,
            variantId: representative?.variantId,
            linkedVariantIds: variants.map((variant) => variant.variantId),
            sku: group.sku || variants[0]?.marketplaceSku || variants[0]?.sku,
            localSkus: [...new Set(variants.map((variant) => variant.sku).filter(Boolean))],
            productName: group.productName || variants[0]?.productName,
            variantName: variantNames.join(' · ') || 'Mặc định',
            marketplaceSources,
            quantity: 1,
            unitCost: Number(unitPrice),
          };
        });
      return [...current, ...additions];
    });
    setPickerOpen(false);
  };

  const updateItem = (groupKey, key, value) => setItems((current) => current.map(
    (item) => (item.groupKey === groupKey ? { ...item, [key]: value } : item),
  ));

  const submit = async (isDraft) => {
    if (!orderCode) return toast.error('Chưa tạo được mã đơn mua hàng.');
    if (!supplier?.id) return toast.error('Vui lòng chọn nhà cung cấp.');
    if (!expectedDate || expectedDate < today) return toast.error('Ngày dự kiến nhận không được trước hôm nay.');
    if (!items.length) return toast.error('Vui lòng thêm ít nhất một sản phẩm.');
    if (items.some((item) => Number(item.quantity) <= 0 || Number(item.unitCost) < 0)) {
      return toast.error('Số lượng và đơn giá sản phẩm không hợp lệ.');
    }

    // Confirm only when sending to supplier (not draft)
    if (!isDraft) {
      const confirmed = await confirm({
        title: 'Gửi đơn cho nhà cung cấp?',
        message: `Đơn mua hàng ${orderCode} sẽ được gửi đến "${supplier.name}".\nSau khi gửi, đơn sẽ chuyển sang trạng thái Đã gửi NCC.`,
        confirmLabel: 'Gửi NCC',
        tone: 'warning',
      });
      if (!confirmed) return undefined;
    }

    setSubmitting(true);
    try {
      await purchaseOrderApi.create({
        orderCode,
        supplierId: supplier.id,
        expectedReceiptDate: expectedDate,
        paymentMethod,
        notes: notes || null,
        isDraft,
        items: items.map((item) => ({
          variantId: item.variantId,
          quantity: Number(item.quantity),
          unitCost: Number(item.unitCost),
        })),
      });
      toast.success(isDraft
        ? 'Đã lưu nháp đơn mua hàng.'
        : 'Đã gửi đơn cho nhà cung cấp. Đơn sẽ chuyển sang Đang giao hàng sau 10 giây.');
      navigate(ROUTES.PURCHASE_ORDERS);
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Không thể tạo đơn mua hàng.');
    } finally {
      setSubmitting(false);
    }
    return undefined;
  };

  return (
    <main className={`${styles.page} product-workspace`}>
      <div className={styles.header}>
        <div className={styles.titleGroup}>
          <button type="button" className={styles.secondaryButton} onClick={() => navigate(ROUTES.PURCHASE_ORDERS)}>
            <ArrowLeft size={18} /> Quay lại
          </button>
          <div className={styles.iconBox}><ShoppingBag size={22} /></div>
          <div>
            <h1 className={styles.title}>Tạo đơn mua hàng</h1>
            <p className={styles.subtitle}>Đặt hàng từ nhà cung cấp vào kho mặc định đa sàn</p>
          </div>
        </div>
        <div className={styles.actions}>
          <button type="button" disabled={submitting || loadingOptions} className={styles.secondaryButton} onClick={() => submit(true)}>Lưu nháp</button>
          <button type="button" disabled={submitting || loadingOptions} className={styles.primaryButton} onClick={() => submit(false)}>
            {submitting && <Loader2 size={17} className={styles.spin} />} Gửi NCC
          </button>
        </div>
      </div>

      <div className={styles.formLayout}>
        <div>
          <section className={styles.card}>
            <h2 className={styles.cardTitle}>Thông tin đơn hàng</h2>
            <div className={styles.grid}>
              <div className={styles.field}>
                <label htmlFor="orderCode">Mã đơn</label>
                <input id="orderCode" className={styles.input} value={orderCode || 'Đang tạo mã...'} readOnly aria-readonly="true" />
              </div>
              <div className={styles.field}>
                <label htmlFor="purchaseTime">Thời gian mua</label>
                <input id="purchaseTime" className={styles.input} value={purchaseTime} readOnly aria-readonly="true" />
              </div>
              <div className={`${styles.field} ${styles.supplierField}`}>
                <label htmlFor="supplier">Nhà cung cấp *</label>
                <SupplierLazySelect selected={supplier} onChange={setSupplier} />
              </div>
              <div className={styles.field}>
                <label htmlFor="warehouse">Kho nhập mặc định</label>
              <input id="warehouse" className={styles.input} value={warehouse ? `${warehouse.name} - ${warehouse.address ?? ''}` : 'Đang tải...'} readOnly aria-readonly="true" />
              </div>
              <div className={styles.field}>
                <label htmlFor="expectedDate">Dự kiến nhận *</label>
                <input id="expectedDate" type="date" min={today} className={styles.input} value={expectedDate} onChange={(event) => setExpectedDate(event.target.value)} />
              </div>
              <div className={styles.field}>
                <label htmlFor="payment">Thanh toán</label>
                <select id="payment" className={styles.select} value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value)}>
                  <option value="COD">Nhận hàng trả tiền</option>
                  <option value="BANK_TRANSFER">Chuyển khoản</option>
                  <option value="CREDIT">Công nợ</option>
                </select>
              </div>
            </div>
            <div className={styles.notesField}>
              <label htmlFor="notes">Ghi chú</label>
              <textarea id="notes" className={styles.textarea} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Ghi chú thêm cho đơn hàng..." />
            </div>
          </section>

          <section className={`${styles.card} ${styles.productTableCard}`}>
            <div className={styles.productHeader}>
              <div>
                <h2 className={styles.cardTitle}>Danh sách sản phẩm</h2>
              </div>
              <button type="button" className={styles.secondaryButton} disabled={loadingOptions} onClick={() => setPickerOpen(true)}>
                {loadingOptions ? <Loader2 size={17} className={styles.spin} /> : <Plus size={17} />} Thêm sản phẩm
              </button>
            </div>

            {!items.length ? (
              <div className={styles.productEmpty}>
                <span><Package size={24} /></span>
                <strong>Chưa có sản phẩm nào</strong>
                <p>Nhấn “Thêm sản phẩm” để chọn các sản phẩm con đã gộp theo SKU từ các sàn.</p>
              </div>
            ) : (
              <>
                <div className={styles.purchaseTableWrap}>
                  <table className={styles.purchaseItemTable}>
                    <thead>
                      <tr>
                        <th>Tên sản phẩm</th>
                        <th>SKU</th>
                        <th>Số lượng</th>
                        <th>Đơn giá (₫)</th>
                        <th className={styles.money}>Thành tiền</th>
                        <th aria-label="Thao tác" />
                      </tr>
                    </thead>
                    <tbody>
                      {items.map((item) => {
                        const lineTotal = Number(item.quantity || 0) * Number(item.unitCost || 0);
                        const quantityInvalid = Number(item.quantity) <= 0;
                        const costInvalid = Number(item.unitCost) < 0;
                        return (
                          <tr key={item.groupKey}>
                            <td>
                              <strong className={styles.itemName}>{item.productName}</strong>
                              <span className={styles.itemVariant}>{item.variantName || 'Mặc định'}</span>
                              <PlatformBadges sources={item.marketplaceSources} />
                            </td>
                            <td>
                              <span className={styles.skuTag}>{item.sku || '—'}</span>
                            </td>
                            <td>
                              <input
                                className={`${styles.tableInput} ${quantityInvalid ? styles.inputInvalid : ''}`}
                                type="number"
                                min="1"
                                step="1"
                                value={item.quantity}
                                onChange={(event) => updateItem(item.groupKey, 'quantity', event.target.value)}
                                aria-label={`Số lượng ${item.productName}`}
                              />
                            </td>
                            <td>
                              <input
                                className={`${styles.tableInput} ${costInvalid ? styles.inputInvalid : ''}`}
                                type="number"
                                min="0"
                                step="1000"
                                value={item.unitCost}
                                onChange={(event) => updateItem(item.groupKey, 'unitCost', event.target.value)}
                                aria-label={`Đơn giá ${item.productName}`}
                              />
                            </td>
                            <td className={`${styles.money} ${styles.lineTotal}`}>{lineTotal > 0 ? money(lineTotal) : '—'}</td>
                            <td>
                              <button type="button" className={styles.removeButton} onClick={() => setItems((current) => current.filter((row) => row.groupKey !== item.groupKey))} aria-label={`Xóa ${item.productName}`}>
                                <Trash2 size={15} />
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
                <div className={styles.productTableFooter}>
                  <span><strong>{items.length}</strong> sản phẩm con</span>
                  <span>Tổng SL: <strong>{totalQuantity.toLocaleString('vi-VN')}</strong></span>
                  <span className={styles.productTableTotal}>Tổng giá trị <strong>{money(totalAmount)}</strong></span>
                </div>
              </>
            )}
          </section>
        </div>

        <aside className={`${styles.card} ${styles.sticky}`}>
          <h2 className={styles.cardTitle}>Tóm tắt đơn</h2>
          <div className={styles.summaryLine}><span>Mã đơn</span><strong>{orderCode || '—'}</strong></div>
          <div className={styles.summaryLine}><span>Nhà cung cấp</span><strong>{supplier?.name ?? 'Chưa chọn'}</strong></div>
          <div className={styles.summaryLine}><span>Kho nhập</span><strong>{warehouse?.name ?? '—'}</strong></div>
          <div className={styles.summaryLine}><span>Sản phẩm con</span><strong>{items.length}</strong></div>
          <div className={styles.summaryLine}><span>Tổng số lượng</span><strong>{totalQuantity}</strong></div>
          <div className={`${styles.summaryLine} ${styles.total}`}><span>Tổng tiền</span><strong>{money(totalAmount)}</strong></div>
        </aside>
      </div>

      {pickerOpen && (
        <ProductPickerModal
          onClose={() => setPickerOpen(false)}
          onConfirm={addProducts}
          catalog={catalog}
          existingGroupKeys={items.map((item) => item.groupKey)}
        />
      )}
      {ConfirmDialog}
    </main>
  );
}
