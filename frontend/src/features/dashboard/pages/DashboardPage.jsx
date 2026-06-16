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

      <div className="flex items-center justify-center gap-6 mt-2">
        {[
          { label: 'Shopee', color: '#f87171' },
          { label: 'TikTok Shop', color: '#64748b' },
          { label: 'Lazada', color: '#6366f1' },
        ].map((s) => (
          <span key={s.label} className="flex items-center gap-1.5 text-xs text-slate-500">
            <span className="w-5 h-0.5 rounded-full inline-block" style={{ backgroundColor: s.color }} />
            {s.label}
          </span>
        ))}
      </div>
    </div>
  );
};

// ── Bar Chart ─────────────────────────────────────────────────────────────────
const BarChart = () => {
  const data = [
    { label: 'Đang xử lý', value: 70 },
    { label: 'Đang giao', value: 130 },
    { label: 'Hoàn thành', value: 260 },
  ];
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

// ── DashboardPage ─────────────────────────────────────────────────────────────
export default function DashboardPage() {
  return (
    <div className="space-y-6">
      {/* Page header */}
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Dashboard</h1>
        <p className="text-sm text-slate-500 mt-1">Tổng quan hoạt động kinh doanh đa kênh</p>
      </div>

      {/* KPI cards */}
      <div className="grid grid-cols-4 gap-4">
        {/* Tổng doanh thu */}
        <div className="bg-white rounded-xl border border-slate-200 p-5">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm text-slate-500">Tổng doanh thu</span>
            <TrendingUp className="w-4 h-4 text-slate-400" />
          </div>
          <div className="text-2xl font-bold text-slate-900 mb-2">đ39.6M</div>
          <div className="flex items-center gap-1 text-xs text-emerald-600 font-medium">
            <TrendingUp className="w-3 h-3" />
            +12.5% so với tháng trước
          </div>
        </div>

        {/* Đơn hàng */}
        <div className="bg-white rounded-xl border border-slate-200 p-5">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm text-slate-500">Đơn hàng</span>
            <ShoppingCart className="w-4 h-4 text-slate-400" />
          </div>
          <div className="text-2xl font-bold text-slate-900 mb-2">502</div>
          <div className="flex items-center gap-1 text-xs text-emerald-600 font-medium">
            <TrendingUp className="w-3 h-3" />
            +8.2% so với hôm trước
          </div>
        </div>

        {/* Sản phẩm */}
        <div className="bg-white rounded-xl border border-slate-200 p-5">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm text-slate-500">Sản phẩm</span>
            <RefreshCw className="w-4 h-4 text-slate-400" />
          </div>
          <div className="text-2xl font-bold text-slate-900 mb-2">1,248</div>
          <div className="text-xs text-slate-400 font-medium">Trên 3 kênh bán hàng</div>
        </div>

        {/* Cảnh báo tồn kho */}
        <div className="bg-white rounded-xl border border-slate-200 p-5">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm text-slate-500">Cảnh báo tồn kho</span>
            <AlertTriangle className="w-4 h-4 text-amber-500" />
          </div>
          <div className="text-2xl font-bold text-amber-500 mb-2">12</div>
          <div className="text-xs text-amber-500 font-medium">Sản phẩm sắp hết hàng</div>
        </div>
      </div>

      {/* Charts row */}
      <div className="grid grid-cols-5 gap-4">
        <div className="col-span-3 bg-white rounded-xl border border-slate-200 p-5">
          <h3 className="text-sm font-semibold text-slate-800 mb-4">Doanh thu theo kênh</h3>
          <AreaChart />
        </div>
        <div className="col-span-2 bg-white rounded-xl border border-slate-200 p-5">
          <h3 className="text-sm font-semibold text-slate-800 mb-4">Trạng thái đơn hàng</h3>
          <BarChart />
        </div>
      </div>

      {/* Bottom row */}
      <div className="grid grid-cols-2 gap-4">
        {/* Top products */}
        <div className="bg-white rounded-xl border border-slate-200 p-5">
          <h3 className="text-sm font-semibold text-slate-800 mb-1">Sản phẩm bán chạy</h3>
          <div className="divide-y divide-slate-100">
            {[
              { name: 'Áo thun nam basic', sold: 254, rev: '11.700.000 đ', pct: '12%' },
              { name: 'Quần jean nữ',      sold: 189, rev: '18.900.000 đ', pct: '8%'  },
              { name: 'Váy hoa mùa hè',    sold: 143, rev: '9.500.000 đ',  pct: '5%'  },
              { name: 'Áo khoác bomber',   sold: 112, rev: '22.400.000 đ', pct: '3%'  },
            ].map((p) => (
              <div key={p.name} className="flex items-center justify-between py-3">
                <div>
                  <div className="text-sm font-medium text-slate-800">{p.name}</div>
                  <div className="text-xs text-slate-400 mt-0.5">
                    Đã bán: {p.sold} | Doanh thu: {p.rev}
                  </div>
                </div>
                <span className="inline-flex items-center gap-1 text-xs font-semibold text-emerald-600 bg-emerald-50 px-2.5 py-1 rounded-full">
                  <TrendingUp className="w-3 h-3" />
                  {p.pct}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Low stock */}
        <div className="bg-white rounded-xl border border-slate-200 p-5">
          <h3 className="flex items-center gap-1.5 text-sm font-semibold text-amber-600 mb-1">
            <AlertTriangle className="w-4 h-4" />
            Cảnh báo tồn kho thấp
          </h3>
          <div className="divide-y divide-slate-100">
            {[
              { name: 'Áo thun nam basic - Size M',  channel: 'Kênh Shopee',      qty: 5, danger: false },
              { name: 'Quần jean nữ - Size 28',       channel: 'Kênh TikTok Shop', qty: 3, danger: true  },
              { name: 'Váy hoa mùa hè - Size S',      channel: 'Kênh Lazada',      qty: 7, danger: false },
              { name: 'Áo khoác bomber - Size L',     channel: 'Kênh Shopee',      qty: 2, danger: true  },
            ].map((s) => (
              <div key={s.name} className="flex items-center justify-between py-3">
                <div>
                  <div className="text-sm font-medium text-slate-800">{s.name}</div>
                  <div className="text-xs text-slate-400 mt-0.5">{s.channel}</div>
                </div>
                <span className={`text-xs font-semibold px-2.5 py-1 rounded-full ${
                  s.danger
                    ? 'bg-red-100 text-red-700'
                    : 'bg-orange-100 text-orange-700'
                }`}>
                  Còn {s.qty}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
