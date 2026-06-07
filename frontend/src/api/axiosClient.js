import axios from 'axios';

const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: Number(import.meta.env.VITE_API_TIMEOUT) || 10000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

export default axiosClient;
