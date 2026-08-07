import { useEffect, useRef, useState } from 'react';
import {
  Warehouse, Phone, Mail, User, CheckCircle2, XCircle,
  AlertTriangle, Loader2, Store, CloudUpload, Info, RefreshCw, Search,
} from 'lucide-react';
import { toast } from 'react-toastify';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import warehouseApi from '../../../api/warehouseApi';
import channelApi from '../../../api/channelApi';
import useConfirmDialog from '../hooks/useConfirmDialog';
import useAuth from '../../auth/hooks/useAuth';
import { ROLES } from '../../auth/constants/roles';

// Fix Leaflet default marker icons (broken in Vite/webpack)
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

const PLATFORM_META = {
  SHOPIFY: { label: 'Shopify',     color: '#047857', bg: '#ecfdf5', border: '#a7f3d0', dot: '#10b981' },
  LAZADA:  { label: 'Lazada',      color: '#3730a3', bg: '#eef2ff', border: '#c7d2fe', dot: '#6366f1' },
  TIKTOK:  { label: 'TikTok Shop', color: '#0f172a', bg: '#f8fafc', border: '#cbd5e1', dot: '#475569' },
};

const unwrap = (r) => r?.data?.data ?? r?.data ?? r ?? {};
const NOMINATIM = 'https://nominatim.openstreetmap.org';
// Vietnam bounding box for viewbox bias
const VN_VIEWBOX = '102.14,8.18,109.46,23.39';

// ── Goong Maps API (Vietnam-native, Google Maps quality) ──────────────────
// Get free API key at https://account.goong.io (500k req/month)
// Endpoints: https://rsapi.goong.io/Place/AutoComplete & /geocode/json
const GOONG_KEY = import.meta.env.VITE_GOONG_API_KEY ?? '';
const GOONG_BASE = 'https://rsapi.goong.io';

// Goong Place Autocomplete — returns predictions like Google Maps
// Params: input (text), api_key, location (lat,lon for bias), radius (meters)
async function goongAutocomplete(input) {
  if (!input?.trim() || !GOONG_KEY) return null; // null = use fallback
  try {
    const params = new URLSearchParams({
      input: input.trim(),
      api_key: GOONG_KEY,
      // Bias toward Vietnam center
      location: '16.04,106.7',
      radius: '500000', // 500km — covers all of Vietnam
    });
    const res = await fetch(`${GOONG_BASE}/Place/AutoComplete?${params}`);
    if (!res.ok) return null;
    const data = await res.json();
    // data.predictions[].description, .place_id
    return data?.predictions ?? null;
  } catch { return null; }
}

// Goong Place Detail — get lat/lon from place_id
async function goongPlaceDetail(placeId) {
  if (!placeId || !GOONG_KEY) return null;
  try {
    const params = new URLSearchParams({ place_id: placeId, api_key: GOONG_KEY });
    const res = await fetch(`${GOONG_BASE}/Place/Detail?${params}`);
    if (!res.ok) return null;
    const data = await res.json();
    const loc = data?.result?.geometry?.location;
    return loc ? { lat: loc.lat, lon: loc.lng } : null;
  } catch { return null; }
}

// Goong Reverse Geocode — lat/lon → address
async function goongReverse(lat, lon) {
  if (!GOONG_KEY) return null;
  try {
    const params = new URLSearchParams({ latlng: `${lat},${lon}`, api_key: GOONG_KEY });
    const res = await fetch(`${GOONG_BASE}/geocode/json?${params}`);
    if (!res.ok) return null;
    const data = await res.json();
    const result = data?.results?.[0];
    if (!result) return null;
    return {
      address: result.formatted_address?.replace(/,?\s*Việt Nam\s*$/i, '').trim() ?? '',
    };
  } catch { return null; }
}

