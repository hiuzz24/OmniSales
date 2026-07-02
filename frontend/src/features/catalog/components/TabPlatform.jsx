import React, { useState } from 'react';
import { ChevronDown, ChevronRight, LinkIcon } from 'lucide-react';
import styles from './TabPlatform.module.css';

const statusLabel = {
  SYNCED: 'Thành công',
  SUCCESS: 'Thành công',
  PENDING: 'Chờ đồng bộ',
  FAILED: 'Lỗi',
  OUT_OF_SYNC: 'Cần đồng bộ',
};

const TabPlatform = ({ product, channels = [] }) => {
  const [expandedPlatform, setExpandedPlatform] = useState(null);

  const productChannels = channels.filter(channel => {
    const channelId = channel.id || channel._id;
    if (product?.channelIds && product.channelIds.length > 0) {
      return product.channelIds.includes(channelId);
    }
    return (product?.channels || []).includes(channel.platform);
  });

  const toggleExpand = (key) => {
    setExpandedPlatform(prev => prev === key ? null : key);
  };

  const calculateTotalStock = () => {
    if (!product?.variants) return 0;
    return product.variants.reduce((sum, variant) => sum + (variant.quantityOnHand || 0), 0);
  };

  const renderStatusBadge = (syncStatus) => {
    if (syncStatus === 'SYNCED' || syncStatus === 'SUCCESS') {
      return <span className={styles.badgeSuccess}>{statusLabel[syncStatus]}</span>;
    }
    if (syncStatus === 'FAILED') {
      return <span className={styles.badgeDanger}>{statusLabel[syncStatus]}</span>;
    }
    return <span className={styles.badgeWarning}>{statusLabel[syncStatus] || syncStatus}</span>;
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
              <th>Tồn kho dự kiến</th>
              <th>Lần đồng bộ cuối</th>
            </tr>
          </thead>
          <tbody>
            {productChannels.length === 0 ? (
              <tr>
                <td colSpan="5" className={styles.emptyCell}>
                  Sản phẩm chưa được liên kết với kênh bán hàng nào.
                </td>
              </tr>
            ) : (
              productChannels.map(channel => {
                const channelId = channel.id || channel._id;
                const rowKey = `${channel.platform}-${channelId}`;
                const isExpanded = expandedPlatform === rowKey;
                const commissionRate = channel.commissionRate || 0;
                const syncInfo = (product?.channelSyncs || []).find(sync =>
                  sync.channelId === channelId || sync.platform === channel.platform
                ) || {};
                const syncStatus = syncInfo.syncStatus || 'PENDING';
                const lastSyncedAt = syncInfo.lastSyncedAt
                  ? new Date(syncInfo.lastSyncedAt).toLocaleString('vi-VN')
                  : 'Chưa đồng bộ';

                return (
                  <React.Fragment key={rowKey}>
                    <tr
                      onClick={() => toggleExpand(rowKey)}
                      className={isExpanded ? styles.expandedRow : styles.clickableRow}
                    >
                      <td className={styles.centerCell}>
                        {isExpanded ? <ChevronDown size={18} /> : <ChevronRight size={18} />}
                      </td>
                      <td className={styles.fw500}>{channel.displayName || channel.platform}</td>
                      <td>{renderStatusBadge(syncStatus)}</td>
                      <td>{calculateTotalStock()}</td>
                      <td className={styles.textGray}>{lastSyncedAt}</td>
                    </tr>
                    {isExpanded && (
                      <tr>
                        <td colSpan="5" className={styles.expandedCell}>
                          <div className={styles.expandedPanel}>
                            <h4 className={styles.sectionTitle}>Chi tiết giá bán từng biến thể</h4>
                            <table className={styles.variantTable}>
                              <thead>
                                <tr>
                                  <th>SKU biến thể</th>
                                  <th>Thuộc tính</th>
                                  <th>Giá gốc</th>
                                  <th>Giá bán đề xuất ({commissionRate}%)</th>
                                </tr>
                              </thead>
                              <tbody>
                                {(product.variants || []).map(variant => {
                                  const rate = commissionRate / 100;
                                  const price = Number(variant.price);
                                  const suggestedPrice = rate >= 1 || !price
                                    ? 'N/A'
                                    : `${Math.round(price / (1 - rate)).toLocaleString('vi-VN')}đ`;

                                  return (
                                    <tr key={variant.id || variant.sku}>
                                      <td>{variant.sku}</td>
                                      <td>{Object.values(variant.optionValues || {}).filter(Boolean).join(' / ') || 'Mặc định'}</td>
                                      <td>{price ? `${price.toLocaleString('vi-VN')}đ` : '0đ'}</td>
                                      <td className={styles.suggestedPrice}>{suggestedPrice}</td>
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
