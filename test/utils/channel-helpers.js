/**
 * Helper utilities for channel API tests.
 * Channels support DELETE /api/channels/{id} (soft delete).
 */

const { TEST_EMAIL, TEST_PASSWORD, API_BASE } = require('./env-config');

async function getAuthToken(request) {
  for (let i = 0; i < 5; i++) {
    const resp = await request.post(`${API_BASE}/auth/login`, {
      data: { email: TEST_EMAIL, password: TEST_PASSWORD },
    });
    if (resp.status() === 200) {
      const body = await resp.json();
      return body.data.accessToken;
    }
    if (resp.status() === 429 || resp.status() === 401 || resp.status() === 403) {
      await new Promise(r => setTimeout(r, 3000 * (i + 1)));
      continue;
    }
    throw new Error(`Login failed with status ${resp.status()}`);
  }
  throw new Error('Login failed after retries');
}

/**
 * Create a test channel via API.
 * Returns the created channel object with id.
 */
async function createTestChannel(request, token, overrides = {}) {
  const timestamp = Date.now();

  const defaultChannel = {
    platform: overrides.platform || 'MANUAL',
    displayName: overrides.displayName || `TestMC_${timestamp}_${Math.random().toString(36).slice(2, 8)}`,
    region: overrides.region || 'VN',
    commissionRate: overrides.commissionRate !== undefined ? overrides.commissionRate : 5.0,
    syncEnabled: overrides.syncEnabled !== undefined ? overrides.syncEnabled : true,
    metadata: overrides.metadata || {},
  };

  const response = await request.post(`${API_BASE}/channels`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data: defaultChannel,
  });

  if (response.status() !== 200 && response.status() !== 201) {
    throw new Error(`Create channel failed with status ${response.status()}`);
  }

  const body = await response.json();
  return body.data;
}

/**
 * Delete a test channel (soft delete via DELETE endpoint).
 */
async function deleteTestChannel(request, token, channelId) {
  if (!channelId) return;
  try {
    await request.delete(`${API_BASE}/channels/${channelId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch (e) {
    // best-effort
  }
}

/**
 * Trigger one of the sync endpoints on a channel.
 * variant: 'sync' | 'sync-from-app' | 'sync-from-marketplace' | 'sync-from-marketplace-jobs' | 'sync-all-from-app'
 * Returns the raw response or null on failure/timeout.
 */
async function triggerChannelSync(request, token, channelId, variant) {
  if (!channelId && variant !== 'sync-all-from-app') return null;
  let url;
  let method = 'POST';
  if (variant === 'sync') {
    url = `${API_BASE}/channels/${channelId}/sync`;
  } else if (variant === 'sync-from-app') {
    url = `${API_BASE}/channels/${channelId}/sync/from-app`;
  } else if (variant === 'sync-from-marketplace') {
    url = `${API_BASE}/channels/${channelId}/sync/from-marketplace`;
  } else if (variant === 'sync-from-marketplace-jobs') {
    url = `${API_BASE}/channels/${channelId}/sync/from-marketplace/jobs`;
  } else if (variant === 'sync-all-from-app') {
    url = `${API_BASE}/channels/sync/from-app`;
  } else if (variant === 'get-sync-job') {
    method = 'GET';
    url = `${API_BASE}/channels/sync-jobs/${channelId}`;
  } else {
    return null;
  }
  try {
    const opts = {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    };
    const resp = await (method === 'GET' ? request.get(url, opts) : request.post(url, opts));
    return {
      status: resp.status(),
      body: (await resp.json().catch(() => null)) || null,
    };
  } catch (_) {
    // ignore
  }
  return null;
}

module.exports = {
  API_BASE,
  TEST_EMAIL,
  TEST_PASSWORD,
  getAuthToken,
  createTestChannel,
  deleteTestChannel,
  triggerChannelSync,
};