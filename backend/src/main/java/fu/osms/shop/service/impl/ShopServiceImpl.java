package fu.osms.shop.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.shop.dto.request.ShopRequest;
import fu.osms.shop.dto.response.ShopResponse;
import fu.osms.shop.entity.Shop;
import fu.osms.shop.mapper.ShopMapper;
import fu.osms.shop.repository.ShopRepository;
import fu.osms.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final ShopRepository shopRepository;
    private final ShopMapper shopMapper;

    @Override
    @Transactional
    public ShopResponse create(ShopRequest request) {
        if (shopRepository.existsBySlug(request.getSlug())) {
            throw new IllegalArgumentException("Slug already exists: " + request.getSlug());
        }
        Shop shop = shopMapper.toEntity(request);
        return shopMapper.toResponse(shopRepository.save(shop));
    }

    @Override
    @Transactional(readOnly = true)
    public ShopResponse getById(UUID id) {
        return shopRepository.findById(id)
                .map(shopMapper::toResponse)
                .orElseThrow(() -> new RuntimeException("Shop not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShopResponse> getAll(int page, int size) {
        Page<Shop> pageResult = shopRepository.findAll(PageRequest.of(page, size));
        return PageResponse.<ShopResponse>builder()
                .content(pageResult.getContent().stream().map(shopMapper::toResponse).toList())
                .page(page).size(size)
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .first(pageResult.isFirst()).last(pageResult.isLast())
                .build();
    }

    @Override
    @Transactional
    public ShopResponse update(UUID id, ShopRequest request) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shop not found: " + id));
        shopMapper.updateEntityFromRequest(request, shop);
        return shopMapper.toResponse(shopRepository.save(shop));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!shopRepository.existsById(id)) {
            throw new RuntimeException("Shop not found: " + id);
        }
        shopRepository.deleteById(id);
    }
}
