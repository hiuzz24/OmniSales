package fu.osms.auth.service;

import fu.osms.auth.dto.request.LoginRequest;
import fu.osms.auth.dto.response.AuthResponse;
import fu.osms.auth.dto.response.TokenPairDTO;

public interface AuthService {

    TokenPairDTO login(LoginRequest request);

    TokenPairDTO refreshToken(String refreshToken);

    void logout(String refreshToken);
}
