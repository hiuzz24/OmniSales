package fu.osms.common.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PageResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private Long totalProducts;
    private Long totalSkus;
    private int totalPages;
    private boolean first;
    private boolean last;
}
