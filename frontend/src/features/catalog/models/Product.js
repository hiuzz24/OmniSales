import { z } from 'zod';

/** Chuẩn hóa input số rỗng thành null và chặn số âm. */
const numberOrNull = z.union([z.string(), z.number(), z.null(), z.undefined()])
  .transform((value) => value === '' || value == null ? null : Number(value))
  .refine((value) => value == null || (!Number.isNaN(value) && value >= 0), 'Giá trị phải là số không âm');

/** Tạo schema chuỗi bắt buộc với độ dài tối đa. */
const requiredText = (message, max = 500) => z.string()
  .trim()
  .min(1, message)
  .max(max, `Tối đa ${max} ký tự`);

/** Tạo schema số bắt buộc và tùy chọn điều kiện lớn hơn 0. */
const requiredNumber = (message, { positive = false } = {}) => numberOrNull.refine(
  (value) => value != null && (positive ? value > 0 : value >= 0),
  message,
);

const imageUrlSchema = z.string()
  .trim()
  .url('URL ảnh không hợp lệ')
  .refine((value) => /^https?:\/\//i.test(value), 'URL ảnh phải bắt đầu bằng http:// hoặc https://');

const optionalBarcode = z.preprocess(
  (value) => value == null ? '' : value,
  z.string(),
);

const variantSchema = z.object({
  id: z.string().nullable().optional(),
  sku: z.string().optional(),
  barcode: optionalBarcode,
  name: z.string().optional(),
  price: numberOrNull,
  costPrice: numberOrNull,
  isActive: z.boolean().optional(),
  optionValues: z.record(z.string(), z.unknown()).optional(),
  images: z.array(z.object({ id: z.string().nullable().optional(), url: imageUrlSchema, sortOrder: z.number().optional(), isPrimary: z.boolean().optional() })).optional(),
});

export const productEditorSchema = z.object({
  version: z.number().nullable().optional(),
  hasOrders: z.boolean().optional(),
  name: requiredText('Tên sản phẩm không được để trống', 500),
  sku: requiredText('SKU không được để trống', 100),
  barcode: optionalBarcode,
  size: z.string().optional(),
  color: z.string().optional(),
  description: requiredText('Mô tả sản phẩm không được để trống', 5000),
  categoryId: z.string().min(1, 'Vui lòng chọn danh mục'),
  brand: requiredText('Thương hiệu không được để trống', 255),
  unit: requiredText('Đơn vị tính không được để trống', 50),
  status: z.enum(['ACTIVE', 'DRAFT']),
  hasVariants: z.boolean(),
  price: numberOrNull,
  costPrice: requiredNumber('Giá vốn không được để trống'),
  packageWeightKg: requiredNumber('Khối lượng phải lớn hơn 0', { positive: true }),
  packageWidthCm: requiredNumber('Chiều rộng phải lớn hơn 0', { positive: true }),
  packageHeightCm: requiredNumber('Chiều cao phải lớn hơn 0', { positive: true }),
  packageLengthCm: requiredNumber('Chiều dài phải lớn hơn 0', { positive: true }),
  lowStockThreshold: requiredNumber('Ngưỡng tồn kho không được để trống'),
  images: z.array(z.object({ id: z.string().nullable().optional(), url: imageUrlSchema, sortOrder: z.number().optional(), isPrimary: z.boolean().optional() }))
    .min(1, 'Vui lòng thêm ít nhất 1 ảnh sản phẩm'),
  variants: z.array(variantSchema),
  channelIds: z.array(z.string()),
  channelConfigs: z.record(z.string(), z.unknown()),
}).superRefine((data, ctx) => {
  if (!data.hasVariants) {
    [
      ['size', data.size, 'Size không được để trống'],
      ['color', data.color, 'Màu sắc không được để trống'],
    ].forEach(([path, value, message]) => {
      if (!value?.trim()) ctx.addIssue({ code: z.ZodIssueCode.custom, path: [path], message });
    });
    if (data.price == null || data.price <= 0) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['price'], message: 'Giá bán phải lớn hơn 0' });
    }
    return;
  }
  if (!data.variants.length) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['variants'], message: 'Vui lòng thêm ít nhất 1 biến thể' });
  const seen = new Set();
  data.variants.forEach((variant, index) => {
    if (variant.isActive === false) return;
    if (!variant.sku?.trim()) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['variants', index, 'sku'], message: 'SKU không được để trống' });
    } else if (variant.sku.length > 100) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['variants', index, 'sku'], message: 'SKU tối đa 100 ký tự' });
    }
    if (!String(variant.optionValues?.Size || '').trim()) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['variants', index, 'optionValues', 'Size'], message: 'Size không được để trống' });
    }
    if (!String(variant.optionValues?.['Màu'] || '').trim()) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['variants', index, 'optionValues', 'Màu'], message: 'Màu sắc không được để trống' });
    }
    if (variant.price == null || variant.price <= 0) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['variants', index, 'price'], message: 'Giá bán phải lớn hơn 0' });
    }
    if (variant.costPrice == null) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['variants', index, 'costPrice'], message: 'Giá vốn không được để trống' });
    }
    if (!variant.images?.length) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['variants', index, 'images'], message: 'Vui lòng thêm ảnh cho biến thể' });
    }
    const normalizedSku = variant.sku?.trim();
    if (normalizedSku && seen.has(normalizedSku)) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['variants', index, 'sku'], message: 'SKU này bị trùng lặp' });
    if (normalizedSku) seen.add(normalizedSku);
  });
});

