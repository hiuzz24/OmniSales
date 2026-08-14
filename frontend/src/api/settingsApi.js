import axiosClient from './axiosClient';
import './interceptors';

const unwrap = (res) => res.data?.data ?? res.data;

const settingsApi = {
  getSettings: async () => {
    const res = await axiosClient.get('/admin/settings');
    return unwrap(res);
  },

  getPublicPreferences: async () => {
    const res = await axiosClient.get('/system/preferences');
    return unwrap(res);
  },

  updateSetting: async (key, value) => {
    const res = await axiosClient.put(`/admin/settings/${key}`, { value });
    return unwrap(res);
  },

  updateSettingsBatch: async (settingsList) => {
    const res = await axiosClient.post('/admin/settings/batch', settingsList);
    return unwrap(res);
  }
};

export default settingsApi;
