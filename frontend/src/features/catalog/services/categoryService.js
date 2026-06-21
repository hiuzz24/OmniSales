import axiosClient from '../../../api/axiosClient';

const categoryService = {
    getAll: async () => {
        const response = await axiosClient.get('/categories');
        const data = response.data?.data || response.data || response;
        if (Array.isArray(data)) return data;
        if (data.content) return data.content;
        if (Array.isArray(data.data)) return data.data;
        return [];
    },
};

export default categoryService;
