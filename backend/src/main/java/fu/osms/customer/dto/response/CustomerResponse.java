package fu.osms.customer.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerResponse {

    private UUID id;
    private String code;
    private String fullName;
    private String gender;
    private LocalDate birth;
    private String phone;
    private String email;
    private Map<String, Object> address;
    private String notes;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long orderCount;
    private BigDecimal totalSpent;
}
