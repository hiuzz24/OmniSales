package fu.osms.order.enums;

public enum ShopifyCancelReason {
    CUSTOMER("customer"),
    FRAUD("fraud"),
    INVENTORY("inventory"),
    STAFF("staff"),
    DECLINED("declined"),
    OTHER("other");

    private final String shopifyValue;

    ShopifyCancelReason(String shopifyValue) {
        this.shopifyValue = shopifyValue;
    }

    public String getShopifyValue() {
        return shopifyValue;
    }
}
