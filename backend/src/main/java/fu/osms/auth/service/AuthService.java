package fu.osms.auth.service;

import fu.osms.auth.dto.request.LoginRequest;
import fu.osms.auth.dto.request.RegisterRequest;
import fu.osms.auth.dto.response.AuthResponse;
import fu.osms.auth.dto.response.TokenPairDTO;

public interface AuthService {

    TokenPairDTO login(LoginRequest request);

    AuthResponse refreshToken(String refreshToken);

    void logout(String refreshToken);

    void register(RegisterRequest request);

    void verifyEmail(String token);

    void resendVerificationEmail(String email);
}
