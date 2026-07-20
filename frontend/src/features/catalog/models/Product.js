import { z } from 'zod';

const numberOrNull = z.union([z.string(), z.number(), z.null(), z.undefined()])
  .transform((value) => value === '' || value == null ? null : Number(value))
  .refine((value) => value == null || (!Number.isNaN(value) && value >= 0), 'Giá trị phải là số không âm');

const imageUrlSchema = z.string()
  .trim()
  .url('URL ảnh không hợp lệ')
  .refine((value) => /^https?:\/\//i.test(value), 'URL ảnh phải bắt đầu bằng http:// hoặc https://');

const variantSchema = z.object({
  id: z.string().nullable().optional(),
  sku: z.string().min(1, 'SKU không được để trống').max(100, 'SKU tối đa 100 ký tự'),
  barcode: z.string().optional(),
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
  name: z.string().min(1, 'Tên sản phẩm không được để trống').max(500, 'Tên sản phẩm tối đa 500 ký tự'),
  sku: z.string().min(1, 'SKU không được để trống').max(100, 'SKU tối đa 100 ký tự'),
  barcode: z.string().optional(),
  size: z.string().optional(),
  color: z.string().optional(),
  description: z.string().optional(),
  categoryId: z.string().min(1, 'Vui lòng chọn danh mục'),
  brand: z.string().optional(),
  unit: z.string().optional(),
  status: z.enum(['ACTIVE', 'DRAFT']),
  hasVariants: z.boolean(),
  price: numberOrNull,
  costPrice: numberOrNull,
  packageWeightKg: numberOrNull,
  packageWidthCm: numberOrNull,
  packageHeightCm: numberOrNull,
  packageLengthCm: numberOrNull,
  lowStockThreshold: numberOrNull,
  images: z.array(z.object({ id: z.string().nullable().optional(), url: imageUrlSchema, sortOrder: z.number().optional(), isPrimary: z.boolean().optional() }))
    .min(1, 'Vui lòng thêm ít nhất 1 ảnh sản phẩm'),
  variants: z.array(variantSchema),
  channelIds: z.array(z.string()),
  channelConfigs: z.record(z.string(), z.unknown()),
}).superRefine((data, ctx) => {
  if (!data.hasVariants) {
    if (data.price == null || data.price <= 0) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['price'], message: 'Giá bán phải lớn hơn 0' });
    }
    return;
  }
  if (!data.variants.length) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['variants'], message: 'Vui lòng thêm ít nhất 1 biến thể' });
  const seen = new Set();
  data.variants.forEach((variant, index) => {
    if (seen.has(variant.sku)) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['variants', index, 'sku'], message: 'SKU này bị trùng lặp' });
    if (variant.isActive !== false && (variant.price == null || variant.price <= 0)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['variants', index, 'price'], message: 'Giá bán phải lớn hơn 0' });
    }
    seen.add(variant.sku);
  });
});

export const defaultProductFormValues = {
  name: '', sku: '', barcode: '', size: '', color: '', description: '', categoryId: '', brand: '', unit: '',
  status: 'ACTIVE', hasVariants: false, price: '0', costPrice: '0', packageWeightKg: '', packageWidthCm: '',
  packageHeightCm: '', packageLengthCm: '', lowStockThreshold: '5', images: [], variants: [], channelIds: [], channelConfigs: {},
};

const optionValues = (values) => Object.fromEntries(Object.entries(values).filter(([, value]) => value));

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
    ...(isCreate ? {} : { version: values.version }), name: values.name, sku: values.sku, description: values.description || null,
    categoryId: values.categoryId || null, brand: values.brand || null, unit: values.unit || null, status: values.status,
    weightGrams: values.packageWeightKg == null ? null : Math.round(Number(values.packageWeightKg) * 1000),
    lowStockThreshold: values.lowStockThreshold == null ? 5 : Number(values.lowStockThreshold),
    attributes: { ...existingAttributes, packageWidthCm: values.packageWidthCm ?? null, packageHeightCm: values.packageHeightCm ?? null, packageLengthCm: values.packageLengthCm ?? null },
    channelIds: values.channelIds, channelConfigs: Object.values(values.channelConfigs).filter((config) => values.channelIds.includes(config.channelId)), variants, images,
  };
}
