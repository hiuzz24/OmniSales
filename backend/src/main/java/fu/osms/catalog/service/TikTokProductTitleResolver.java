package fu.osms.catalog.service;

import fu.osms.catalog.dto.TikTokProductTitleInput;
import fu.osms.catalog.dto.TikTokProductTitleResult;

public interface TikTokProductTitleResolver {

    TikTokProductTitleResult resolve(TikTokProductTitleInput input);
}
