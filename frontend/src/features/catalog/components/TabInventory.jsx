import styles from './TabInventory.module.css';

const TabInventory = ({ product }) => {
  // Aggregate real inventory data from variants
  const totalQuantity = product.variants?.reduce((sum, v) => sum + (v.quantityOnHand || 0), 0) || 0;
  const availableQuantity = product.variants?.reduce((sum, v) => sum + (v.availableQuantity || 0), 0) || 0;
  const reservedQuantity = totalQuantity - availableQuantity;

  // Mock data for history
  const mockHistory = [
    { id: 1, date: '30/5/2026', type: 'Xuất kho', quantity: -25, channel: 'Shopee', note: 'Xuất kho cho đơn #SP12345' },
    { id: 2, date: '29/5/2026', type: 'Nhập kho', quantity: '+100', channel: '-', note: 'Nhập kho từ nhà cung cấp' },
    { id: 3, date: '28/5/2026', type: 'Xuất kho', quantity: -18, channel: 'TikTok Shop', note: 'Xuất kho cho đơn #TT67890' },
  ];

  return (
    <div className={styles.tabContainer}>
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <h3 className={styles.cardTitle}>Thông tin tồn kho</h3>
          <p className={styles.cardSubtitle}>Chi tiết về số lượng và trạng thái kho hàng</p>
        </div>

        <div className={styles.statsGrid}>
          <div className={styles.statBox}>
            <div className={styles.statLabel}>Tổng số lượng trong kho</div>
            <div className={styles.statValueMain}>{totalQuantity}</div>
            <div className={styles.statSubBox}>
              <div className={styles.subLabelWarning}>Đã đặt trước (Reserved)</div>
              <div className={styles.subValueWarning}>{reservedQuantity}</div>
            </div>
          </div>
          <div className={styles.statBox}>
            <div className={styles.statLabel}>Kho</div>
            <div className={styles.statValueMain}>Kho Quận 1</div>
            <div className={styles.statSubBox}>
              <div className={styles.subLabelSuccess}>Có thể bán (Available)</div>
              <div className={styles.subValueSuccess}>{availableQuantity}</div>
            </div>
          </div>
        </div>

        <div className={styles.formula}>
          Công thức: Available = Warehouse Quantity - Reserved<br/>
          {availableQuantity} = {totalQuantity} - {reservedQuantity}
        </div>
      </div>

      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <h3 className={styles.cardTitle}>Lịch sử xuất nhập kho <span className={styles.mockNote}>(Mock)</span></h3>
          <p className={styles.cardSubtitle}>Các giao dịch kho hàng gần đây</p>
        </div>
        
        <div className={styles.tableWrapper}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Ngày</th>
                <th>Loại</th>
                <th>Số lượng</th>
                <th>Kênh</th>
                <th>Ghi chú</th>
              </tr>
            </thead>
            <tbody>
              {mockHistory.map((item) => (
                <tr key={item.id}>
                  <td>{item.date}</td>
                  <td>
                    <span className={item.type === 'Nhập kho' ? styles.badgeSuccess : styles.badgeBlue}>
                      {item.type}
                    </span>
                  </td>
                  <td className={item.quantity > 0 ? styles.textSuccess : styles.textBlue}>
                    {item.quantity}
                  </td>
                  <td>{item.channel}</td>
                  <td className={styles.textGray}>{item.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default TabInventory;
