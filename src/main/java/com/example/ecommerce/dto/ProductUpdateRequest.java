package com.example.ecommerce.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductUpdateRequest {

    @NotBlank(message = "El nombre no puede estar vacío")
    private String name;

    private String description;

    @Positive(message = "El precio debe ser mayor a cero")
    private BigDecimal price;

    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stock;

    @NotBlank(message = "La categoría no puede estar vacía")
    private String category;
}
