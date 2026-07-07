package fu.osms.system.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "backup_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Column(name = "filepath", nullable = false, length = 500)
    private String filepath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 20)
    private String type; // MANUAL, SCHEDULED

    @Column(nullable = false, length = 20)
    private String status; // SUCCESS, FAILED

    @Column(name = "created_by")
    private String createdBy; // Actor email or "SYSTEM"

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
