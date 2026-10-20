package com.restaurantapp.restaurant;

import com.restaurantapp.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    public RestaurantService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    @Transactional(readOnly = true)
    public List<RestaurantResponse> listActive() {
        return restaurantRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(RestaurantResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getActive(UUID restaurantId) {
        return RestaurantResponse.from(findActive(restaurantId));
    }

    @Transactional(readOnly = true)
    public Restaurant findActive(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .filter(Restaurant::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND",
                        "Restaurant was not found."));
    }
}