// ── Nominatim fallback (when no Goong key) ───────────────────────────────
async function nominatimSearch(query) {
  if (!query?.trim() || query.trim().length < 2) return [];
  try {
    const params = new URLSearchParams({
      q: query.trim(),
      format: 'json',
      limit: '8',
      countrycodes: 'vn',
      viewbox: VN_VIEWBOX,
      bounded: '1',
      addressdetails: '1',
      'accept-language': 'vi',
    });
    const res = await fetch(`${NOMINATIM}/search?${params}`, { headers: { 'Accept-Language': 'vi,en' } });
    if (!res.ok) return [];
    const data = await res.json();
    if (!data?.length) {
      // retry without bounded
      const p2 = new URLSearchParams({ q: query.trim(), format: 'json', limit: '8', countrycodes: 'vn', addressdetails: '1', 'accept-language': 'vi' });
      const r2 = await fetch(`${NOMINATIM}/search?${p2}`, { headers: { 'Accept-Language': 'vi,en' } });
      return r2.ok ? await r2.json() : [];
    }
    return data;
  } catch { return []; }
}

async function reverseGeocode(lat, lon) {
  // Block coordinates outside Vietnam bounding box
  if (!isInsideVietnam(lat, lon)) return null;
  // Try Goong first (Vietnam-accurate), fallback to Nominatim
  if (GOONG_KEY) {
    const goong = await goongReverse(lat, lon);
    if (goong?.address) return goong;
  }
  try {
    const params = new URLSearchParams({
      lat: String(lat), lon: String(lon),
      format: 'json', addressdetails: '1', zoom: '18', 'accept-language': 'vi',
    });
    const res = await fetch(`${NOMINATIM}/reverse?${params}`, { headers: { 'Accept-Language': 'vi,en' } });
    if (!res.ok) return null;
    const data = await res.json();
    if (!data) return null;
    return { address: buildNominatimDisplayName(data) };
  } catch { return null; }
}

function buildNominatimDisplayName(result) {
  if (result.display_name) {
    return result.display_name.replace(/,?\s*Việt Nam\s*$/i, '').replace(/,?\s*Vietnam\s*$/i, '').trim();
  }
  const a = result.address ?? {};
  return [
    a.house_number ? `${a.house_number} ${a.road || ''}`.trim() : a.road,
    a.suburb || a.neighbourhood,
    a.city_district || a.district,
    a.city || a.town || a.village,
    a.state,
  ].filter(Boolean).join(', ');
}

