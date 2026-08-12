import { useCallback, useEffect, useRef, useState } from 'react';
import useDebounce from '../../../shared/hooks/useDebounce';

const DEFAULT_PAGE_SIZE = 50;

/**
 * usePagedVariants
 *
 * Lazy-load (infinite scroll) một danh sách variant phân trang từ backend.
 *
 * Props:
 *   fetcher   {async ({ page, size, keyword }) => Promise<any>} — trả về
 *             hoặc { content, totalPages } (PageResponse) hoặc mảng nguyên.
 *   pageSize  {number}  — kích thước mỗi trang (mặc định 200).
 *   enabled   {boolean} — chỉ load khi true (ví dụ modal đang mở).
 *
 * Returns:
 *   keyword, setKeyword, items, loading, loadingMore, hasMore, error,
 *   open(), close(), loadMore(), loadAll()
 */
export default function usePagedVariants({ fetcher, pageSize = DEFAULT_PAGE_SIZE, enabled = true }) {
  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebounce(keyword, 300);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [totalItems, setTotalItems] = useState(0);
  const [error, setError] = useState(null);
  const pageRef = useRef(0);
  const seqRef = useRef(0);
  const keywordRef = useRef('');
  const activeRef = useRef(false);

  useEffect(() => {
    keywordRef.current = debouncedKeyword.trim();
  }, [debouncedKeyword]);

  const loadPage = useCallback(async (page, seq, keywordValue) => {
    if (seq !== seqRef.current) return false;
    let response;
    try {
      response = await fetcher({ page, size: pageSize, keyword: keywordValue });
    } catch {
      if (seq === seqRef.current) {
        setError('Không thể tải danh sách sản phẩm. Vui lòng thử lại.');
        setHasMore(false);
      }
      return false;
    }
    if (seq !== seqRef.current) return false;
    const data = response?.data ?? response;
    const content = Array.isArray(data) ? data : data?.content ?? [];
    const more = Array.isArray(data)
      ? content.length >= pageSize
      : data?.totalPages != null
        ? page + 1 < data.totalPages
        : content.length >= pageSize;
    setItems((prev) => (page === 0 ? content : [...prev, ...content]));
    setHasMore(more);
    setTotalItems(Array.isArray(data) ? content.length : data?.totalElements ?? content.length);
    pageRef.current = page + 1;
    setError(null);
    return more;
  }, [fetcher, pageSize]);

  const open = useCallback(() => {
    if (!enabled) return;
    activeRef.current = true;
    seqRef.current += 1;
    const seq = seqRef.current;
    pageRef.current = 0;
    setItems([]);
    setHasMore(false);
    setError(null);
    setLoading(true);
    loadPage(0, seq, keywordRef.current).finally(() => {
      if (seq === seqRef.current) setLoading(false);
    });
  }, [enabled, loadPage]);

  const close = useCallback(() => {
    activeRef.current = false;
    seqRef.current += 1;
    setItems([]);
    setHasMore(false);
    setTotalItems(0);
    setLoading(false);
    setLoadingMore(false);
    setError(null);
  }, []);

  const loadMore = useCallback(() => {
    if (loading || loadingMore || !hasMore) return;
    const seq = seqRef.current;
    setLoadingMore(true);
    loadPage(pageRef.current, seq, keywordRef.current).finally(() => {
      if (seq === seqRef.current) setLoadingMore(false);
    });
  }, [loading, loadingMore, hasMore, loadPage]);

  const loadAll = useCallback(async (keywordOverride) => {
    const keywordValue = keywordOverride ?? keywordRef.current;
    seqRef.current += 1;
    const seq = seqRef.current;
    let page = 0;
    let more = true;
    while (more) {
      if (seq !== seqRef.current) return;
      more = await loadPage(page, seq, keywordValue);
      page += 1;
    }
    if (seq === seqRef.current) setHasMore(false);
  }, [loadPage]);

  // Reload page 0 khi keyword thay đổi (tìm kiếm server-side).
  // Lần load đầu khi mở modal do `open()` đảm nhận nên không đặt `enabled`
  // vào deps để tránh load 2 lần khi modal mở.
  useEffect(() => {
    if (!enabled || !activeRef.current) return;
    const timer = window.setTimeout(() => {
      seqRef.current += 1;
      const seq = seqRef.current;
      pageRef.current = 0;
      setLoading(true);
      loadPage(0, seq, debouncedKeyword.trim()).finally(() => {
        if (seq === seqRef.current) setLoading(false);
      });
    }, 0);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedKeyword]);

  return { keyword, setKeyword, items, totalItems, loading, loadingMore, hasMore, error, open, close, loadMore, loadAll };
}
