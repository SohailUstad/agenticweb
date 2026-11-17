package com.example.catalog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CatalogSeeder implements ApplicationRunner {
    private final CategoryRepository categories;
    private final ProductRepository products;

    public CatalogSeeder(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (categories.count() > 0 || products.count() > 0) return;
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("seed-catalog.txt").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int categoryCount = 0;
            int productCount = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\\|", -1);
                if (fields.length != 4) throw new IllegalStateException("Invalid catalog seed row: " + line);
                String[] names = fields[3].split(",");
                if (names.length != 6) throw new IllegalStateException("Each category needs six products: " + fields[0]);
                String categoryName = fields[0].trim();
                String slug = categoryName.toLowerCase(Locale.ROOT).replace(' ', '-');
                Category category = categories.save(new Category(slug, categoryName));
                categoryCount++;
                for (String rawName : names) {
                    String name = rawName.trim();
                    Set<String> keywords = new LinkedHashSet<>();
                    keywords.addAll(Arrays.asList(fields[2].trim().toLowerCase(Locale.ROOT).split(" ")));
                    keywords.addAll(Arrays.asList(name.toLowerCase(Locale.ROOT).split("[ -]+")));
                    keywords.removeIf(word -> word.isBlank() || Set.of("and", "the", "for", "with", "a").contains(word));
                    String description = name + " is a sample " + categoryName.toLowerCase(Locale.ROOT)
                            + " product for " + fields[1].trim() + ".";
                    products.save(new Product(name, description, category, keywords));
                    productCount++;
                }
            }
            if (categoryCount != 50 || productCount != 300) {
                throw new IllegalStateException("Expected 50 categories and 300 products, got "
                        + categoryCount + " and " + productCount);
            }
        }
    }
}
