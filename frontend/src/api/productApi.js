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
    },

    getById: async (id) => {
        const response = await axiosClient.get(`/products/${id}`);
        console.log(response);
        
        return response;
    },

    update: async (id, data) => {
        const response = await axiosClient.put(`/products/${id}`, data);
        return response;
    },

    delete: async (id) => {
        const response = await axiosClient.delete(`/products/${id}/delete`); 
        return response;
    },

    importExcel: async (file) => {
        const formData = new FormData();
        formData.append('file', file);
        const response = await axiosClient.post('/products/import', formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
        });
        return response;
    }
}
export default productApi;
