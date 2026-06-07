import axiosClient from './axiosClient';

const ACCESS_TOKEN_KEY = 'osms_access_token';

export const getAccessToken = () => localStorage.getItem(ACCESS_TOKEN_KEY);

export const setAccessToken = (token) => {
  localStorage.setItem(ACCESS_TOKEN_KEY, token);
};

export const clearAccessToken = () => {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
};

axiosClient.interceptors.request.use(
  (config) => {
    const token = getAccessToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

axiosClient.interceptors.response.use(
  (response) => response.data?.data ?? response.data,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const refreshed = await axiosClient.post('/auth/refresh');
        setAccessToken(refreshed.accessToken);
        originalRequest.headers.Authorization = `Bearer ${refreshed.accessToken}`;
        return axiosClient(originalRequest);
      } catch {
        clearAccessToken();
        window.location.href = '/login';
      }
    }

    return Promise.reject(error);
  }
);
