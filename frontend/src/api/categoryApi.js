import axiosClient from "./axiosClient"

const categoryApi = {
    getAll: async () => {
        const data = await axiosClient.get('/categories');
        return data;
    },
    getTree: async () => {
        const data = await axiosClient.get('/categories/tree');
        return data;
    }
}
export default categoryApi;
