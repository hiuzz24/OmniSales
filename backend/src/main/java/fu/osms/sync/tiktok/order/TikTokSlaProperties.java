package fu.osms.sync.tiktok.order;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "osms.tiktok-sla")
public class TikTokSlaProperties {
    private int bufferHours = 2;
    private List<String> vietnamHolidays = new ArrayList<>();

    public int getBufferHours() { return bufferHours; }
    public void setBufferHours(int bufferHours) { this.bufferHours = bufferHours; }
    public List<String> getVietnamHolidays() { return vietnamHolidays; }
    public void setVietnamHolidays(List<String> vietnamHolidays) {
        this.vietnamHolidays = vietnamHolidays == null ? new ArrayList<>() : vietnamHolidays;
    }
}
