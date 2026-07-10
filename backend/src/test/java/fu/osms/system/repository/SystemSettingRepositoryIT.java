package fu.osms.system.repository;

import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import fu.osms.system.entity.SystemSetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SystemSettingRepositoryIT extends IntegrationTestBase {

    @Autowired SystemSettingRepository settingRepo;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void findByCategory_returnsSettingsOfCategory() {
        String category = "IT_CATEGORY_" + TestDataFactory.uniqueSuffix();
        SystemSetting s = new SystemSetting();
        s.setKey("IT_KEY_" + TestDataFactory.uniqueSuffix());
        s.setValue("IT_VALUE");
        s.setCategory(category);
        settingRepo.save(s);

        List<SystemSetting> got = settingRepo.findByCategory(category);
        assertThat(got).extracting(SystemSetting::getKey).contains(s.getKey());
    }

    @Test
    void findById_returnsSetting() {
        SystemSetting s = new SystemSetting();
        s.setKey("IT_KEY_" + TestDataFactory.uniqueSuffix());
        s.setValue("V");
        s.setCategory("IT");
        settingRepo.save(s);

        Optional<SystemSetting> got = settingRepo.findById(s.getKey());
        assertThat(got).isPresent();
    }

    @Test
    void saveAndDelete_setting() {
        SystemSetting s = new SystemSetting();
        s.setKey("IT_KEY_" + TestDataFactory.uniqueSuffix());
        s.setValue("V");
        s.setCategory("IT");
        settingRepo.save(s);

        String key = s.getKey();
        settingRepo.deleteById(key);
        assertThat(settingRepo.findById(key)).isEmpty();
    }
}
