package fu.osms.customer.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.customer.dto.request.CustomerRequest;
import fu.osms.customer.dto.response.CustomerResponse;
import fu.osms.customer.dto.response.CustomerStatsResponse;
import fu.osms.customer.dto.response.PageWithOrderCustomersResponse;
import fu.osms.customer.entity.Customer;

import java.util.UUID;

public interface CustomerService {

    CustomerResponse create(CustomerRequest request);

    CustomerResponse getById(UUID id);

    PageResponse<CustomerResponse> getAll(int page, int size, String search, String status, String gender);

    CustomerStatsResponse getStats();

    PageWithOrderCustomersResponse getPageWithOrderCustomers(int page, int size, String search, String status, String gender);

    CustomerResponse update(UUID id, CustomerRequest request);

    void delete(UUID id);

    /**
     * Sync KH ảo từ đơn hàng: quét tất cả order có customer_id IS NULL và gắn KH
     * vào từ buyer_name + buyer_phone. Dùng 1 lần khi boot để backfill.
     */
    int syncCustomersFromOrders();

    /**
     * Tìm KH theo fullName + phone, nếu không có thì tạo mới.
     * Dùng khi INSERT order mới có customer_id NULL.
     */
    Customer findOrCreateFromBuyer(String buyerName, String buyerPhone);
}
