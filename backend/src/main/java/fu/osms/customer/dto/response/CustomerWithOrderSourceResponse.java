package fu.osms.customer.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CustomerWithOrderSourceResponse extends CustomerResponse {

    private Boolean fromOrders = false;

    public static CustomerWithOrderSourceResponse from(CustomerResponse base, boolean fromOrders) {
        CustomerWithOrderSourceResponse r = new CustomerWithOrderSourceResponse();
        r.setId(base.getId());
        r.setCode(base.getCode());
        r.setFullName(base.getFullName());
        r.setGender(base.getGender());
        r.setBirth(base.getBirth());
        r.setPhone(base.getPhone());
        r.setEmail(base.getEmail());
        r.setAddress(base.getAddress());
        r.setNotes(base.getNotes());
        r.setIsActive(base.getIsActive());
        r.setCreatedAt(base.getCreatedAt());
        r.setUpdatedAt(base.getUpdatedAt());
        r.setOrderCount(base.getOrderCount());
        r.setTotalSpent(base.getTotalSpent());
        r.setFromOrders(fromOrders);
        return r;
    }
}
