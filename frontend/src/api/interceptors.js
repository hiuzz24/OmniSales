import authApi from './authApi';
import axiosClient from './axiosClient';

const ACCESS_TOKEN_KEY = 'osms_access_token';

export const getAccessToken = () => {
  return localStorage.getItem(ACCESS_TOKEN_KEY) || sessionStorage.getItem(ACCESS_TOKEN_KEY);
};

export const setAccessToken = (token, rememberMe) => {
  if (rememberMe === undefined) {
    if (sessionStorage.getItem(ACCESS_TOKEN_KEY)) {
      sessionStorage.setItem(ACCESS_TOKEN_KEY, token);
    } else {
      localStorage.setItem(ACCESS_TOKEN_KEY, token);
    }
  } else if (rememberMe) {
    localStorage.setItem(ACCESS_TOKEN_KEY, token);
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  } else {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, token);
    localStorage.removeItem(ACCESS_TOKEN_KEY);
  }
};

export const clearAccessToken = () => {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
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
    const isAuthEndpoint = originalRequest.url.includes('/auth/login') || originalRequest.url.includes('/auth/refresh');

    if (error.response?.status === 401 && !originalRequest._retry && !isAuthEndpoint) {
      originalRequest._retry = true;

      try {
        console.log("send refresh");
        const refreshed = await authApi.refreshToken;
        setAccessToken(refreshed.accessToken);
        originalRequest.headers.Authorization = `Bearer ${refreshed.accessToken}`;
        return axiosClient(originalRequest);
      } catch {
        clearAccessToken();
        localStorage.removeItem('osms_user');
        sessionStorage.removeItem('osms_user');
        window.location.href = '/login';
      }
    }

    return Promise.reject(error);
  }
);
