import { useState, useEffect, useCallback, useRef } from 'react';
import { MapPin, ChevronDown } from 'lucide-react';
import addressApi from '../../../api/addressApi';
import styles from './CascadingAddress.module.css';

const CascadingAddress = ({ value = {}, onChange, countryCode: countryCodeProp }) => {
  const countryCode = countryCodeProp || value.country || '';
  const hasCountry = !!countryCode;

  // ── VIETNAM STATE ─────────────────────────────────────────────────────────
  const [provinces, setProvinces] = useState([]);
  const [districts, setDistricts] = useState([]);
  const [wards, setWards] = useState([]);
  const [provinceCode, setProvinceCode] = useState('');
  const [districtCode, setDistrictCode] = useState('');
  const [wardCode, setWardCode] = useState('');

  // ── FOREIGN STATE (cities = level 1 divisions) ─────────────────────────────
  const [cities, setCities] = useState([]);
  const [cityCode, setCityCode] = useState('');
  const [cityManual, setCityManual] = useState('');

  // ── LOADING STATES ─────────────────────────────────────────────────────────
  const [provinceLoading, setProvinceLoading] = useState(false);
  const [districtLoading, setDistrictLoading] = useState(false);
  const [wardLoading, setWardLoading] = useState(false);
  const [cityLoading, setCityLoading] = useState(false);

  // ── MOUNT TRACKING: prevent auto-select on initial value propagation ──────
  const vnProvinceMounted = useRef(false);
  const vnDistrictMounted = useRef(false);
  const vnWardMounted = useRef(false);
  const foreignCityMounted = useRef(false);

  // ── RESET ALL WHEN COUNTRY CHANGES ────────────────────────────────────────
  useEffect(() => {
    setProvinces([]);
    setDistricts([]);
    setWards([]);
    setProvinceCode('');
    setDistrictCode('');
    setWardCode('');
    setCities([]);
    setCityCode('');
    setCityManual('');
    vnProvinceMounted.current = false;
    vnDistrictMounted.current = false;
    vnWardMounted.current = false;
    foreignCityMounted.current = false;
  }, [countryCode]);

  // ── VIETNAM: LOAD PROVINCES ────────────────────────────────────────────────
  useEffect(() => {
    if (!hasCountry || countryCode !== 'VN') return;
    setProvinceLoading(true);
    addressApi.getDivisions(countryCode, 1).then((data) => {
      setProvinces(data || []);
      setProvinceLoading(false);
    }).catch(() => setProvinceLoading(false));
  }, [countryCode, hasCountry]);

  // ── VIETNAM: SYNC PROVINCE CODE FROM EXTERNAL VALUE (on mount only) ───────
  useEffect(() => {
    if (!provinces.length || provinceCode || !value.province) return;
    if (vnProvinceMounted.current) return;
    const found = provinces.find((p) => p.name === value.province);
    if (found) {
      vnProvinceMounted.current = true;
      setProvinceCode(found.code);
    }
  }, [provinces]);

  // ── VIETNAM: SYNC DISTRICT CODE FROM EXTERNAL VALUE (on mount only) ───────
  useEffect(() => {
    if (!districts.length || districtCode || !value.district) return;
    if (vnDistrictMounted.current) return;
    const found = districts.find((d) => d.name === value.district);
    if (found) {
      vnDistrictMounted.current = true;
      setDistrictCode(found.code);
    }
  }, [districts]);

  // ── VIETNAM: SYNC WARD CODE FROM EXTERNAL VALUE (on mount only) ───────────
  useEffect(() => {
    if (!wards.length || wardCode || !value.ward) return;
    if (vnWardMounted.current) return;
    const found = wards.find((w) => w.name === value.ward);
    if (found) {
      vnWardMounted.current = true;
      setWardCode(found.code);
    }
  }, [wards]);

  // ── VIETNAM: LOAD DISTRICTS ────────────────────────────────────────────────
  useEffect(() => {
    if (!provinceCode) {
      setDistricts([]);
      setWards([]);
      return;
    }
    setDistrictLoading(true);
    addressApi.getDivisions(countryCode, 2, provinceCode).then((data) => {
      setDistricts(data || []);
      setDistrictLoading(false);
    }).catch(() => setDistrictLoading(false));
  }, [countryCode, provinceCode]);

  // ── VIETNAM: LOAD WARDS ────────────────────────────────────────────────────
  useEffect(() => {
    if (!districtCode) {
      setWards([]);
      return;
    }
    setWardLoading(true);
    addressApi.getDivisions(countryCode, 3, districtCode).then((data) => {
      setWards(data || []);
      setWardLoading(false);
    }).catch(() => setWardLoading(false));
  }, [countryCode, districtCode]);

  // ── FOREIGN: LOAD CITIES ───────────────────────────────────────────────────
  useEffect(() => {
    if (!hasCountry || countryCode === 'VN') return;
    setCityLoading(true);
    addressApi.getDivisions(countryCode, 1).then((data) => {
      setCities(data || []);
      setCityLoading(false);
    }).catch(() => setCityLoading(false));
  }, [countryCode, hasCountry]);

  // ── FOREIGN: SYNC CITY CODE FROM EXTERNAL VALUE (on mount only) ────────────
  useEffect(() => {
    if (!cities.length || cityCode || !value.province) return;
    if (foreignCityMounted.current) return;
    const found = cities.find((c) => c.name === value.province);
    if (found) {
      foreignCityMounted.current = true;
      setCityCode(found.code);
    }
  }, [cities]);

  // ── EMIT CHANGE (VN) ───────────────────────────────────────────────────────
  const emitVietnamChange = useCallback((prov, dist, ward, detail) => {
    onChange({
      country: countryCode,
      province: prov || '',
      district: dist || '',
      ward: ward || '',
      detail: detail ?? value.detail ?? '',
    });
  }, [onChange, countryCode, value.detail]);

  // ── EMIT CHANGE (FOREIGN) ─────────────────────────────────────────────────
  const emitForeignChange = useCallback((city, detail) => {
    onChange({
      country: countryCode,
      province: city || '',
      district: '',
      ward: '',
      detail: detail ?? value.detail ?? '',
    });
  }, [onChange, countryCode, value.detail]);

  // ── HANDLERS (VN) ──────────────────────────────────────────────────────────
  const handleProvinceChange = (code) => {
    setProvinceCode(code);
    setDistrictCode('');
    setWardCode('');
    setDistricts([]);
    setWards([]);
    vnDistrictMounted.current = false;
    vnWardMounted.current = false;
    const prov = provinces.find((p) => p.code === code);
    emitVietnamChange(prov?.name || '', '', '', '');
  };

  const handleDistrictChange = (code) => {
    setDistrictCode(code);
    setWardCode('');
    setWards([]);
    vnWardMounted.current = false;
    const prov = provinces.find((p) => p.code === provinceCode);
    const dist = districts.find((d) => d.code === code);
    emitVietnamChange(prov?.name || '', dist?.name || '', '', '');
  };

  const handleWardChange = (code) => {
    setWardCode(code);
    const prov = provinces.find((p) => p.code === provinceCode);
    const dist = districts.find((d) => d.code === districtCode);
    const ward = wards.find((w) => w.code === code);
    emitVietnamChange(prov?.name || '', dist?.name || '', ward?.name || '', '');
  };

  const handleDetailVietnamChange = (e) => {
    const prov = provinces.find((p) => p.code === provinceCode);
    const dist = districts.find((d) => d.code === districtCode);
    const ward = wards.find((w) => w.code === wardCode);
    emitVietnamChange(prov?.name || '', dist?.name || '', ward?.name || '', e.target.value);
  };

  // ── HANDLERS (FOREIGN) ─────────────────────────────────────────────────────
  const handleCityChange = (code) => {
    setCityCode(code);
    setCityManual('');
    if (code) {
      const city = cities.find((c) => c.code === code);
      emitForeignChange(city?.name || '', '');
    } else {
      emitForeignChange('', '');
    }
  };

  const handleCityManualChange = (e) => {
    const text = e.target.value;
    setCityManual(text);
    setCityCode('');
    emitForeignChange(text, '');
  };

  const handleDetailForeignChange = (e) => {
    const city = cityCode ? cities.find((c) => c.code === cityCode)?.name || '' : cityManual;
    emitForeignChange(city, e.target.value);
  };

  // ── HELPERS ────────────────────────────────────────────────────────────────
  const isVietnam = hasCountry && countryCode === 'VN';
  const isForeign = hasCountry && countryCode !== 'VN';
  const hasDivisionsData = isForeign && (cityLoading || cities.length > 0);

  // ── RENDER: NO COUNTRY ─────────────────────────────────────────────────────
  if (!hasCountry) {
    return (
      <div className={styles.wrapper}>
        <div className={styles.noCountryHint}>Vui lòng chọn Quốc gia trước</div>
      </div>
    );
  }

  // ── RENDER: VIETNAM ────────────────────────────────────────────────────────
  if (isVietnam) {
    return (
      <div className={styles.wrapper}>
        <div className={styles.row}>
          <div className={styles.field}>
            <label className={styles.label}>
              <MapPin size={13} />
              Tỉnh / Thành phố
            </label>
            <div className={styles.selectWrapper}>
              <select
                className={styles.select}
                value={provinceCode}
                onChange={(e) => handleProvinceChange(e.target.value)}
                disabled={provinceLoading}
              >
                <option value="">— Chọn Tỉnh / Thành phố —</option>
                {provinces.map((p) => (
                  <option key={p.code} value={p.code}>{p.name}</option>
                ))}
              </select>
              {provinceCode && (
                <button
                  type="button"
                  className={styles.selectClearBtn}
                  onClick={() => handleProvinceChange('')}
                  title="Bỏ chọn"
                >
                  ✕
                </button>
              )}
              <ChevronDown size={14} className={styles.selectIcon} />
            </div>
          </div>

          <div className={styles.field}>
            <label className={styles.label}>Quận / Huyện</label>
            <div className={styles.selectWrapper}>
              <select
                className={`${styles.select} ${!provinceCode ? styles.selectDisabled : ''}`}
                value={districtCode}
                onChange={(e) => handleDistrictChange(e.target.value)}
                disabled={!provinceCode || districtLoading}
              >
                <option value="">— Chọn Quận / Huyện —</option>
                {districts.map((d) => (
                  <option key={d.code} value={d.code}>{d.name}</option>
                ))}
              </select>
              {districtCode && (
                <button
                  type="button"
                  className={styles.selectClearBtn}
                  onClick={() => handleDistrictChange('')}
                  title="Bỏ chọn"
                >
                  ✕
                </button>
              )}
              <ChevronDown size={14} className={styles.selectIcon} />
            </div>
          </div>

          <div className={styles.field}>
            <label className={styles.label}>Phường / Xã</label>
            <div className={styles.selectWrapper}>
              <select
                className={`${styles.select} ${!districtCode ? styles.selectDisabled : ''}`}
                value={wardCode}
                onChange={(e) => handleWardChange(e.target.value)}
                disabled={!districtCode || wardLoading}
              >
                <option value="">— Bỏ chọn —</option>
                {wards.map((w) => (
                  <option key={w.code} value={w.code}>{w.name}</option>
                ))}
              </select>
              {wardCode && (
                <button
                  type="button"
                  className={styles.selectClearBtn}
                  onClick={() => handleWardChange('')}
                  title="Bỏ chọn"
                >
                  ✕
                </button>
              )}
              <ChevronDown size={14} className={styles.selectIcon} />
            </div>
          </div>
        </div>

        <div className={styles.field}>
          <label className={styles.label}>Địa chỉ chi tiết</label>
          <input
            type="text"
            className={`${styles.input} ${!provinceCode || !districtCode ? styles.inputDisabled : ''}`}
            value={value.detail || ''}
            onChange={handleDetailVietnamChange}
            placeholder={provinceCode && districtCode ? 'Ví dụ: 123 Nguyễn Huệ, Tầng 3' : 'Vui lòng chọn Tỉnh và Quận trước'}
            disabled={!provinceCode || !districtCode}
          />
        </div>
      </div>
    );
  }

  // ── RENDER: FOREIGN ────────────────────────────────────────────────────────
  return (
    <div className={styles.wrapper}>
      {/* City selector: dropdown if data available, otherwise free-text */}
      {hasDivisionsData ? (
        <div className={styles.field}>
          <label className={styles.label}>
            <MapPin size={13} />
            Thành phố / Bang / Khu vực
          </label>
          <div className={styles.selectWrapper}>
            <select
              className={styles.select}
              value={cityCode}
              onChange={(e) => handleCityChange(e.target.value)}
              disabled={cityLoading}
            >
              <option value="">— Chọn Thành phố —</option>
              {cities.map((c) => (
                <option key={c.code} value={c.code}>{c.name}</option>
              ))}
            </select>
            <ChevronDown size={14} className={styles.selectIcon} />
          </div>
        </div>
      ) : (
        <div className={styles.field}>
          <label className={styles.label}>
            <MapPin size={13} />
            Thành phố / Bang / Khu vực
          </label>
          <input
            type="text"
            className={styles.input}
            value={cityManual || value.province || ''}
            onChange={handleCityManualChange}
            placeholder="Nhập tên thành phố"
          />
        </div>
      )}

      {/* Detail address */}
      <div className={styles.field}>
        <label className={styles.label}>Địa chỉ chi tiết</label>
        <input
          type="text"
          className={styles.input}
          value={value.detail || ''}
          onChange={handleDetailForeignChange}
          placeholder="Ví dụ: 123 Main Street, Apt 4B"
        />
      </div>
    </div>
  );
};

export default CascadingAddress;
