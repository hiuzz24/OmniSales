import userApi from '../../../api/userApi';

const userService = {
  /**
   * Get current user's profile.
   */
  getMyProfile: async () => {
    const data = await userApi.getMyProfile();
    return data;
  },

  /**
   * Update current user's profile.
   * @param {Object} profileData - { fullName, phone, avatarUrl }
   */
  updateMyProfile: async (profileData) => {
    const data = await userApi.updateMyProfile(profileData);
    return data;
  },

  /**
   * Get user profile by ID.
   */
  getProfileById: async (id) => {
    const data = await userApi.getProfileById(id);
    return data;
  },

  /**
   * Change current user's password.
   * @param {Object} data - { oldPassword, newPassword, confirmPassword }
   */
  changeMyPassword: async (data) => {
    const result = await userApi.changeMyPassword(data);
    return result;
  },
};

export default userService;
