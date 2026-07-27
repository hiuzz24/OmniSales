package fu.osms.catalog.dto;

public record TikTokProductTitleResult(
        String title,
        boolean overridden,
        boolean valid,
        String validationError
) {
}
