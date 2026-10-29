package com.restaurantapp.orders;

import org.springframework.data.domain.Page;

import java.util.List;

public record OrderPageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    static <T> OrderPageResponse<T> from(Page<?> source, List<T> content) {
        return new OrderPageResponse<>(content, source.getNumber(), source.getSize(),
                source.getTotalElements(), source.getTotalPages());
    }
}
