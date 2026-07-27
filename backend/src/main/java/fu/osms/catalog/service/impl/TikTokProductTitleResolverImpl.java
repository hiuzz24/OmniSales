package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.TikTokProductTitleInput;
import fu.osms.catalog.dto.TikTokProductTitleResult;
import fu.osms.catalog.service.TikTokProductTitleResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TikTokProductTitleResolverImpl implements TikTokProductTitleResolver {

    private static final int MIN_LENGTH = 25;
    private static final int MAX_LENGTH = 255;
    private static final String SEPARATOR = " - ";

    @Override
    public TikTokProductTitleResult resolve(TikTokProductTitleInput input) {
        TikTokProductTitleInput safeInput = input == null
                ? new TikTokProductTitleInput(null, null, null, null, null)
                : input;
        String override = normalize(safeInput.listingTitle());
        boolean overridden = override != null;
        String title = overridden ? override : automaticTitle(safeInput);
        String validationError = validationError(title);
        return new TikTokProductTitleResult(title, overridden, validationError == null, validationError);
    }

    private String automaticTitle(TikTokProductTitleInput input) {
        List<String> parts = new ArrayList<>();
        addDistinct(parts, input.productName());
        addDistinct(parts, input.categoryName());
        addDistinct(parts, input.brandName());

        String title = String.join(SEPARATOR, parts);
        String description = normalize(input.description());
        if (title.length() < MIN_LENGTH && description != null) {
            title = title.isBlank() ? description : title + SEPARATOR + description;
        }
        return truncateAtWord(title, MAX_LENGTH);
    }

    private void addDistinct(List<String> parts, String candidate) {
        String normalized = normalize(candidate);
        if (normalized == null) return;
        boolean duplicate = parts.stream().anyMatch(value -> value.equalsIgnoreCase(normalized));
        if (!duplicate) parts.add(normalized);
    }

    private String validationError(String title) {
        int length = title == null ? 0 : title.length();
        if (length < MIN_LENGTH) {
            return "Tên sản phẩm TikTok phải có ít nhất 25 ký tự";
        }
        if (length > MAX_LENGTH) {
            return "Tên sản phẩm TikTok không được vượt quá 255 ký tự";
        }
        return null;
    }

    private String truncateAtWord(String value, int maxLength) {
        String normalized = normalize(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized == null ? "" : normalized;
        }
        int boundary = normalized.lastIndexOf(' ', maxLength);
        if (boundary <= 0) boundary = maxLength;
        return normalized.substring(0, boundary).trim();
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }
}
