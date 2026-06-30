import { z } from 'zod';

export const productVariantSchema = z.object({
  sku: z
    .string()
    .min(1, 'SKU không được để trống')
    .max(100, 'SKU tối đa 100 ký tự'),
  name: z.string().optional(),
  price: z
    .union([z.string(), z.number()])
    .transform((val) => (val === '' ? undefined : Number(val)))
    .pipe(
      z.number({ error: 'Giá phải là số' })
        .nonnegative('Giá phải >= 0')
    ),
  costPrice: z
    .union([z.string(), z.number()])
    .transform((val) => (val === '' || val === undefined || val === null ? null : Number(val)))
    .pipe(z.number().nonnegative('Giá vốn phải >= 0').nullable())
    .optional(),
  optionValues: z.record(z.string(), z.unknown()).optional(),
  images: z.array(z.any()).optional(),
});

const baseProductSchema = z.object({
  name: z
    .string()
    .min(1, 'Tên sản phẩm không được để trống')
    .max(500, 'Tên sản phẩm tối đa 500 ký tự'),
  sku: z
    .string()
    .min(1, 'SKU không được để trống')
    .max(100, 'SKU tối đa 100 ký tự'),
  barcode: z.string().optional(),
  description: z.string().optional(),
  categoryId: z
    .string()
    .min(1, 'Vui lòng chọn danh mục'),
  brand: z.string().optional(),
  weightGrams: z
    .union([z.string(), z.number()])
    .transform((val) => (val === '' || val === undefined || val === null ? null : Number(val)))
    .pipe(z.number().nonnegative('Khối lượng phải >= 0').nullable())
    .optional(),
  lowStockThreshold: z
    .union([z.string(), z.number()])
    .transform((val) => (val === '' || val === undefined || val === null ? null : Number(val)))
    .pipe(z.number().int('Ngưỡng tồn thấp phải là số nguyên').nonnegative('Ngưỡng tồn thấp phải >= 0').nullable())
    .optional(),
  dimensions: z.string().optional(),
});

export const productWithoutVariantsSchema = baseProductSchema.extend({
  hasVariants: z.literal(false),
  price: z
    .union([z.string(), z.number()])
    .transform((val) => (val === '' ? undefined : Number(val)))
    .pipe(
      z.number({ error: 'Giá bán phải là số' })
        .nonnegative('Giá bán phải >= 0')
    ),
  costPrice: z
    .union([z.string(), z.number()])
    .transform((val) => (val === '' || val === undefined || val === null ? null : Number(val)))
    .pipe(z.number().nonnegative('Giá vốn phải >= 0').nullable())
    .optional(),
});

export const productWithVariantsSchema = baseProductSchema.extend({
  hasVariants: z.literal(true),
  variants: z
    .array(productVariantSchema)
    .min(1, 'Vui lòng thêm ít nhất 1 biến thể')
}).superRefine((data, ctx) => {
  const skus = data.variants.map(v => v.sku);
  skus.forEach((sku, idx) => {
    if (sku && skus.indexOf(sku) !== idx) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'SKU này bị trùng lặp với biến thể khác',
        path: ['variants', idx, 'sku'],
      });
    }
  });
});

export const createProductSchema = z.discriminatedUnion('hasVariants', [
  productWithoutVariantsSchema,
  productWithVariantsSchema,
]);

export function validateProductForm(formValues) {
  const result = createProductSchema.safeParse(formValues);

  if (result.success) {
    return { success: true, data: result.data };
  }

  const fieldErrors = {};
  const variantErrors = {};

  for (const issue of result.error.issues) {
    const path = issue.path;

    if (path[0] === 'variants' && typeof path[1] === 'number') {
      const idx = path[1];
      const field = path[2];
      if (!variantErrors[idx]) variantErrors[idx] = {};
      variantErrors[idx][field] = issue.message;
    } else {
      const key = path.join('.');
      fieldErrors[key || '_root'] = issue.message;
    }
  }

  return {
    success: false,
    fieldErrors,
    variantErrors,
    formErrors: result.error.issues.map((i) => i.message),
  };
}
