package fu.osms.sync.lazada.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public record LazadaMigratedImages(
        List<String> productImageUrls,
        Map<UUID, List<String>> variantImageUrls
) {
    public LazadaMigratedImages {
        productImageUrls = productImageUrls == null ? List.of() : List.copyOf(productImageUrls);
        variantImageUrls = variantImageUrls == null
                ? Map.of()
                : variantImageUrls.entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(
                                Map.Entry::getKey,
                                entry -> List.copyOf(entry.getValue())
                        ));
    }
}
