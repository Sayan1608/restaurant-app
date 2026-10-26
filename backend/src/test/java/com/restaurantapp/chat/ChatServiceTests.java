package com.restaurantapp.chat;

import com.restaurantapp.common.ApiException;
import com.restaurantapp.menu.MenuCategoryResponse;
import com.restaurantapp.menu.MenuItemResponse;
import com.restaurantapp.menu.MenuResponse;
import com.restaurantapp.menu.MenuService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;

class ChatServiceTests {
    private static final UUID RESTAURANT_ID = UUID.randomUUID();

    @Test
    void sendsCurrentPublicMenuAsContextAndReturnsProviderReply() {
        MenuService menuService = mock(MenuService.class);
        when(menuService.getPublicMenu(RESTAURANT_ID)).thenReturn(menu());
        RestClient.Builder builder = RestClient.builder().baseUrl("https://provider.test/api/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://provider.test/api/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paneer Bowl")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("249.00")))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"A fresh paneer bowl is on the menu.\"}}]}", APPLICATION_JSON));

        ChatService service = new ChatService(menuService, builder.build(), "test-key", "test-model");

        assertThat(service.answer(RESTAURANT_ID, "What can I have?").reply())
                .isEqualTo("A fresh paneer bowl is on the menu.");
        server.verify();
    }

    @Test
    void doesNotCallProviderWhenKeyIsMissing() {
        MenuService menuService = mock(MenuService.class);
        ChatService service = new ChatService(menuService, RestClient.builder().build(), "", "model");

        assertThatThrownBy(() -> service.answer(RESTAURANT_ID, "Tell me about lunch"))
                .isInstanceOf(ApiException.class)
                .hasMessage("The menu assistant is unavailable right now. Please try again later.");
        verify(menuService, never()).getPublicMenu(RESTAURANT_ID);
    }

    @Test
    void returnsSafeUnavailableMessageWhenProviderFails() {
        MenuService menuService = mock(MenuService.class);
        when(menuService.getPublicMenu(RESTAURANT_ID)).thenReturn(menu());
        RestClient.Builder builder = RestClient.builder().baseUrl("https://provider.test/api/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://provider.test/api/v1/chat/completions"))
                .andRespond(withServerError());
        ChatService service = new ChatService(menuService, builder.build(), "test-key", "test-model");

        assertThatThrownBy(() -> service.answer(RESTAURANT_ID, "Tell me about lunch"))
                .isInstanceOf(ApiException.class)
                .hasMessage("The menu assistant is unavailable right now. Please try again later.");
        server.verify();
    }

    @Test
    void limitsRequestsForEachClientToTwentyPerMinute() {
        ChatRateLimiter limiter = new ChatRateLimiter();
        for (int i = 0; i < 20; i++) assertThat(limiter.allow("192.0.2.8")).isTrue();
        assertThat(limiter.allow("192.0.2.8")).isFalse();
        assertThat(limiter.allow("192.0.2.9")).isTrue();
    }

    private static MenuResponse menu() {
        MenuItemResponse item = new MenuItemResponse(UUID.randomUUID(), UUID.randomUUID(), "Mains", "Paneer Bowl",
                "Grilled paneer with rice", new BigDecimal("249.00"), null, true);
        return new MenuResponse(RESTAURANT_ID, "Goodfolk Kitchen",
                List.of(new MenuCategoryResponse(UUID.randomUUID(), "Mains", 0, List.of(item))));
    }
}
