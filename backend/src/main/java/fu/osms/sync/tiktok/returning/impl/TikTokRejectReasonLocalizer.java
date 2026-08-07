package fu.osms.sync.tiktok.returning.impl;

import java.util.Locale;

final class TikTokRejectReasonLocalizer {

    String localize(String reasonCode, String platformLabel) {
        String source = ((reasonCode == null ? "" : reasonCode) + " "
                + (platformLabel == null ? "" : platformLabel))
                .toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (source.contains("REVERSE_REJECT_REQUEST_REASON_1")) return "Lý do trả hàng của khách không hợp lệ";
        if (source.contains("REVERSE_REJECT_REQUEST_REASON_2")) return "Sản phẩm không áp dụng trả hàng do thay đổi nhu cầu";
        if (source.contains("REVERSE_REJECT_REQUEST_REASON_3")) return "Sản phẩm không đủ điều kiện trả hàng";
        if (source.contains("REVERSE_REJECT_REQUEST_REASON_4")) return "Đơn hàng vẫn đang được giao đúng lịch";
        if (source.contains("REVERSE_REJECT_REQUEST_REASON_5") || source.contains("REACHED_AN_AGREEMENT")) return "Đã đạt thỏa thuận với khách hàng";
        if (source.contains("REVERSE_REJECT_RETURN_PARCEL_REASON_1")) return "Sản phẩm gửi trả không phải sản phẩm đã bán";
        if (source.contains("REVERSE_REJECT_RETURN_PARCEL_REASON_2")) return "Sản phẩm gửi trả không đủ điều kiện nhập lại";
        if (source.contains("REVERSE_REJECT_RETURN_PARCEL_REASON_3")) return "Kiện trả hàng bị thiếu sản phẩm hoặc phụ kiện";
        if (source.contains("REVERSE_REJECT_RETURN_PARCEL_REASON_4")) return "Chưa nhận được kiện hàng trả về";
        if (source.contains("REVERSE_REJECT_RETURN_PARCEL_REASON_5")) return "Sản phẩm trả về đã hư hỏng hoặc qua sử dụng";
        if (source.contains("FINAL_SALE")) return "Sản phẩm không áp dụng trả hàng";
        if (source.contains("RETURN_PERIOD") || source.contains("EXPIRED")) return "Đã hết thời hạn trả hàng";
        if (source.contains("NOT_ELIGIBLE") || source.contains("INELIGIBLE")) return "Sản phẩm không đủ điều kiện trả hàng";
        if (source.contains("INSUFFICIENT_EVIDENCE") || source.contains("INSUFFICIENT_PROOF")
                || source.contains("PROOF_NOT_SUFFICIENT") || source.contains("LACK_OF_EVIDENCE")
                || source.contains("REASON_IS_UNCLEAR")) return "Lý do chưa rõ ràng hoặc bằng chứng không đầy đủ";
        if (source.contains("WRONG_RETURN") || source.contains("WRONG_ITEM")) return "Sản phẩm khách gửi trả không đúng";
        if (source.contains("DAMAGED") || source.contains("USED")) return "Sản phẩm trả về không đúng tình trạng yêu cầu";
        if (source.contains("NOT_RECEIVED")) return "Chưa nhận được hàng trả về";
        if (source.contains("CHANGE_OF_MIND") || source.contains("NO_LONGER_NEEDED")) return "Lý do trả hàng của khách không phù hợp";
        if (source.contains("ITEM_IS_CORRECT")) return "Sản phẩm đã giao là chính xác";
        if (source.contains("PRODUCT_FUNCTIONS_WELL") || source.contains("WRONG_WAY")) return "Sản phẩm hoạt động bình thường hoặc khách sử dụng chưa đúng cách";
        if (source.contains("PRODUCTS_WILL_BE_SENT_SEPARATELY")) return "Các sản phẩm được gửi thành nhiều kiện riêng";
        if (source.contains("PACKAGE_HAS_NOT_EXCEEDED_ESTIMATED_DELIVERY_TIME")) return "Đơn hàng vẫn đang trong thời gian giao dự kiến";
        if (source.contains("UNABLE_TO_CHANGE_ADDRESS")) return "Không thể thay đổi địa chỉ giao hàng";
        if (source.contains("INCORRECT_ADDRESS")) return "Địa chỉ không chính xác thuộc trách nhiệm khách hàng";
        if (source.contains("NEED_TO_APPLY_FOR_REFUND")) return "Khách cần gửi yêu cầu hoàn tiền và trả hàng đúng quy trình";
        if (source.contains("OTHER")) return "Lý do khác";
        return "Lý do từ chối khác do TikTok cung cấp";
    }
}
