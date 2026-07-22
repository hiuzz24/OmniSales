package fu.osms.sync.tiktok.order;

import java.util.Map;

public interface TikTokOrderMapper {
    TikTokOrderWriteModel map(Map<String, Object> detail);
}
