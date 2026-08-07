package fu.osms.sync.tiktok.order;

import java.util.Map;

public interface TikTokOrderMetadataMapper {
    Map<String, Object> merge(Map<String, Object> existing, TikTokOrderWriteContext context,
                              TikTokOrderWriteModel model, boolean includeSnapshot);
}
