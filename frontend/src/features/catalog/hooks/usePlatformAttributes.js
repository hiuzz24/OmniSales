import { useState } from 'react';

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