// ── AddressAutocomplete ──────────────────────────────────────────────────
// Uses Goong Maps if VITE_GOONG_API_KEY is set (Vietnam-accurate, Google quality)
// Falls back to Nominatim OSM when no key available.
function AddressAutocomplete({ value, onChange, onSelect, disabled, error }) {
  const [suggestions, setSuggestions] = useState([]);
  const [showDrop, setShowDrop] = useState(false);
  const [searching, setSearching] = useState(false);
  const debounceRef = useRef(null);
  const wrapRef = useRef(null);

  useEffect(() => {
    const handler = (e) => { if (wrapRef.current && !wrapRef.current.contains(e.target)) setShowDrop(false); };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleChange = (e) => {
    const v = e.target.value;
    onChange(v);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (v.trim().length < 3) { setSuggestions([]); setShowDrop(false); return; }
    debounceRef.current = setTimeout(async () => {
      setSearching(true);
      if (GOONG_KEY) {
        // Goong: returns { predictions: [{ description, place_id }] }
        const preds = await goongAutocomplete(v);
        if (preds) {
          setSuggestions(preds.map((p) => ({ type: 'goong', label: p.description, placeId: p.place_id })));
          setShowDrop(preds.length > 0);
          setSearching(false);
          return;
        }
      }
      // Fallback: Nominatim
      const results = await nominatimSearch(v);
      setSuggestions(results.map((r) => ({ type: 'nominatim', label: buildNominatimDisplayName(r), lat: parseFloat(r.lat), lon: parseFloat(r.lon) })));
      setShowDrop(results.length > 0);
      setSearching(false);
    }, 400);
  };

  const pick = async (suggestion) => {
    setSuggestions([]); setShowDrop(false);
    if (suggestion.type === 'goong') {
      onChange(suggestion.label);
      // Fetch lat/lon via Place Detail
      const coords = await goongPlaceDetail(suggestion.placeId);
      onSelect({ address: suggestion.label, lat: coords?.lat ?? 16.04, lon: coords?.lon ?? 106.7 });
    } else {
      onChange(suggestion.label);
      onSelect({ address: suggestion.label, lat: suggestion.lat, lon: suggestion.lon });
    }
  };

  const hasGoongKey = Boolean(GOONG_KEY);

  return (
    <div ref={wrapRef} style={{ position: 'relative' }}>
      {!hasGoongKey && (
        <div style={{ fontSize: 11, color: '#f97316', marginBottom: 4, display: 'flex', alignItems: 'center', gap: 3 }}>
          <AlertTriangle size={10} />
          Thêm <code>VITE_GOONG_API_KEY</code> vào .env để tìm kiếm chính xác hơn (<a href="https://account.goong.io" target="_blank" rel="noopener noreferrer" style={{ color: '#2563eb' }}>lấy key miễn phí</a>)
        </div>
      )}
      <div style={{ position: 'relative' }}>
        <Search size={13} style={{ position: 'absolute', left: 9, top: '50%', transform: 'translateY(-50%)', color: '#94a3b8', pointerEvents: 'none' }} />
        {searching && <Loader2 size={13} style={{ position: 'absolute', right: 9, top: '50%', transform: 'translateY(-50%)', color: '#94a3b8', animation: 'spin 1s linear infinite' }} />}
        <input
          value={value}
          onChange={handleChange}
          disabled={disabled}
          placeholder={hasGoongKey ? 'Tìm địa chỉ tại Việt Nam...' : 'Nhập địa chỉ (tối thiểu 3 ký tự)...'}
          style={{ width: '100%', boxSizing: 'border-box', padding: '9px 32px 9px 28px', borderRadius: 8, border: `1px solid ${error ? '#fca5a5' : '#e2e8f0'}`, fontSize: 13, color: '#0f172a', outline: 'none', background: disabled ? '#f8fafc' : '#fff' }}
        />
      </div>
      {showDrop && (
        <div style={{ position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 999, background: '#fff', border: '1px solid #e2e8f0', borderRadius: 8, boxShadow: '0 8px 24px rgba(0,0,0,.12)', marginTop: 2, maxHeight: 240, overflowY: 'auto' }}>
          {suggestions.map((s, i) => (
            <button key={i} type="button" onMouseDown={() => pick(s)}
              style={{ width: '100%', textAlign: 'left', padding: '9px 12px', border: 'none', background: 'none', cursor: 'pointer', borderBottom: i < suggestions.length - 1 ? '1px solid #f1f5f9' : 'none' }}
              onMouseEnter={(e) => e.currentTarget.style.background = '#f8fafc'}
              onMouseLeave={(e) => e.currentTarget.style.background = 'none'}
            >
              <div style={{ fontSize: 12.5, color: '#0f172a', fontWeight: 500 }}>{s.label}</div>
            </button>
          ))}
          <div style={{ padding: '4px 12px 6px', fontSize: 10.5, color: '#94a3b8', borderTop: '1px solid #f1f5f9', textAlign: 'right' }}>
            {hasGoongKey ? '© Goong Maps' : '© OpenStreetMap'}
          </div>
        </div>
      )}
      {error && <span style={{ fontSize: 11.5, color: '#dc2626', display: 'flex', alignItems: 'center', gap: 3, marginTop: 3 }}><AlertTriangle size={10} />{error}</span>}
    </div>
  );
}

// ── Vietnam bounds ────────────────────────────────────────────────────────
// Bounding box: SW(8.18, 102.14) → NE(23.39, 109.46)
const VN_BOUNDS = L.latLngBounds(
  L.latLng(8.18,  102.14),  // SW
  L.latLng(23.39, 109.46),  // NE
);

function isInsideVietnam(lat, lng) {
  return lat >= 8.18 && lat <= 23.39 && lng >= 102.14 && lng <= 109.46;
}

// ── LeafletMap ─────────────────────────────────────────────────────────────
// Renders a full interactive Leaflet map. Click anywhere → reverse geocode → fill address.
// Uses Goong Maps tiles when VITE_GOONG_API_KEY is set (Vietnam-updated data).
// Clicks outside Vietnam bounding box are silently ignored.
function LeafletMap({ coords, onMapClick }) {
  const mapRef = useRef(null);
  const leafletRef = useRef(null);
  const markerRef = useRef(null);

  // Init map once
  useEffect(() => {
    if (leafletRef.current) return;
    const map = L.map(mapRef.current, {
      center: [16.04, 106.7],
      zoom: 6,
      zoomControl: true,
      maxBounds: VN_BOUNDS,          // restrict pan to Vietnam
      maxBoundsViscosity: 0.85,      // rubber-band effect at edges
    });

    // Use Goong Maps tile if key available (has post-2025 merger data)
    // otherwise fallback to OSM
    if (GOONG_KEY) {
      L.tileLayer(`https://tiles.goong.io/assets/goong_map_web.pbf?api_key=${GOONG_KEY}`, {
        attribution: '© <a href="https://goong.io">Goong Maps</a>',
        maxZoom: 20,
      }).addTo(map);
    } else {
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
        maxZoom: 19,
      }).addTo(map);
    }

    map.on('click', (e) => {
      const { lat, lng } = e.latlng;
      // Block clicks outside Vietnam
      if (!isInsideVietnam(lat, lng)) {
        // Visual feedback: briefly flash the bounds
        L.rectangle(VN_BOUNDS, {
          color: '#ef4444', weight: 2, fill: false, dashArray: '6 4',
        }).addTo(map).bringToFront();
        setTimeout(() => map.eachLayer((layer) => {
          if (layer instanceof L.Rectangle) map.removeLayer(layer);
        }), 800);
        return;
      }
      onMapClick({ lat, lon: lng });
    });

    leafletRef.current = map;
    return () => { map.remove(); leafletRef.current = null; };
  }, []);

  // Move marker and zoom when coords change
  useEffect(() => {
    const map = leafletRef.current;
    if (!map || !coords) return;
    if (markerRef.current) {
      markerRef.current.setLatLng([coords.lat, coords.lon]);
    } else {
      markerRef.current = L.marker([coords.lat, coords.lon]).addTo(map);
    }
    map.setView([coords.lat, coords.lon], 16, { animate: true });
  }, [coords?.lat, coords?.lon]);

  return (
    <div ref={mapRef} style={{ width: '100%', height: '100%', minHeight: 480 }} />
  );
}

