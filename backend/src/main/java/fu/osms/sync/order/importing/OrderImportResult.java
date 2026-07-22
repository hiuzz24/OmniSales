package fu.osms.sync.order.importing;

public enum OrderImportResult {
    CREATED,
    UPDATED,
    UNCHANGED,
    SKIPPED_STALE
}
