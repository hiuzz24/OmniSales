package fu.osms.address.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "administrative_divisions",
        indexes = {
                @Index(name = "idx_adm_country_level", columnList = "country_code, level"),
                @Index(name = "idx_adm_parent", columnList = "country_code, parent_code")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdministrativeDivision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", length = 10, nullable = false)
    private String countryCode;

    @Column(name = "parent_code", length = 20)
    private String parentCode;

    @Column(name = "code", length = 20, nullable = false)
    private String code;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer level;
}
