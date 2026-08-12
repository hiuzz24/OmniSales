const hasActualQuantity = (item) => item.actualQuantity !== '' && item.actualQuantity !== null && item.actualQuantity !== undefined;
const getItemDiff = (item) => Number(item.actualQuantity || 0) - Number(item.systemQuantity || 0);

export const getDiffSummary = (items) => {
  const checkedItems = items.filter((item) => hasActualQuantity(item));
  const surplusCount = checkedItems.filter((item) => getItemDiff(item) > 0).length;
  const shortageCount = checkedItems.filter((item) => getItemDiff(item) < 0).length;
  const hasDiff = surplusCount > 0 || shortageCount > 0;
  const label = surplusCount > 0 && shortageCount > 0
    ? 'thừa và thiếu'
    : surplusCount > 0
      ? 'thừa'
      : shortageCount > 0
        ? 'thiếu'
        : '';
  return { surplusCount, shortageCount, hasDiff, label };
};

export const confirmStocktakeComplete = ({ confirm, summary }) => {
  if (summary.hasDiff) {
    return confirm({
      title: 'Có sản phẩm chênh lệch tồn kho',
      message: `Có sản phẩm ${summary.label} so với tồn kho hệ thống.\nBạn có chắc chắn muốn thay đổi tồn kho không?`,
      confirmLabel: 'Chắc chắn thay đổi',
      tone: 'warning',
    });
  }
  return confirm({
    title: 'Hoàn thành kiểm kho',
    message: 'Bạn đã chắc chắn tồn kho đã đủ chưa?',
    confirmLabel: 'Hoàn thành',
  });
};

export const confirmSyncMarketplaceNow = ({ confirm }) => confirm({
  title: 'Đồng bộ lên sàn ngay!!!',
  message: 'Tồn kho đã được điều chỉnh theo kết quả kiểm kho.\nĐồng bộ tồn kho mới lên các sàn đang bán ngay bây giờ?',
  confirmLabel: 'Đồng bộ ngay',
  cancelLabel: 'Để sau',
});
