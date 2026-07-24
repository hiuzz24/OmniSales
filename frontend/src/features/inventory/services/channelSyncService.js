import channelApi from '../../../api/channelApi';

const SUPPORTED_PLATFORMS = new Set(['LAZADA', 'SHOPIFY', 'TIKTOK']);

const unwrap = (response) => response?.data?.data ?? response?.data ?? response;

const getSyncableChannels = async () => {
  const channelResponse = await channelApi.getAll();
  const channels = unwrap(channelResponse);
  return (Array.isArray(channels) ? channels : [])
    .filter((channel) => SUPPORTED_PLATFORMS.has(channel.platform))
    .filter((channel) => channel.status === 'CONNECTED')
    .filter((channel) => channel.syncEnabled !== false);
};

const channelSyncService = {
  getSyncableChannels,
  syncChannelFromApp: async (channelId) => {
    const response = await channelApi.syncFromApp(channelId);
    return unwrap(response);
  },
  syncAllFromApp: async () => {
    const response = await channelApi.syncAllFromApp();
    return unwrap(response);
  },
  syncChannelFromMarketplace: async (channelId) => {
    const response = await channelApi.syncFromMarketplace(channelId);
    return unwrap(response);
  },
  enqueueSyncFromMarketplace: async (channelId) => {
    const response = await channelApi.enqueueSyncFromMarketplace(channelId);
    return unwrap(response);
  },
  getSyncJob: async (jobId) => {
    const response = await channelApi.getSyncJob(jobId);
    return unwrap(response);
  },
};

export default channelSyncService;
