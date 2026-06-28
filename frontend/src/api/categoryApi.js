import axiosClient from "./axiosClient"

const categoryApi = {
    getAll: async () => {
        const res = await axiosClient.get('/categories');
        return res.data?.data ?? res.data;
    },
    getTree: async () => {
        const res = await axiosClient.get('/categories/tree');
        return res.data?.data ?? res.data;
    },
    getDashboard: async (page = 0, size = 10) => {
        const res = await axiosClient.get('/categories/dashboard', {
            params: { page, size }
        });
        return res.data?.data ?? res.data;
    },
    create: async (data) => {
        const res = await axiosClient.post('/categories', data);
        return res.data?.data ?? res.data;
    },
    update: async (id, data) => {
        const res = await axiosClient.put(`/categories/${id}`, data);
        return res.data?.data ?? res.data;
    },
    delete: async (id) => {
        const res = await axiosClient.delete(`/categories/${id}`);
        return res.data?.data ?? res.data;
    }
}
export default categoryApi;