// ── ChannelStatus row ──────────────────────────────────────────────────────
function ChannelStatus({ ch, syncResult }) {
  const meta = PLATFORM_META[ch.platform] ?? PLATFORM_META.SHOPIFY;
  const result = syncResult?.channels?.find((c) => c.channelId === ch.id);
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 12px', borderRadius: 10, border: `1px solid ${meta.border}`, background: meta.bg }}>
      <div style={{ width: 8, height: 8, borderRadius: '50%', background: meta.dot, flexShrink: 0 }} />
      <span style={{ fontSize: 12.5, fontWeight: 700, color: meta.color, flex: 1 }}>{ch.displayName}</span>
      <span style={{ fontSize: 11, color: '#64748b' }}>{meta.label}</span>
      {result && (result.success
        ? <CheckCircle2 size={13} color="#16a34a" />
        : <span title={result.error} style={{ cursor: 'help', display: 'flex', gap: 2 }}><XCircle size={13} color="#dc2626" /><Info size={11} color="#f59e0b" /></span>
      )}
    </div>
  );
}

// ── SyncResultPanel ────────────────────────────────────────────────────────
function SyncResultPanel({ result, onClose }) {
  if (!result) return null;
  const allOk = result.allSucceeded;
  return (
    <div style={{ borderRadius: 12, padding: '12px 16px', marginBottom: 16, background: allOk ? '#f0fdf4' : '#fffbeb', border: `1px solid ${allOk ? '#bbf7d0' : '#fde68a'}` }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
        {allOk ? <CheckCircle2 size={15} color="#16a34a" /> : <AlertTriangle size={15} color="#d97706" />}
        <span style={{ fontWeight: 700, fontSize: 13, color: allOk ? '#15803d' : '#92400e', flex: 1 }}>
          {allOk ? 'Đã cập nhật tất cả sàn thành công' : 'Hoàn tất — một số sàn không hỗ trợ cập nhật qua API'}
        </span>
        <button onClick={onClose} style={{ border: 'none', background: 'none', cursor: 'pointer', color: '#94a3b8', fontSize: 18, lineHeight: 1 }}>×</button>
      </div>
      {(result.channels ?? []).map((ch) => (
        <div key={ch.channelId} style={{ display: 'flex', alignItems: 'flex-start', gap: 6, fontSize: 12, marginTop: 5 }}>
          {ch.success ? <CheckCircle2 size={12} color="#16a34a" style={{ marginTop: 1 }} /> : <XCircle size={12} color="#dc2626" style={{ marginTop: 1 }} />}
          <div>
            <span style={{ fontWeight: 700 }}>{ch.channelName}</span>
            <span style={{ color: '#64748b', marginLeft: 4 }}>({ch.platform})</span>
            {ch.success
              ? <span style={{ color: '#16a34a', marginLeft: 4 }}>Thành công</span>
              : <span style={{ color: '#92400e', marginLeft: 4 }}>{ch.error}</span>}
          </div>
        </div>
      ))}
    </div>
  );
}

// ── LabeledInput ──────────────────────────────────────────────────────────
function LabeledInput({ label, required, error, icon: Icon, disabled, hint, ...props }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <label style={{ fontSize: 12.5, fontWeight: 700, color: '#334155' }}>
        {label}{required && <span style={{ color: '#ef4444', marginLeft: 2 }}>*</span>}
      </label>
      <div style={{ position: 'relative' }}>
        {Icon && <Icon size={13} style={{ position: 'absolute', left: 9, top: '50%', transform: 'translateY(-50%)', color: '#94a3b8', pointerEvents: 'none' }} />}
        <input {...props} disabled={disabled} style={{ width: '100%', boxSizing: 'border-box', padding: Icon ? '9px 10px 9px 28px' : '9px 10px', borderRadius: 8, border: `1px solid ${error ? '#fca5a5' : '#e2e8f0'}`, fontSize: 13, color: '#0f172a', outline: 'none', background: disabled ? '#f8fafc' : '#fff' }} />
      </div>
      {hint && !error && <span style={{ fontSize: 11.5, color: '#f97316', display: 'flex', alignItems: 'center', gap: 3 }}><AlertTriangle size={10} />{hint}</span>}
      {error && <span style={{ fontSize: 11.5, color: '#dc2626', display: 'flex', alignItems: 'center', gap: 3 }}><AlertTriangle size={10} />{error}</span>}
    </div>
  );
}

