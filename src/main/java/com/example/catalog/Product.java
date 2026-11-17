package com.example.catalog;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String name;
    @Column(nullable = false, length = 1000)
    private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_keywords", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "keyword", nullable = false)
    private Set<String> keywords = new LinkedHashSet<>();

    protected Product() {}
    public Product(String name, String description, Category category, Set<String> keywords) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keywords = keywords;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public Set<String> getKeywords() { return keywords; }
}
