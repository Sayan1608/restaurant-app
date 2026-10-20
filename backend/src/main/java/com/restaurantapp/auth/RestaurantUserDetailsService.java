package com.restaurantapp.auth;

import com.restaurantapp.user.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class RestaurantUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public RestaurantUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return userAccountRepository.findByEmail(normalizedEmail)
                .map(AuthenticatedUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
