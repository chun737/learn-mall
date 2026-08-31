package com.mall.dto;

import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductListDTO {
    private Integer pageNum;
    private Integer categoryId;
    private Integer status;
    private String keyword;
    private LocalDate datetime;
}
