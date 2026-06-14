import axiosClient from "./axiosClient"

const productApi = {
    getAll: async (page, size, keyword, status, platform) => {
        const params = { page, size };
        if (keyword) params.keyword = keyword;
        if (status) params.status = status;
        if (platform) params.platform = platform;
        const data = await axiosClient.get('/products', { params });
        return data;
    },

    create: async (data) => {
        const response = await axiosClient.post('/products', data);
        return response;
    }
}
export default productApi;