// ── Main page ──────────────────────────────────────────────────────────────
export default function WarehousePage() {
  const { user } = useAuth();
  const isReadOnly = user?.role === ROLES.OPERATIONS;
  const { confirm, ConfirmDialog } = useConfirmDialog();

  const [warehouse, setWarehouse] = useState(null);
  const [channels, setChannels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [syncResult, setSyncResult] = useState(null);
  const [reverseLoading, setReverseLoading] = useState(false);

  const [form, setForm] = useState({
    name: '', address: '', contactName: '', phone: '', email: '',
    countryCode: 'VN',
  });
  const [coords, setCoords] = useState(null); // { lat, lon }
  const [errors, setErrors] = useState({});

  const load = async () => {
    setLoading(true);
    try {
      const [wRes, chRes] = await Promise.all([warehouseApi.getMaster(), channelApi.getAll()]);
      const w = unwrap(wRes);
      const allChannels = Array.isArray(unwrap(chRes)) ? unwrap(chRes) : [];
      setWarehouse(w);
      setChannels(allChannels.filter((c) => ['SHOPIFY', 'LAZADA', 'TIKTOK'].includes(c.platform) && c.connectionState === 'CONNECTED'));
      setForm((p) => ({ ...p, name: w.name ?? '', address: w.address ?? '' }));
    } catch { toast.error('Không thể tải thông tin kho hàng.'); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  // When address is loaded initially, geocode it to show pin
  useEffect(() => {
    if (!form.address?.trim() || coords) return;
    const init = async () => {
      if (GOONG_KEY) {
        const preds = await goongAutocomplete(form.address);
        if (preds?.[0]) {
          const c = await goongPlaceDetail(preds[0].place_id);
          if (c) { setCoords(c); return; }
        }
      }
      nominatimSearch(form.address).then((results) => {
        if (results?.[0]) setCoords({ lat: parseFloat(results[0].lat), lon: parseFloat(results[0].lon) });
      });
    };
    init();
  }, [form.address]);

  const set = (field) => (v) => {
    const val = typeof v === 'string' ? v : v.target.value;
    setForm((p) => ({ ...p, [field]: val }));
    setErrors((p) => ({ ...p, [field]: '' }));
  };

  // Called when user picks from autocomplete dropdown
  const handleAddressSelect = ({ address, lat, lon }) => {
    setForm((p) => ({ ...p, address }));
    setCoords({ lat, lon });
    setErrors((p) => ({ ...p, address: '' }));
  };

  // Called when user clicks on Leaflet map
  const handleMapClick = async ({ lat, lon }) => {
    setReverseLoading(true);
    try {
      const result = await reverseGeocode(lat, lon);
      if (result?.address) {
        setForm((p) => ({ ...p, address: result.address }));
        setCoords({ lat, lon });
        setErrors((p) => ({ ...p, address: '' }));
      }
    } finally { setReverseLoading(false); }
  };

  const validate = () => {
    const e = {};
    if (!form.name.trim()) e.name = 'Tên kho là bắt buộc';
    if (!form.address.trim()) e.address = 'Địa chỉ là bắt buộc';
    if (channels.length > 0 && !form.contactName.trim()) e.contactName = 'Bắt buộc cho Lazada & TikTok';
    if (channels.length > 0 && !form.phone.trim()) e.phone = 'Bắt buộc cho tất cả sàn';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSaveLocal = async () => {
    if (!validate()) return;
    const ok = await confirm({ title: 'Lưu thông tin kho?', message: 'Chỉ lưu trong hệ thống, chưa cập nhật lên sàn.', confirmLabel: 'Lưu' });
    if (!ok) return;
    setSaving(true);
    try {
      await warehouseApi.update(warehouse.id, { name: form.name, address: form.address, isActive: true });
      toast.success('Đã lưu thông tin kho.');
      await load();
    } catch (e) { toast.error(e?.response?.data?.message || 'Không thể lưu.'); }
    finally { setSaving(false); }
  };

  const handleSync = async () => {
    if (!validate()) return;
    if (channels.length === 0) { toast.info('Không có sàn nào đang kết nối.'); return; }
    const list = channels.map((c) => `${c.displayName} (${PLATFORM_META[c.platform]?.label ?? c.platform})`).join(', ');
    const ok = await confirm({ title: 'Cập nhật kho lên các sàn?', message: `Thông tin kho sẽ được cập nhật lên:\n${list}\n\nLưu ý: Lazada và TikTok không hỗ trợ cập nhật kho qua API — chỉ Shopify sẽ được cập nhật tự động.`, confirmLabel: 'Cập nhật', tone: 'warning' });
    if (!ok) return;
    setSyncing(true); setSyncResult(null);
    try {
      const res = await warehouseApi.syncToMarketplaces(warehouse.id, { name: form.name, address: form.address, contactName: form.contactName, phone: form.phone, email: form.email || undefined, countryCode: form.countryCode || 'VN' });
      const result = unwrap(res);
      setSyncResult(result);
      if (result.allSucceeded) toast.success('Đã cập nhật kho lên tất cả các sàn.');
      else toast.warn(`Một số sàn không hỗ trợ — xem chi tiết bên dưới.`);
      await load();
    } catch (e) { toast.error(e?.response?.data?.message || 'Không thể đồng bộ.'); }
    finally { setSyncing(false); }
  };

  if (loading) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, minHeight: 320, color: '#94a3b8' }}>
      <Loader2 size={20} style={{ animation: 'spin 1s linear infinite' }} /> Đang tải...
    </div>
  );
  if (!warehouse) return <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>Không tìm thấy kho mặc định.</div>;

  return (
    <main style={{ maxWidth: 1100, margin: '0 auto', padding: '24px 20px' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 18 }}>
        <div style={{ width: 40, height: 40, borderRadius: 10, background: '#eff6ff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Warehouse size={20} color="#2563eb" />
        </div>
        <div>
          <h1 style={{ margin: 0, fontSize: 20, fontWeight: 800, color: '#0f172a' }}>Kho hàng</h1>
          <p style={{ margin: 0, fontSize: 12.5, color: '#64748b' }}>Kho mặc định dùng chung cho tất cả các sàn</p>
        </div>
      </div>

      <SyncResultPanel result={syncResult} onClose={() => setSyncResult(null)} />

      {/* Two-column layout */}
      <div style={{ display: 'grid', gridTemplateColumns: '340px 1fr', background: '#fff', borderRadius: 14, border: '1px solid #e2e8f0', overflow: 'hidden', boxShadow: '0 4px 16px rgba(0,0,0,.06)' }}>

        {/* ── LEFT: form ── */}
        <div style={{ padding: '22px 20px', borderRight: '1px solid #e2e8f0', display: 'flex', flexDirection: 'column', gap: 14, overflowY: 'auto', maxHeight: '85vh' }}>

          <LabeledInput label="Tên kho hàng" required icon={Store}
            value={form.name} onChange={set('name')} disabled={isReadOnly}
            error={errors.name} placeholder="Kho mặc định đa sàn" />

          <LabeledInput label="Họ và tên người liên hệ"
            required={channels.length > 0} icon={User}
            value={form.contactName} onChange={set('contactName')} disabled={isReadOnly}
            error={errors.contactName} hint={channels.length > 0 ? 'Bắt buộc cho Lazada & TikTok' : undefined}
            placeholder="Nguyễn Văn A" />

          {/* Phone with prefix */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <label style={{ fontSize: 12.5, fontWeight: 700, color: '#334155' }}>
              Số điện thoại{channels.length > 0 && <span style={{ color: '#ef4444', marginLeft: 2 }}>*</span>}
            </label>
            <div style={{ display: 'flex', gap: 6 }}>
              <select value={form.countryCode === 'VN' ? '+84' : '+1'} onChange={(e) => setForm((p) => ({ ...p, countryCode: e.target.value === '+84' ? 'VN' : 'US' }))} disabled={isReadOnly}
                style={{ width: 68, padding: '9px 6px', borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 13, color: '#0f172a', background: isReadOnly ? '#f8fafc' : '#fff', outline: 'none' }}>
                <option value="+84">+84</option>
                <option value="+1">+1</option>
                <option value="+65">+65</option>
                <option value="+60">+60</option>
              </select>
              <input type="tel" value={form.phone} onChange={set('phone')} disabled={isReadOnly}
                placeholder="0912 345 678"
                style={{ flex: 1, padding: '9px 10px', borderRadius: 8, border: `1px solid ${errors.phone ? '#fca5a5' : '#e2e8f0'}`, fontSize: 13, outline: 'none', background: isReadOnly ? '#f8fafc' : '#fff' }} />
            </div>
            {channels.length > 0 && !errors.phone && <span style={{ fontSize: 11.5, color: '#f97316', display: 'flex', alignItems: 'center', gap: 3 }}><AlertTriangle size={10} />Bắt buộc cho tất cả sàn</span>}
            {errors.phone && <span style={{ fontSize: 11.5, color: '#dc2626', display: 'flex', alignItems: 'center', gap: 3 }}><AlertTriangle size={10} />{errors.phone}</span>}
          </div>

          <LabeledInput label="Email" icon={Mail}
            type="email" value={form.email} onChange={set('email')} disabled={isReadOnly}
            placeholder="warehouse@company.vn" />

          {/* Address with autocomplete */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <label style={{ fontSize: 12.5, fontWeight: 700, color: '#334155' }}>
              Địa chỉ kho <span style={{ color: '#ef4444' }}>*</span>
            </label>
            <AddressAutocomplete
              value={form.address}
              onChange={set('address')}
              onSelect={handleAddressSelect}
              disabled={isReadOnly}
              error={errors.address}
            />
            {reverseLoading && (
              <span style={{ fontSize: 11.5, color: '#2563eb', display: 'flex', alignItems: 'center', gap: 3 }}>
                <Loader2 size={10} style={{ animation: 'spin 1s linear infinite' }} /> Đang lấy địa chỉ từ bản đồ...
              </span>
            )}
            {coords && !reverseLoading && (
              <span style={{ fontSize: 11, color: '#059669' }}>
                📍 {coords.lat.toFixed(5)}, {coords.lon.toFixed(5)}
              </span>
            )}
          </div>

          {/* Channels */}
          {channels.length > 0 && (
            <div>
              <div style={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.06em', marginBottom: 8 }}>Sàn kết nối ({channels.length})</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {channels.map((ch) => <ChannelStatus key={ch.id} ch={ch} syncResult={syncResult} />)}
              </div>
            </div>
          )}

          {/* Sync note */}
          <div style={{ background: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: 10, padding: '10px 12px', fontSize: 11.5, color: '#166534', lineHeight: 1.55 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 5, fontWeight: 700, marginBottom: 4 }}>
              <RefreshCw size={11} /> Lưu ý đồng bộ
            </div>
            Shopify: cập nhật Location qua API tự động.<br />
            Lazada & TikTok: không hỗ trợ cập nhật kho qua API — vui lòng cập nhật thủ công tại Seller Center.
          </div>

          {/* Buttons */}
          {!isReadOnly && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 4 }}>
              <button type="button" onClick={handleSync} disabled={saving || syncing}
                style={{ padding: '10px', borderRadius: 8, border: 'none', background: '#1a56db', color: '#fff', fontWeight: 700, fontSize: 13, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
                {syncing ? <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> : <CloudUpload size={14} />}
                {syncing ? 'Đang đồng bộ...' : 'Lưu & cập nhật lên sàn'}
              </button>
              <button type="button" onClick={handleSaveLocal} disabled={saving || syncing}
                style={{ padding: '9px', borderRadius: 8, border: '1px solid #e2e8f0', background: '#fff', fontWeight: 600, fontSize: 13, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, color: '#475569' }}>
                {saving ? <Loader2 size={13} style={{ animation: 'spin 1s linear infinite' }} /> : null}
                {saving ? 'Đang lưu...' : 'Chỉ lưu trong hệ thống'}
              </button>
            </div>
          )}
        </div>

        {/* ── RIGHT: Leaflet map ── */}
        <div style={{ position: 'relative' }}>
          <LeafletMap coords={coords} onMapClick={handleMapClick} />
          {reverseLoading && (
            <div style={{ position: 'absolute', top: 10, left: '50%', transform: 'translateX(-50%)', background: 'rgba(255,255,255,.92)', borderRadius: 8, padding: '6px 12px', display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, fontWeight: 600, color: '#2563eb', boxShadow: '0 2px 8px rgba(0,0,0,.12)', zIndex: 1000 }}>
              <Loader2 size={13} style={{ animation: 'spin 1s linear infinite' }} /> Đang lấy địa chỉ...
            </div>
          )}
          {coords && form.address && (
            <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, zIndex: 1000, background: 'rgba(255,255,255,.88)', backdropFilter: 'blur(4px)', padding: '7px 12px', borderTop: '1px solid #e2e8f0', fontSize: 12, color: '#475569', display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ fontSize: 14 }}>📍</span>
              <span style={{ flex: 1, fontWeight: 500 }}>Đã ghim: {form.address}</span>
              <a href={`https://www.openstreetmap.org/?mlat=${coords.lat}&mlon=${coords.lon}#map=16/${coords.lat}/${coords.lon}`} target="_blank" rel="noopener noreferrer"
                style={{ fontSize: 11, fontWeight: 700, color: '#2563eb', textDecoration: 'none', padding: '4px 8px', border: '1px solid #bfdbfe', borderRadius: 6, background: '#eff6ff', whiteSpace: 'nowrap' }}>
                Xem trên bản đồ ↗
              </a>
            </div>
          )}
        </div>
      </div>

      {ConfirmDialog}
    </main>
  );
}
