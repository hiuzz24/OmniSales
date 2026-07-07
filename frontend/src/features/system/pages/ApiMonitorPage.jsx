import { useState, useEffect } from 'react';
import { 
  Activity, CheckCircle, Clock, AlertTriangle, Search, RefreshCw, ShieldAlert
} from 'lucide-react';
import { 
  AreaChart, Area, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend
} from 'recharts';
import monitorApi from '../../../api/monitorApi';
import styles from './ApiMonitorPage.module.css';

const ApiMonitorPage = () => {
  const [range, setRange] = useState('today'); // 'today' | '24h' | '7d'
  const [summary, setSummary] = useState({
    totalRequestsToday: 0,
    successRate: 100,
    avgLatencyMs: 0,
    activeAlertsCount: 0
  });
  const [traffic, setTraffic] = useState([]);
  const [endpoints, setEndpoints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const fetchData = async (isSilent = false) => {
    if (!isSilent) setLoading(true);
    else setRefreshing(true);

    try {
      const [summaryRes, trafficRes, endpointsRes] = await Promise.all([
        monitorApi.getSummary(),
        monitorApi.getTraffic(range),
        monitorApi.getEndpoints()
      ]);

      setSummary(summaryRes || {
        totalRequestsToday: 0,
        successRate: 100,
        avgLatencyMs: 0,
        activeAlertsCount: 0
      });
      setTraffic(trafficRes || []);
      setEndpoints(endpointsRes || []);
    } catch (error) {
      console.error('Error fetching API monitor data:', error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [range]);

  const handleRefresh = () => {
    fetchData(true);
  };

  // Filter endpoints by search query
  const filteredEndpoints = endpoints.filter(item => 
    item.endpoint.toLowerCase().includes(searchQuery.toLowerCase()) ||
    item.method.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const getMethodClass = (method) => {
    switch (method.toUpperCase()) {
      case 'GET': return styles.methodGet;
      case 'POST': return styles.methodPost;
      case 'PUT': return styles.methodPut;
      case 'DELETE': return styles.methodDelete;
      default: return styles.methodDefault;
    }
  };

  const getQuotaColorClass = (remaining, total) => {
    if (total <= 0) return styles.progressOk;
    const usagePercent = ((total - remaining) / total) * 100;
    if (usagePercent >= 90) return styles.progressDanger;
    if (usagePercent >= 75) return styles.progressWarn;
    return styles.progressOk;
  };

  const getSuccessRateClass = (rate) => {
    if (rate >= 98) return styles.successOk;
    if (rate >= 90) return styles.successWarn;
    return styles.successDanger;
  };

  return (
    <div className={styles.container}>
      {/* Header */}
      <div className={styles.header}>
        <div className={styles.titleArea}>
          <h1>
            <Activity className="text-blue-600" size={28} />
            Giám sát lưu lượng API
          </h1>
          <p>Báo cáo lưu lượng truy cập API, tỉ lệ lỗi, độ trễ và hạn ngạch (Quota) hệ thống</p>
        </div>

        <div className="flex items-center gap-4">
          {/* Time range picker */}
          <div className={styles.tabs}>
            <button 
              className={`${styles.tabBtn} ${range === 'today' ? styles.tabActive : ''}`}
              onClick={() => setRange('today')}
            >
              Hôm nay
            </button>
            <button 
              className={`${styles.tabBtn} ${range === '24h' ? styles.tabActive : ''}`}
              onClick={() => setRange('24h')}
            >
              24 giờ qua
            </button>
            <button 
              className={`${styles.tabBtn} ${range === '7d' ? styles.tabActive : ''}`}
              onClick={() => setRange('7d')}
            >
              7 ngày qua
            </button>
          </div>

          {/* Refresh Button */}
          <button 
            className="flex items-center justify-center p-2 rounded-lg border border-gray-300 bg-white hover:bg-gray-50 transition cursor-pointer text-gray-600"
            onClick={handleRefresh}
            disabled={loading || refreshing}
            title="Làm mới dữ liệu"
          >
            <RefreshCw size={18} className={refreshing ? 'animate-spin' : ''} />
          </button>
        </div>
      </div>

      {loading ? (
        <div className="flex flex-col items-center justify-center h-64 text-gray-500">
          <RefreshCw size={40} className="animate-spin mb-4 text-blue-600" />
          <span>Đang tải thông tin giám sát API...</span>
        </div>
      ) : (
        <>
          {/* Metrics KPI Cards */}
          <div className={styles.metricsGrid}>
            {/* Requests Card */}
            <div className={styles.card}>
              <div className={`${styles.iconWrapper} ${styles.requestsIcon}`}>
                <Activity size={24} />
              </div>
              <div className={styles.cardInfo}>
                <span className={styles.cardLabel}>Tổng số Requests</span>
                <span className={styles.cardValue}>{summary.totalRequestsToday?.toLocaleString() ?? 0}</span>
              </div>
            </div>

            {/* Success Rate Card */}
            <div className={styles.card}>
              <div className={`${styles.iconWrapper} ${styles.successIcon}`}>
                <CheckCircle size={24} />
              </div>
              <div className={styles.cardInfo}>
                <span className={styles.cardLabel}>Tỉ lệ thành công</span>
                <span className={`${styles.cardValue} ${getSuccessRateClass(summary.successRate)}`}>
                  {summary.successRate ? summary.successRate.toFixed(2) : '100.00'}%
                </span>
              </div>
            </div>

            {/* Avg Latency Card */}
            <div className={styles.card}>
              <div className={`${styles.iconWrapper} ${styles.latencyIcon}`}>
                <Clock size={24} />
              </div>
              <div className={styles.cardInfo}>
                <span className={styles.cardLabel}>Độ trễ trung bình</span>
                <span className={styles.cardValue}>
                  {summary.avgLatencyMs ? summary.avgLatencyMs.toFixed(0) : '0'} ms
                </span>
              </div>
            </div>

            {/* Alerts Card */}
            <div className={styles.card}>
              <div className={`${styles.iconWrapper} ${summary.activeAlertsCount > 0 ? styles.alertsIcon : styles.alertsNormalIcon}`}>
                {summary.activeAlertsCount > 0 ? <ShieldAlert size={24} /> : <CheckCircle size={24} />}
              </div>
              <div className={styles.cardInfo}>
                <span className={styles.cardLabel}>Hạn ngạch cạn kiệt</span>
                <div>
                  <span className={`${styles.alertBadge} ${summary.activeAlertsCount > 0 ? styles.alertActive : styles.alertHealthy}`}>
                    {summary.activeAlertsCount > 0 ? `${summary.activeAlertsCount} Endpoint` : 'An toàn'}
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Traffic Area Charts */}
          <div className={styles.chartsSection}>
            <div className={styles.chartHeader}>
              <h3 className={styles.chartTitle}>Lưu lượng truy cập theo thời gian</h3>
              <span className="text-xs text-gray-500 font-medium">Đơn vị: Số lượng request</span>
            </div>
            
            <div className={styles.chartContainer}>
              {traffic.length === 0 ? (
                <div className="flex items-center justify-center h-full text-gray-400">Không có dữ liệu lưu lượng truy cập</div>
              ) : (
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={traffic} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                    <defs>
                      <linearGradient id="colorRequests" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.8}/>
                        <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                      </linearGradient>
                      <linearGradient id="colorFails" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#ef4444" stopOpacity={0.8}/>
                        <stop offset="95%" stopColor="#ef4444" stopOpacity={0}/>
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                    <XAxis dataKey="timeLabel" stroke="#94a3b8" fontSize={11} tickLine={false} />
                    <YAxis stroke="#94a3b8" fontSize={11} tickLine={false} axisLine={false} />
                    <Tooltip 
                      contentStyle={{ backgroundColor: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '8px', fontSize: '12px' }}
                      labelClassName="font-bold text-gray-700"
                    />
                    <Legend iconType="circle" wrapperStyle={{ fontSize: '12px', paddingTop: '10px' }} />
                    <Area type="monotone" name="Thành công" dataKey="successCount" stroke="#3b82f6" fillOpacity={1} fill="url(#colorRequests)" strokeWidth={2} />
                    <Area type="monotone" name="Lỗi" dataKey="failCount" stroke="#ef4444" fillOpacity={1} fill="url(#colorFails)" strokeWidth={2} />
                  </AreaChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>

          {/* Endpoints Detail Table */}
          <div className={styles.tableSection}>
            <div className={styles.tableHeader}>
              <h3 className={styles.tableTitle}>Chi tiết theo Endpoint</h3>
              
              {/* Search */}
              <div className={styles.searchWrapper}>
                <Search className={styles.searchIcon} size={20} />
                <input 
                  type="text" 
                  className={styles.searchInput}
                  placeholder="Tìm kiếm Endpoint hoặc Method..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
              </div>
            </div>

            <div className={styles.tableResponsive}>
              {filteredEndpoints.length === 0 ? (
                <div className={styles.emptyState}>
                  <AlertTriangle className={styles.emptyStateIcon} />
                  <p>Không tìm thấy endpoint nào phù hợp</p>
                </div>
              ) : (
                <table className={styles.endpointTable}>
                  <thead>
                    <tr>
                      <th>Method</th>
                      <th>API Endpoint Pattern</th>
                      <th>Tổng Requests</th>
                      <th>Tỉ lệ thành công</th>
                      <th>Độ trễ TB</th>
                      <th>Rate Limit</th>
                      <th>Hạn ngạch ngày (Quota)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredEndpoints.map((item, index) => {
                      const quotaUsed = item.dailyQuota - item.remainingQuota;
                      const quotaPercent = item.dailyQuota > 0 ? (quotaUsed / item.dailyQuota) * 100 : 0;
                      const successPercent = item.requestCount > 0 ? (item.successCount / item.requestCount) * 100 : 100;

                      return (
                        <tr key={index}>
                          <td>
                            <span className={`${styles.methodBadge} ${getMethodClass(item.method)}`}>
                              {item.method}
                            </span>
                          </td>
                          <td>
                            <span className={styles.endpointText}>{item.endpoint}</span>
                          </td>
                          <td className="font-semibold text-gray-800">
                            {item.requestCount.toLocaleString()}
                          </td>
                          <td>
                            <span className={getSuccessRateClass(successPercent)}>
                              {successPercent.toFixed(1)}%
                            </span>
                          </td>
                          <td>
                            <span className="text-gray-600 font-medium">
                              {item.avgLatencyMs.toFixed(0)} ms
                            </span>
                          </td>
                          <td>
                            <span className="text-xs font-semibold px-2 py-1 bg-gray-100 rounded text-gray-700">
                              {item.rateLimitPerMin} req/min
                            </span>
                          </td>
                          <td>
                            <div className={styles.quotaWrapper}>
                              <div className={styles.progressContainer}>
                                <div 
                                  className={`${styles.progressBar} ${getQuotaColorClass(item.remainingQuota, item.dailyQuota)}`}
                                  style={{ width: `${Math.min(100, quotaPercent)}%` }}
                                ></div>
                              </div>
                              <div className={styles.quotaLabel}>
                                <span>Đã dùng: {quotaUsed.toLocaleString()}</span>
                                <span>/ {item.dailyQuota.toLocaleString()}</span>
                              </div>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
};

export default ApiMonitorPage;
