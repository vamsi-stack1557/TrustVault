package com.snovasys.trustvault.security;

import com.snovasys.trustvault.model.AppUser;
import com.snovasys.trustvault.repo.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class VaultUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    public VaultUserDetailsService(UserRepository users) { this.users = users; }
    @Override public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = users.findByEmailIgnoreCase(email).orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return User.withUsername(user.getEmail()).password(user.getPasswordHash())
                .roles(user.getRole().name()).disabled(!user.isEnabled()).build();
    }
}
