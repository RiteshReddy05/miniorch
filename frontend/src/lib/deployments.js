import client from '../api/client.js';

const CREATE_TIMEOUT_MS = 60_000;

export function listDeployments() {
  return client.get('/deployments').then((r) => r.data);
}

export function getDeployment(id) {
  return client.get(`/deployments/${id}`).then((r) => r.data);
}

export function getDeploymentEvents(id) {
  return client.get(`/deployments/${id}/events`).then((r) => r.data);
}

export function createDeployment(body) {
  return client.post('/deployments', body, { timeout: CREATE_TIMEOUT_MS }).then((r) => r.data);
}

export function scaleDeployment(id, desiredReplicas) {
  return client.patch(`/deployments/${id}/scale`, { desiredReplicas }).then((r) => r.data);
}

export function resetReplica(id, replicaIndex) {
  return client.post(`/deployments/${id}/replicas/${replicaIndex}/reset`).then((r) => r.data);
}

export function deleteDeployment(id) {
  return client.delete(`/deployments/${id}`);
}