export const defaultProductFormValues = {
  name: '', sku: '', barcode: '', size: '', color: '', description: '', categoryId: '', brand: '', unit: '',
  status: 'ACTIVE', hasVariants: false, price: '0', costPrice: '0', packageWeightKg: '', packageWidthCm: '',
  packageHeightCm: '', packageLengthCm: '', lowStockThreshold: '5', images: [], variants: [], channelIds: [], channelConfigs: {},
};

/** Chuẩn hóa optionValues bằng cách loại các giá trị rỗng. */
const optionValues = (values) => Object.fromEntries(Object.entries(values).filter(([, value]) => value));

/** Tìm giá trị đầu tiên theo danh sách tên option tương đương. */
const firstOptionValue = (options, keys) => keys
  .map((key) => options?.[key])
  .find((value) => value != null && String(value).trim());

/** Chuẩn hóa tên option Size/Màu từ dữ liệu cũ hoặc dữ liệu platform. */
export function normalizeVariantForEditor(variant = {}) {
  const options = { ...(variant.optionValues || {}) };
  const size = firstOptionValue(options, ['Size', 'size', 'Kích thước', 'Option 2']);
  const color = firstOptionValue(options, ['Màu', 'Màu sắc', 'Color', 'Colour', 'color', 'Option 1']);

  if (size != null) options.Size = size;
  if (color != null) options['Màu'] = color;
  if (size != null) delete options['Option 2'];
  if (color != null) delete options['Option 1'];

  return {
    ...variant,
    barcode: variant.barcode ?? '',
    optionValues: options,
  };
}

/** Khởi tạo Variant đầu tiên từ dữ liệu Product khi bật chế độ biến thể. */
export function seedVariantFromProduct(values) {
  const currentVariants = values.variants || [];
  if (currentVariants.length > 1) return currentVariants;

  const current = normalizeVariantForEditor(currentVariants[0]);
  const currentOptions = current.optionValues || {};
  const currentImages = current.images || [];
  const productImages = values.images || [];

  return [{
    ...current,
    sku: current.sku || values.sku || '',
    barcode: current.barcode || values.barcode || '',
    name: current.name || values.name || '',
    price: current.price ?? values.price ?? '0',
    costPrice: current.costPrice ?? values.costPrice ?? '0',
    isActive: current.isActive !== false,
    optionValues: {
      ...currentOptions,
      Size: currentOptions.Size || values.size || '',
      'Màu': currentOptions['Màu'] || values.color || '',
    },
    images: currentImages.length > 0
      ? currentImages
      : productImages.slice(0, 1),
  }];
}

/** Chuyển dữ liệu form thành request đúng contract create/update Product. */
export function buildProductRequest(values, { mode, existingAttributes = {} }) {
  const isCreate = mode === 'create';
  const images = values.images.map((image, index) => ({ id: image.id || null, url: image.url, sortOrder: index, isPrimary: index === 0 }));
  const variants = values.hasVariants
    ? values.variants.map((variant) => ({
      id: variant.id || null, sku: variant.sku, barcode: variant.barcode || null,
      name: [variant.optionValues?.Size, variant.optionValues?.['Màu']].filter(Boolean).join(' / ') || variant.sku,
        price: Number(variant.price || 0), costPrice: isCreate ? 0 : (variant.costPrice ?? null),
      isActive: variant.isActive !== false, optionValues: variant.optionValues,
      images: (variant.images || []).map((image, index) => ({ id: image.id || null, url: image.url, isPrimary: false, sortOrder: index })),
    }))
    : [{
      id: values.variants[0]?.id || null, sku: values.sku, barcode: values.barcode || null,
        name: values.name || 'Mặc định', price: Number(values.price || 0),
      costPrice: isCreate ? 0 : (values.costPrice ?? null), optionValues: optionValues({ Size: values.size, Màu: values.color }), images: [],
    }];
  return {
    ...(isCreate ? {} : { version: values.version }), hasVariants: values.hasVariants, name: values.name, sku: values.sku, description: values.description || null,
    categoryId: values.categoryId || null, brand: values.brand || null, unit: values.unit || null, status: values.status,
    weightGrams: values.packageWeightKg == null ? null : Math.round(Number(values.packageWeightKg) * 1000),
    lowStockThreshold: values.lowStockThreshold == null ? 5 : Number(values.lowStockThreshold),
    attributes: { ...existingAttributes, packageWidthCm: values.packageWidthCm ?? null, packageHeightCm: values.packageHeightCm ?? null, packageLengthCm: values.packageLengthCm ?? null },
    channelIds: values.channelIds, channelConfigs: Object.values(values.channelConfigs).filter((config) => values.channelIds.includes(config.channelId)), variants, images,
  };
}
