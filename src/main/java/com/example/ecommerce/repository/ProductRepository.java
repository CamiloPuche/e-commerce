package com.example.ecommerce.repository;

import com.example.ecommerce.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    // JpaRepository ya nos da findAll(Pageable pageable), save, findById, etc.
    // Si necesitáramos buscar por categoría, por ejemplo, agregaríamos:
    // Page<Product> findByCategory(String category, Pageable pageable);
}
