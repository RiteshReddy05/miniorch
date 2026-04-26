import axios from 'axios';

const client = axios.create({
  baseURL: '/api/v1',
  timeout: 5000,
});

export default client;
