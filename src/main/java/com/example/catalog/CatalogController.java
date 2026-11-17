package com.example.catalog;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final ProductRepository products;
    private final CategoryRepository categories;

    public CatalogController(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return categories.findAllByOrderByNameAsc().stream()
                .map(c -> new CategoryResponse(c.getSlug(), c.getName())).toList();
    }

    @GetMapping("/products")
    @Transactional(readOnly = true)
    public Page<ProductResponse> products(
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size must be 1..100");
        }
        if (!Set.of("name", "category").contains(sort) || !Set.of("asc", "desc").contains(direction)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort or direction");
        }
        String categoryValue = category.strip().toLowerCase(Locale.ROOT);
        String query = q.strip().toLowerCase(Locale.ROOT);
        if (query.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q must be <= 100 characters");
        }
        Specification<Product> filter = (root, criteria, cb) -> {
            var predicate = cb.conjunction();
            if (!categoryValue.isEmpty()) {
                predicate = cb.and(predicate, cb.equal(root.join("category").get("slug"), categoryValue));
            }
            if (!query.isEmpty()) {
                String pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                var keywordMatch = criteria.subquery(Long.class);
                Root<Product> keywordProduct = keywordMatch.from(Product.class);
                Join<Product, String> keyword = keywordProduct.join("keywords");
                keywordMatch.select(keywordProduct.get("id")).where(
                        cb.equal(keywordProduct.get("id"), root.get("id")),
                        cb.like(cb.lower(keyword), pattern, '\\'));
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\'),
                        cb.exists(keywordMatch)));
            }
            return predicate;
        };
        Sort ordering = Sort.by(Sort.Direction.fromString(direction), sort.equals("category") ? "category.name" : "name")
                .and(Sort.by("id"));
        Page<Product> matching = products.findAll(filter, PageRequest.of(page, size, ordering));
        if (matching.isEmpty()) return matching.map(product -> null);
        Map<Long, Product> details = products.findAllWithDetails(matching.map(Product::getId).getContent())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        return matching.map(item -> {
            Product product = details.get(item.getId());
            return new ProductResponse(product.getId(), product.getName(), product.getDescription(),
                    new CategoryResponse(product.getCategory().getSlug(), product.getCategory().getName()),
                    product.getKeywords().stream().sorted().toList());
        });
    }

    public record CategoryResponse(String slug, String name) {}
    public record ProductResponse(Long id, String name, String description, CategoryResponse category, List<String> keywords) {}
}
