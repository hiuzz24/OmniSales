package fu.osms.config;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PasswordMigrationRunner implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        List<User> users = userRepository.findAll();
        for(User u : users){
            String pass = u.getPasswordHash();
            if(!pass.startsWith("$2a$")){
                u.setPasswordHash(passwordEncoder.encode(pass));
                userRepository.save(u);
            }
        }
    }
}
