import { useState } from 'react';

/** Quản lý trạng thái attribute platform và tiến trình tải bảng size. */
const usePlatformAttributes = () => {
  const [attributeState, setAttributeState] = useState({});
  const [sizeChartUploading, setSizeChartUploading] = useState({});

  return {
    attributeState,
    setAttributeState,
    sizeChartUploading,
    setSizeChartUploading,
  };
};

export default usePlatformAttributes;
