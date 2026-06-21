import axiosClient from './axiosClient';

const orderLogApi = {
  getAll: (params = {}) => axiosClient.get('/audit-logs', { params }),
  getByEntity: (entityType, entityId, params) =>
    axiosClient.get(`/audit-logs/entity/${entityType}/${entityId}`, { params }),
  getByActor: (actorId, params) =>
    axiosClient.get(`/audit-logs/actor/${actorId}`, { params }),
  getByDateRange: (from, to, params) =>
    axiosClient.get('/audit-logs/date-range', { params: { from, to, ...params } }),
};

export default orderLogApi;
