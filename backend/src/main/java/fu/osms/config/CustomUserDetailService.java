package fu.osms.config;

import fu.osms.auth.entity.User;
import fu.osms.auth.entity.UserRole;
import fu.osms.auth.enums.UserStatus;
import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CustomUserDetailService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        if(user.getStatus().equals(UserStatus.LOCKED)){
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        } else if (user.getStatus().equals(UserStatus.INACTIVE)) {
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        }

        var roles = userRoleRepository.findByUserId(user.getId());
        if (roles.isEmpty()) {
            throw new UsernameNotFoundException("No role assigned to user: " + email);
        }
        UserRole userRole = roles.get(0);

        GrantedAuthority authority = new SimpleGrantedAuthority(userRole.getRole().getName());

        return org.springframework.security.core.userdetails.User
                .builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .roles(authority.getAuthority())
                .build();
    }
}
