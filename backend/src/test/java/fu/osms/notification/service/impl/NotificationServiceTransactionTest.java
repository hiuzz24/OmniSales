package fu.osms.notification.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationServiceImpl transaction boundaries")
class NotificationServiceTransactionTest {

    @Test
    @DisplayName("deduplicated notification opens a new transaction for after-commit listeners")
    void createNotificationIfAbsent_requiresNewTransaction() throws NoSuchMethodException {
        Method method = NotificationServiceImpl.class.getMethod(
                "createNotificationIfAbsent",
                UUID.class,
                String.class,
                String.class,
                String.class,
                String.class,
                UUID.class
        );

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
