export const TIKTOK_TITLE_MIN_LENGTH = 25;
export const TIKTOK_TITLE_MAX_LENGTH = 255;

const normalize = (value) => {
  const normalized = String(value || '').trim().replace(/\s+/g, ' ');
  return normalized || '';
};

const truncateAtWord = (value, maxLength) => {
  const normalized = normalize(value);
  if (normalized.length <= maxLength) return normalized;
  const boundary = normalized.lastIndexOf(' ', maxLength);
  return normalized.slice(0, boundary > 0 ? boundary : maxLength).trim();
};

export const resolveTikTokProductTitle = ({
  listingTitle,
  productName,
  categoryName,
  brandName,
  description,
}) => {
  const override = normalize(listingTitle);
  if (override) {
    return {
      title: override,
      overridden: true,
      valid: override.length >= TIKTOK_TITLE_MIN_LENGTH && override.length <= TIKTOK_TITLE_MAX_LENGTH,
    };
  }

  const parts = [];
  [productName, categoryName, brandName].forEach((value) => {
    const part = normalize(value);
    if (part && !parts.some((existing) => existing.toLocaleLowerCase() === part.toLocaleLowerCase())) {
      parts.push(part);
    }
  });

  let title = parts.join(' - ');
  const normalizedDescription = normalize(description);
  if (title.length < TIKTOK_TITLE_MIN_LENGTH && normalizedDescription) {
    title = title ? `${title} - ${normalizedDescription}` : normalizedDescription;
  }
  title = truncateAtWord(title, TIKTOK_TITLE_MAX_LENGTH);

  return {
    title,
    overridden: false,
    valid: title.length >= TIKTOK_TITLE_MIN_LENGTH && title.length <= TIKTOK_TITLE_MAX_LENGTH,
  };
};
