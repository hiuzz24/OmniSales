import axiosClient from './axiosClient';

const addressApi = {
  getCountries: async () => {
    const response = await axiosClient.get('/address/countries');
    return response.data.data;
  },

  getDivisions: async (countryCode, level, parentCode = '') => {
    const params = { country: countryCode, level };
    if (parentCode) params.parent = parentCode;
    const response = await axiosClient.get('/address/divisions', { params });
    return response.data.data;
  },
};

export default addressApi;
