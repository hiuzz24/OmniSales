import { useRef, useState } from 'react';

const usePlatformCategoryBrowser = () => {
  const [categoryState, setCategoryState] = useState({});
  const [suggestionState, setSuggestionState] = useState({});
  const [manualBrowser, setManualBrowser] = useState({});
  const [searchValues, setSearchValues] = useState({});
  const categorySearchTimers = useRef({});

  return {
    categoryState,
    setCategoryState,
    suggestionState,
    setSuggestionState,
    manualBrowser,
    setManualBrowser,
    searchValues,
    setSearchValues,
    categorySearchTimers,
  };
};

export default usePlatformCategoryBrowser;
