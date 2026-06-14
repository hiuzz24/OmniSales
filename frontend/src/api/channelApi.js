import axiosClient from "./axiosClient"

const channelApi = {
    getAll: async () => {
        const data = await axiosClient.get('/channels');
        return data;
    }
}
export default channelApi;
