import axiosClient from "./axiosClient"

const categoryApi = {
    getAll: async () => {
        const res = await axiosClient.get('/categories');
        return res.data?.data ?? res.data;
    },
    getTree: async () => {
        const res = await axiosClient.get('/categories/tree');
        return res.data?.data ?? res.data;
    }
}
export default categoryApi;
