package fu.osms.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Payload for syncing warehouse contact/address info to all connected marketplaces.
 * These fields are required by Lazada and TikTok when updating warehouse info.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseMarketplaceSyncRequest {

    /** Warehouse display name — sent to all platforms. */
    @NotBlank(message = "Tên kho là bắt buộc")
    @Size(max = 200)
    private String name;

    /** Street address / detail address — required by Lazada & TikTok. */
    @NotBlank(message = "Địa chỉ chi tiết là bắt buộc")
    @Size(max = 500)
    private String address;

    /** Contact person full name — required by Lazada & TikTok. */
    @NotBlank(message = "Tên người liên hệ là bắt buộc")
    @Size(max = 100)
    private String contactName;

    /**
     * Contact phone number — required by all platforms.
     * Lazada: must be a valid local number.
     * TikTok: international format preferred.
     * Shopify: stored on location.
     */
    @NotBlank(message = "Số điện thoại là bắt buộc")
    @Pattern(regexp = "^\\+?[0-9\\s\\-]{7,20}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    /** Email — optional, used by Lazada warehouse update. */
    @Size(max = 100)
    private String email;

    /**
     * Country code for Shopify LocationEditAddressInput — defaults to "VN".
     * ISO 3166-1 alpha-2.
     */
    @Builder.Default
    private String countryCode = "VN";

    /**
     * City — used by Shopify locationEdit address.
     */
    @Size(max = 100)
    private String city;

    /**
     * Province/state code for Shopify — e.g. "HN" for Hanoi.
     */
    @Size(max = 50)
    private String province;

    /**
     * Zip/postal code — optional for Shopify.
     */
    @Size(max = 20)
    private String zip;
}
