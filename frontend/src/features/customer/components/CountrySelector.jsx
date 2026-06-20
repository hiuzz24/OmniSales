import { useState, useEffect } from 'react';
import { Globe, ChevronDown } from 'lucide-react';
import addressApi from '../../../api/addressApi';
import styles from './CountrySelector.module.css';

const CountrySelector = ({ value, onChange }) => {
  const [countries, setCountries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    addressApi.getCountries().then((data) => {
      setCountries(data || []);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  const selected = countries.find((c) => c.code === value);

  const handleSelect = (country) => {
    onChange(country.code);
    setOpen(false);
  };

  if (loading) {
    return (
      <div className={styles.wrapper}>
        <div className={styles.skeleton} />
      </div>
    );
  }

  return (
    <div className={styles.wrapper}>
      <label className={styles.label}>
        <Globe size={13} />
        Quốc gia
      </label>
      <div className={styles.selectWrapper}>
        <button
          type="button"
          className={styles.trigger}
          onClick={() => setOpen((o) => !o)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
        >
          <span className={styles.triggerContent}>
            {selected ? (
              <>
                <span className={styles.flag}>{selected.flagEmoji}</span>
                <span>{selected.name}</span>
              </>
            ) : (
              <span className={styles.placeholder}>— Chọn Quốc gia —</span>
            )}
          </span>
          <ChevronDown size={14} className={`${styles.icon} ${open ? styles.iconOpen : ''}`} />
        </button>

        {open && (
          <div className={styles.dropdown}>
            <div className={styles.dropdownList}>
              {countries.map((country) => (
                <button
                  key={country.code}
                  type="button"
                  className={`${styles.option} ${country.code === value ? styles.optionSelected : ''}`}
                  onClick={() => handleSelect(country)}
                >
                  <span className={styles.flag}>{country.flagEmoji}</span>
                  <span>{country.name}</span>
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default CountrySelector;
