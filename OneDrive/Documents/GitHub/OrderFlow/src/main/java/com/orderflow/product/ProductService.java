package com.orderflow.product;

import com.orderflow.common.error.ApiException;
import com.orderflow.common.error.ErrorCode;
import com.orderflow.common.error.ResourceNotFoundException;
import com.orderflow.common.events.EventPublisher;
import com.orderflow.product.ProductDtos.CreateProductRequest;
import com.orderflow.product.ProductDtos.ProductDto;
import com.orderflow.product.ProductDtos.UpdateProductRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    public static final String PRODUCT_CACHE = "product";
    public static final String PRODUCT_LIST_CACHE = "productList";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final EventPublisher eventPublisher;
    private final CacheManager cacheManager;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            EventPublisher eventPublisher,
            CacheManager cacheManager) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.eventPublisher = eventPublisher;
        this.cacheManager = cacheManager;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUCT_CACHE, key = "#id")
    public ProductDto getById(Long id) {
        Product product =
                productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return ProductDto.from(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> search(ProductStatus status, String category, String search, Pageable pageable) {
        return productRepository.search(status, category, search, pageable).map(ProductDto::from);
    }

    @Transactional
    public ProductDto create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ApiException(ErrorCode.CONFLICT, "SKU already exists");
        }
        Category category = resolveCategory(request.category());
        Product product = new Product(request.sku(), request.name(), request.description(), request.price(), category);
        product.setStatus(request.status() == null ? ProductStatus.ACTIVE : request.status());
        Product saved = productRepository.save(product);
        log.info("Product created: {} ({})", saved.getSku(), saved.getId());
        return ProductDto.from(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = PRODUCT_CACHE, key = "#id")
    public ProductDto update(Long id, UpdateProductRequest request) {
        Product product = requireProduct(id);
        Category category = resolveCategory(request.category());
        product.update(request.name(), request.description(), request.price(), category);
        if (request.status() != null) {
            product.setStatus(request.status());
        }
        evictListCaches();
        return ProductDto.from(product);
    }

    @Transactional
    @CacheEvict(cacheNames = PRODUCT_CACHE, key = "#id")
    public void delete(Long id) {
        Product product = requireProduct(id);
        product.setStatus(ProductStatus.DISCONTINUED);
        evictListCaches();
        eventPublisher.publish("product.deleted", "product", String.valueOf(id), Map.of("productId", id));
    }

    private Product requireProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private Category resolveCategory(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return categoryRepository
                .findByNameIgnoreCase(name)
                .orElseGet(() -> categoryRepository.save(new Category(name)));
    }

    private void evictListCaches() {
        // List responses embed product rows; simplest correct strategy is to clear the list cache on any change.
        var listCache = cacheManager.getCache(PRODUCT_LIST_CACHE);
        if (listCache != null) {
            listCache.clear();
        }
    }
}
