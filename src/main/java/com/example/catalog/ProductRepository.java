package com.example.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    @Query("select distinct p from Product p join fetch p.category left join fetch p.keywords where p.id in :ids")
    List<Product> findAllWithDetails(@Param("ids") List<Long> ids);
}
