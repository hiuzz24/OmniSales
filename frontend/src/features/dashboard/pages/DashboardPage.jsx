import { TrendingUp, ShoppingCart, RefreshCw, AlertTriangle } from 'lucide-react';

// ── Stacked Area Chart ────────────────────────────────────────────────────────
const AreaChart = () => {
  const W = 520, H = 150;
  const xs = [0, 74, 148, 222, 296, 370, 444, 520];
  const shopee = [850, 900, 960, 1010, 1060, 1110, 1160, 1220];
  const tiktok = [700, 760, 810, 860, 910, 960, 1010, 1060];
  const lazada = [600, 650, 710, 760, 810, 860, 910, 960];
  const maxVal = 3400;
  const y = (v) => H - (v / maxVal) * H;
  const s1 = shopee;
  const s2 = shopee.map((v, i) => v + tiktok[i]);
  const s3 = shopee.map((v, i) => v + tiktok[i] + lazada[i]);
  const zeros = new Array(8).fill(0);
  const area = (top, bot) => {
    const t = top.map((v, i) => `${xs[i]},${y(v)}`);
    const b = [...bot].reverse().map((v, i) => `${xs[bot.length - 1 - i]},${y(v)}`);
    return `M ${t[0]} L ${t.join(' L ')} L ${b.join(' L ')} Z`;
  };
  const pts = (arr) => arr.map((v, i) => `${xs[i]},${y(v)}`).join(' ');
  const dateLabels = ['01/05', '02/05', '03/05', '04/05', '05/05', '06/05', '07/05'];
  return (
    <div>
      <svg viewBox={`-38 -8 ${W + 46} ${H + 36}`} style={{ width: '100%', height: 185 }}>
        {[0, 1, 2, 3].map((t) => {
          const val = (t * maxVal) / 3;
          return (
            <g key={t}>
              <line x1={0} y1={y(val)} x2={W} y2={y(val)} stroke="#f1f5f9" strokeWidth="1" />
              <text x={-4} y={y(val)} textAnchor="end" fontSize="9" fill="#94a3b8" dominantBaseline="middle">
                {val === 0 ? '0' : (val / 1000000).toFixed(0) + 'M'}
              </text>
            </g>
          );
        })}
        <path d={area(s3, s2)} fill="#6366f1" opacity="0.7" />
        <path d={area(s2, s1)} fill="#64748b" opacity="0.75" />
        <path d={area(s1, zeros)} fill="#f87171" opacity="0.8" />
        <polyline points={pts(s3)} fill="none" stroke="#6366f1" strokeWidth="1.5" />
        <polyline points={pts(s2)} fill="none" stroke="#64748b" strokeWidth="1.5" />
        <polyline points={pts(s1)} fill="none" stroke="#f87171" strokeWidth="1.5" />
        {dateLabels.map((label, i) => (
          <text key={label} x={xs[i + 1] - 37} y={H + 14} fontSize="9" fill="#94a3b8" textAnchor="middle">{label}</text>
        ))}
      </svg>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 24, marginTop: 8 }}>
        {[{ label: 'Shopee', color: '#f87171' }, { label: 'TikTok Shop', color: '#64748b' }, { label: 'Lazada', color: '#6366f1' }].map((s) => (
          <span key={s.label} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: '#64748b' }}>
            <span style={{ width: 20, height: 3, borderRadius: 999, backgroundColor: s.color, display: 'inline-block' }} />
            {s.label}
          </span>
        ))}
      </div>
    </div>
  );
};

// ── Bar Chart ─────────────────────────────────────────────────────────────────
const BarChart = () => {
  const data = [{ label: 'Đang xử lý', value: 70 }, { label: 'Đang giao', value: 130 }, { label: 'Hoàn thành', value: 260 }];
  const W = 300, H = 150, BAR_W = 56, maxVal = 300;
  const gap = (W - data.length * BAR_W) / (data.length + 1);
  const y = (v) => H - (v / maxVal) * H;
  const yTicks = [0, 65, 130, 195, 260];
  return (
    <svg viewBox={`-32 -8 ${W + 42} ${H + 36}`} style={{ width: '100%', height: 185 }}>
      {yTicks.map((v) => (
        <g key={v}>
          <line x1={0} y1={y(v)} x2={W} y2={y(v)} stroke="#f1f5f9" strokeWidth="1" />
          <text x={-4} y={y(v)} textAnchor="end" fontSize="9" fill="#94a3b8" dominantBaseline="middle">{v}</text>
        </g>
      ))}
      {data.map((d, i) => {
        const x = gap + i * (BAR_W + gap);
        const bh = (d.value / maxVal) * H;
        return (
          <g key={d.label}>
            <rect x={x} y={H - bh} width={BAR_W} height={bh} fill="#3b82f6" rx="4" opacity="0.85" />
            <text x={x + BAR_W / 2} y={H + 14} textAnchor="middle" fontSize="9" fill="#94a3b8">{d.label}</text>
          </g>
        );
      })}
    </svg>
  );
};

// ── Card wrapper ──────────────────────────────────────────────────────────────
const Card = ({ children, style }) => (
  <div style={{ backgroundColor: '#fff', borderRadius: 12, border: '1px solid #e2e8f0', padding: 20, ...style }}>
    {children}
  </div>
);

