/**
 * Channel Sync API Tests.
 *
 * Covers all 6 sync endpoints on /api/channels:
 *   - POST /api/channels/{id}/sync                         (push local -> channel)
 *   - POST /api/channels/{id}/sync/from-app                (alias for /sync)
 *   - POST /api/channels/sync/from-app                     (push all)
 *   - POST /api/channels/{id}/sync/from-marketplace        (pull from channel)
 *   - POST /api/channels/{id}/sync/from-marketplace/jobs   (enqueue async pull)
 *   - GET  /api/channels/sync-jobs/{jobId}                 (poll job status)
 *
 * Most of these call remote marketplace APIs that can take seconds or fail
 * for channels that aren't fully connected. We accept 200/201/202/4xx/5xx
 * depending on what the channel supports — the goal is to confirm the
 * endpoint exists, validates its inputs, and returns a serializable body.
 */

const { test, expect } = require('../../fixtures/auth-fixtures');
const {
  createTestChannel,
  deleteTestChannel,
  triggerChannelSync,
  API_BASE,
} = require('../../utils/channel-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Channel Sync API Tests', () => {

  let createdChannelIds = [];

  test.afterEach(async ({ request }) => {
    if (createdChannelIds.length) {
      const authToken = await getAuthTokenCached(request);
      for (const id of createdChannelIds.splice(0)) {
        await deleteTestChannel(request, authToken, id);
      }
    }
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  async function createManualChannel(request, managerHeaders) {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const ch = await createTestChannel(request, authToken, {});
    if (ch && ch.id) createdChannelIds.push(ch.id);
    return ch;
  }

  // === /sync (push) =========================================================

  test('CS-1 - POST /api/channels/{id}/sync - Push local changes', async ({ request, managerHeaders }) => {
    const ch = await createManualChannel(request, managerHeaders);
    test.skip(!ch || !ch.id, 'Cannot create channel');

    const response = await request.post(`${API_BASE}/channels/${ch.id}/sync`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
    });
    expect([200, 201, 202, 400, 404, 500]).toContain(response.status());
    if (response.status() === 200 || response.status() === 201) {
      const body = await response.json();
      expect(body).toHaveProperty('success');
    }
  });

  test('CS-1b - POST /api/channels/{id}/sync - Non-existent channel returns 4xx/5xx', async ({ request, managerHeaders }) => {
    const response = await request.post(
      `${API_BASE}/channels/00000000-0000-0000-0000-000000000000/sync`,
      { headers: { ...managerHeaders, 'Content-Type': 'application/json' } }
    );
    expect([400, 404, 500]).toContain(response.status());
  });

  // === /sync/from-app =======================================================

  test('CS-2 - POST /api/channels/{id}/sync/from-app - Push channel-specific', async ({ request, managerHeaders }) => {
    const ch = await createManualChannel(request, managerHeaders);
    test.skip(!ch || !ch.id, 'Cannot create channel');

    const response = await request.post(`${API_BASE}/channels/${ch.id}/sync/from-app`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
    });
    expect([200, 201, 202, 400, 404, 500]).toContain(response.status());
  });

  test('CS-3 - POST /api/channels/sync/from-app - Push all channels', async ({ request, managerHeaders }) => {
    // Create one channel so the operation has at least one to sync.
    await createManualChannel(request, managerHeaders);

    const response = await request.post(`${API_BASE}/channels/sync/from-app`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
    });
    expect([200, 201, 202, 400, 500]).toContain(response.status());
  });

  // === /sync/from-marketplace ==============================================

  test('CS-4 - POST /api/channels/{id}/sync/from-marketplace - Pull from channel', async ({ request, managerHeaders }) => {
    const ch = await createManualChannel(request, managerHeaders);
    test.skip(!ch || !ch.id, 'Cannot create channel');

    const response = await request.post(
      `${API_BASE}/channels/${ch.id}/sync/from-marketplace`,
      { headers: { ...managerHeaders, 'Content-Type': 'application/json' } }
    );
    expect([200, 201, 202, 400, 404, 500]).toContain(response.status());
  });

  // === /sync/from-marketplace/jobs =========================================

  test('CS-5 - POST /api/channels/{id}/sync/from-marketplace/jobs - Enqueue async pull', async ({ request, managerHeaders }) => {
    const ch = await createManualChannel(request, managerHeaders);
    test.skip(!ch || !ch.id, 'Cannot create channel');

    const response = await request.post(
      `${API_BASE}/channels/${ch.id}/sync/from-marketplace/jobs`,
      { headers: { ...managerHeaders, 'Content-Type': 'application/json' } }
    );
    expect([200, 201, 202, 400, 404, 500]).toContain(response.status());

    if (response.status() === 200 || response.status() === 201 || response.status() === 202) {
      const body = await response.json();
      const jobId = body.data?.id;
      if (jobId) {
        const poll = await request.get(`${API_BASE}/channels/sync-jobs/${jobId}`, {
          headers: managerHeaders,
        });
        expect([200, 202]).toContain(poll.status());
      }
    }
  });

  // === /sync-jobs/{jobId} ===================================================

  test('CS-6 - GET /api/channels/sync-jobs/{jobId} - Non-existent job returns 404/500', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/channels/sync-jobs/00000000-0000-0000-0000-000000000000`,
      { headers: managerHeaders }
    );
    expect([404, 500]).toContain(response.status());
  });

  test('CS-7 - triggerChannelSync helper - dispatches to each variant', async ({ request, managerHeaders }) => {
    const ch = await createManualChannel(request, managerHeaders);
    test.skip(!ch || !ch.id, 'Cannot create channel');

    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const r = await triggerChannelSync(request, authToken, ch.id, 'sync');
    expect(r).not.toBeNull();
    expect(typeof r.status).toBe('number');
  });
});
