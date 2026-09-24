package com.orderflow.auth;

import com.orderflow.common.error.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<UserDto> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserDto::from);
    }

    @Transactional
    public UserDto changeRole(Long userId, Role newRole) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setRole(newRole);
        return UserDto.from(user);
    }

    public record UserDto(Long id, String email, String fullName, String role) {

        static UserDto from(User user) {
            return new UserDto(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getRole().name());
        }
    }
}
