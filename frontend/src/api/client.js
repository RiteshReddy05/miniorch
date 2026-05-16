import axios from 'axios';

const STORAGE_KEY = 'miniorch.token';
const UNAUTHORIZED_EVENT = 'miniorch:unauthorized';

const client = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
});

client.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(STORAGE_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const hadToken = !!sessionStorage.getItem(STORAGE_KEY);
    if (status === 401 && hadToken) {
      sessionStorage.removeItem(STORAGE_KEY);
      window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
    }
    return Promise.reject(error);
  }
);

export default client;
