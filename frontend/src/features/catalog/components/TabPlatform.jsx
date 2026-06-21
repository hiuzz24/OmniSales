import React, { useState } from 'react';
import { LinkIcon, ChevronDown, ChevronRight } from 'lucide-react';
import styles from './TabPlatform.module.css';

const TabPlatform = ({ product, channels = [] }) => {
  const [expandedPlatform, setExpandedPlatform] = useState(null);

  const productChannels = channels.filter(c => (product?.channels || []).includes(c.platform));

  const toggleExpand = (platformName) => {
    setExpandedPlatform(prev => prev === platformName ? null : platformName);
  };

  const calculateTotalStock = () => {
    if (!product?.variants) return 0;
    return product.variants.reduce((sum, v) => sum + (v.quantityOnHand || 0), 0);
  };

  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <div className={styles.titleRow}>
          <LinkIcon className={styles.titleIcon} />
          <h3 className={styles.cardTitle}>Platform Mapping</h3>
        </div>
        <p className={styles.cardSubtitle}>Thông tin kết nối và đồng bộ với các nền tảng bán hàng</p>
      </div>

      <div className={styles.tableWrapper}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th style={{ width: '40px' }}></th>
              <th>Nền tảng</th>
              <th>Trạng thái</th>
              <th>Tồn kho (Dự kiến)</th>
              <th>Lần đồng bộ cuối</th>
            </tr>
          </thead>
          <tbody>
            {productChannels.length === 0 ? (
              <tr>
                <td colSpan="5" style={{ textAlign: 'center', padding: '24px', color: '#6b7280' }}>
                  Sản phẩm chưa được liên kết với kênh bán hàng nào.
                </td>
              </tr>
            ) : (
              productChannels.map(channel => {
                const isExpanded = expandedPlatform === channel.platform;
                const commissionRate = channel.commissionRate || 0;

                const syncInfo = (product?.channelSyncs || []).find(s => s.platform === channel.platform) || {};
                const syncStatus = syncInfo.syncStatus || 'PENDING';
                const lastSyncedAt = syncInfo.lastSyncedAt ? new Date(syncInfo.lastSyncedAt).toLocaleString('vi-VN') : 'Chưa đồng bộ';

                return (
                  <React.Fragment key={channel.id}>
                    <tr onClick={() => toggleExpand(channel.platform)} style={{ cursor: 'pointer', backgroundColor: isExpanded ? '#f9fafb' : 'white' }}>
                      <td style={{ textAlign: 'center' }}>
                        {isExpanded ? <ChevronDown size={18} color="#6b7280" /> : <ChevronRight size={18} color="#6b7280" />}
                      </td>
                      <td className={styles.fw500}>{channel.displayName || channel.platform}</td>
                      <td>
                        {syncStatus === 'SUCCESS' && <span className={styles.badgeSuccess}>Thành công</span>}
                        {syncStatus === 'PENDING' && <span className={styles.badgeWarning} style={{ backgroundColor: '#fef08a', color: '#854d0e', padding: '4px 8px', borderRadius: '4px', fontSize: '12px', fontWeight: 600 }}>Chờ đồng bộ</span>}
                        {syncStatus === 'FAILED' && <span className={styles.badgeDanger} style={{ backgroundColor: '#fee2e2', color: '#b91c1c', padding: '4px 8px', borderRadius: '4px', fontSize: '12px', fontWeight: 600 }}>Lỗi</span>}
                        {!['SUCCESS', 'PENDING', 'FAILED'].includes(syncStatus) && <span>{syncStatus}</span>}
                      </td>
                      <td>{calculateTotalStock()}</td>
                      <td className={styles.textGray}>{lastSyncedAt}</td>
                    </tr>
                    {isExpanded && (
                      <tr>
                        <td colSpan="5" style={{ padding: '0' }}>
                          <div style={{ backgroundColor: '#f9fafb', padding: '16px 24px', borderBottom: '1px solid #e5e7eb' }}>
                            <h4 style={{ fontSize: '13px', fontWeight: 600, color: '#374151', marginBottom: '12px' }}>
                              Chi tiết giá bán từng biến thể
                            </h4>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
                              <thead>
                                <tr style={{ borderBottom: '1px solid #d1d5db', textAlign: 'left', color: '#4b5563' }}>
                                  <th style={{ padding: '8px' }}>SKU Biến thể</th>
                                  <th style={{ padding: '8px' }}>Thuộc tính</th>
                                  <th style={{ padding: '8px' }}>Giá gốc</th>
                                  <th style={{ padding: '8px' }}>Giá bán đề xuất ({commissionRate}%)</th>
                                </tr>
                              </thead>
                              <tbody>
                                {(product.variants || []).map(v => {
                                  const rate = commissionRate / 100;
                                  const numPrice = Number(v.price);
                                  const suggestedPrice = (rate >= 1 || !numPrice) ? 'N/A' : Math.round(numPrice / (1 - rate)).toLocaleString('vi-VN') + 'đ';

                                  return (
                                    <tr key={v.id || v.sku} style={{ borderBottom: '1px solid #e5e7eb' }}>
                                      <td style={{ padding: '10px 8px', fontWeight: 500 }}>{v.sku}</td>
                                      <td style={{ padding: '10px 8px' }}>{[v.optionValues?.Size, v.optionValues?.['Màu']].filter(Boolean).join(' / ') || 'Mặc định'}</td>
                                      <td style={{ padding: '10px 8px' }}>{numPrice ? numPrice.toLocaleString('vi-VN') + 'đ' : '0đ'}</td>
                                      <td style={{ padding: '10px 8px', color: '#059669', fontWeight: 600 }}>{suggestedPrice}</td>
                                    </tr>
                                  );
                                })}
                              </tbody>
                            </table>
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default TabPlatform;
