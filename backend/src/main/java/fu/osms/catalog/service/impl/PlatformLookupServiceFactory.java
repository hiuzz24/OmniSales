package fu.osms.catalog.service.impl;

import fu.osms.catalog.service.PlatformLookupService;
import fu.osms.common.enums.PlatformType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PlatformLookupServiceFactory {

    private final Map<PlatformType, PlatformLookupService> services;

    public PlatformLookupServiceFactory(List<PlatformLookupService> services) {
        Map<PlatformType, PlatformLookupService> map = new EnumMap<>(PlatformType.class);
        for (PlatformLookupService service : services) {
            map.put(service.getPlatform(), service);
        }
        this.services = map;
    }

    public PlatformLookupService get(PlatformType platform) {
        PlatformLookupService service = services.get(platform);
        if (service == null) {
            throw new IllegalArgumentException("Platform lookup is not supported: " + platform);
        }
        return service;
    }
}
