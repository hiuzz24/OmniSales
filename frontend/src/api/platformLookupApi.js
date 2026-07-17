import axiosClient from './axiosClient';

const platformLookupApi = {
  getCategories: (platform, params) => axiosClient.get(`/platform-lookups/${platform}/categories`, { params }),
  getCategorySuggestions: (platform, payload) => axiosClient.post(`/platform-lookups/${platform}/category-suggestions`, payload),
  getAttributes: (platform, categoryId, params) => axiosClient.get(`/platform-lookups/${platform}/categories/${categoryId}/attributes`, { params }),
  getBrands: (platform, params) => axiosClient.get(`/platform-lookups/${platform}/brands`, { params }),
  clearCache: (platform, channelId) => axiosClient.put(`/platform-lookups/${platform}/cache`, null, { params: { channelId } }),
};

export default platformLookupApi;
