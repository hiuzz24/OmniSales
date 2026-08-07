const normalized = (value) => String(value ?? '').trim().toLowerCase();

const sumNullable = (items, field) => {
  const values = items
    .map((item) => item[field])
    .filter((value) => value != null && Number.isFinite(Number(value)));
  return values.length === 0
    ? null
    : values.reduce((total, value) => total + Number(value), 0);
};

export const groupStockDeliveryOrderItems = (items = []) => {
  const groups = new Map();

  items.forEach((item) => {
    const key = item.variantId
      || (item.sku ? `sku:${normalized(item.sku)}` : null)
      || `product:${normalized(item.name)}|price:${item.unitPrice ?? ''}`;
    const current = groups.get(key) ?? [];
    current.push(item);
    groups.set(key, current);
  });

  return Array.from(groups.entries()).map(([key, sourceItems]) => ({
    ...sourceItems[0],
    id: key,
    sourceItems,
    quantity: sourceItems.reduce((total, item) => total + Number(item.quantity ?? 0), 0),
    discountAmount: sumNullable(sourceItems, 'discountAmount'),
    totalPrice: sumNullable(sourceItems, 'totalPrice'),
  }));
};
