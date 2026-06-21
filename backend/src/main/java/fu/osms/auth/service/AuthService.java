package fu.osms.auth.service;

import fu.osms.auth.dto.request.LoginRequest;
import fu.osms.auth.dto.request.ChangePasswordRequest;
import fu.osms.auth.dto.request.ResetPasswordRequest;
import fu.osms.auth.dto.response.AuthResponse;
import fu.osms.auth.dto.response.ResetPasswordResponse;
import fu.osms.auth.dto.response.TokenPairDTO;

import java.util.UUID;

public interface AuthService {

    TokenPairDTO login(LoginRequest request);

    TokenPairDTO refreshToken(String refreshToken);

    void logout(String refreshToken);

    public void processForgotPassword(String email);

    public void validateResetToken(String tokenStr);

    public void updatePassword(ChangePasswordRequest request);

    public ResetPasswordResponse resetUserPassword(ResetPasswordRequest request, UUID adminId);

    public void changePasswordAfterLogin(UUID userId, String oldPassword, String newPassword, String confirmPassword);
}
