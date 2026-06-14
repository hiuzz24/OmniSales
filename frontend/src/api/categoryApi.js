import axiosClient from "./axiosClient"

const categoryApi = {
    getAll: async () => {
        const data = await axiosClient.get('/categories');
        return data;
    }
}
export default categoryApi;
