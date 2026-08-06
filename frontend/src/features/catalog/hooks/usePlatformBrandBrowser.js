import { useRef, useState } from 'react';

const usePlatformBrandBrowser = () => {
  const [brandState, setBrandState] = useState({});
  const [brandPages, setBrandPages] = useState({});
  const [brandPageTokens, setBrandPageTokens] = useState({});
  const [brandPageHistory, setBrandPageHistory] = useState({});
  const [brandSearchValues, setBrandSearchValues] = useState({});
  const brandSearchTimers = useRef({});

  return {
    brandState,
    setBrandState,
    brandPages,
    setBrandPages,
    brandPageTokens,
    setBrandPageTokens,
    brandPageHistory,
    setBrandPageHistory,
    brandSearchValues,
    setBrandSearchValues,
    brandSearchTimers,
  };
};

export default usePlatformBrandBrowser;