// ── DashboardPage ─────────────────────────────────────────────────────────────
export default function DashboardPage() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* Header */}
      <div>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0f172a', margin: 0 }}>Dashboard</h1>
        <p style={{ fontSize: 14, color: '#64748b', marginTop: 4, marginBottom: 0 }}>Tổng quan hoạt động kinh doanh đa kênh</p>
      </div>

      {/* KPI Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16 }}>
        <Card>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
            <span style={{ fontSize: 14, color: '#64748b' }}>Tổng doanh thu</span>
            <TrendingUp size={16} color="#94a3b8" />
          </div>
          <div style={{ fontSize: 26, fontWeight: 700, color: '#0f172a', marginBottom: 8 }}>đ39.6M</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#059669', fontWeight: 500 }}>
            <TrendingUp size={12} /> +12.5% so với tháng trước
          </div>
        </Card>

        <Card>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
            <span style={{ fontSize: 14, color: '#64748b' }}>Đơn hàng</span>
            <ShoppingCart size={16} color="#94a3b8" />
          </div>
          <div style={{ fontSize: 26, fontWeight: 700, color: '#0f172a', marginBottom: 8 }}>502</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#059669', fontWeight: 500 }}>
            <TrendingUp size={12} /> +8.2% so với hôm trước
          </div>
        </Card>

        <Card>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
            <span style={{ fontSize: 14, color: '#64748b' }}>Sản phẩm</span>
            <RefreshCw size={16} color="#94a3b8" />
          </div>
          <div style={{ fontSize: 26, fontWeight: 700, color: '#0f172a', marginBottom: 8 }}>1,248</div>
          <div style={{ fontSize: 12, color: '#94a3b8', fontWeight: 500 }}>Trên 3 kênh bán hàng</div>
        </Card>

        <Card>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
            <span style={{ fontSize: 14, color: '#64748b' }}>Cảnh báo tồn kho</span>
            <AlertTriangle size={16} color="#f59e0b" />
          </div>
          <div style={{ fontSize: 26, fontWeight: 700, color: '#f59e0b', marginBottom: 8 }}>12</div>
          <div style={{ fontSize: 12, color: '#f59e0b', fontWeight: 500 }}>Sản phẩm sắp hết hàng</div>
        </Card>
      </div>

      {/* Charts */}
      <div style={{ display: 'grid', gridTemplateColumns: '3fr 2fr', gap: 16 }}>
        <Card>
          <h3 style={{ fontSize: 14, fontWeight: 600, color: '#0f172a', margin: '0 0 16px' }}>Doanh thu theo kênh</h3>
          <AreaChart />
        </Card>
        <Card>
          <h3 style={{ fontSize: 14, fontWeight: 600, color: '#0f172a', margin: '0 0 16px' }}>Trạng thái đơn hàng</h3>
          <BarChart />
        </Card>
      </div>

      {/* Bottom */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <Card>
          <h3 style={{ fontSize: 14, fontWeight: 600, color: '#0f172a', margin: '0 0 4px' }}>Sản phẩm bán chạy</h3>
          {[
            { name: 'Áo thun nam basic', sold: 254, rev: '11.700.000 đ', pct: '12%' },
            { name: 'Quần jean nữ',      sold: 189, rev: '18.900.000 đ', pct: '8%'  },
            { name: 'Váy hoa mùa hè',    sold: 143, rev: '9.500.000 đ',  pct: '5%'  },
            { name: 'Áo khoác bomber',   sold: 112, rev: '22.400.000 đ', pct: '3%'  },
          ].map((p) => (
            <div key={p.name} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '11px 0', borderBottom: '1px solid #f1f5f9' }}>
              <div>
                <div style={{ fontSize: 14, fontWeight: 500, color: '#0f172a' }}>{p.name}</div>
                <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>Đã bán: {p.sold} | Doanh thu: {p.rev}</div>
              </div>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, fontSize: 12, fontWeight: 600, color: '#059669', backgroundColor: '#f0fdf4', padding: '3px 10px', borderRadius: 999 }}>
                <TrendingUp size={10} /> {p.pct}
              </span>
            </div>
          ))}
        </Card>

        <Card>
          <h3 style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 14, fontWeight: 600, color: '#f59e0b', margin: '0 0 4px' }}>
            <AlertTriangle size={15} /> Cảnh báo tồn kho thấp
          </h3>
          {[
            { name: 'Áo thun nam basic - Size M',  channel: 'Kênh Shopee',      qty: 5, danger: false },
            { name: 'Quần jean nữ - Size 28',       channel: 'Kênh TikTok Shop', qty: 3, danger: true  },
            { name: 'Váy hoa mùa hè - Size S',      channel: 'Kênh Lazada',      qty: 7, danger: false },
            { name: 'Áo khoác bomber - Size L',     channel: 'Kênh Shopee',      qty: 2, danger: true  },
          ].map((s) => (
            <div key={s.name} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '11px 0', borderBottom: '1px solid #f1f5f9' }}>
              <div>
                <div style={{ fontSize: 14, fontWeight: 500, color: '#0f172a' }}>{s.name}</div>
                <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>{s.channel}</div>
              </div>
              <span style={{
                fontSize: 12, fontWeight: 700, padding: '3px 10px', borderRadius: 999,
                backgroundColor: s.danger ? '#fee2e2' : '#fff7ed',
                color: s.danger ? '#dc2626' : '#ea580c',
              }}>
                Còn {s.qty}
              </span>
            </div>
          ))}
        </Card>
      </div>
    </div>
  );
}
