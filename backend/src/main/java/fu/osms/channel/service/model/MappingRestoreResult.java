package fu.osms.channel.service.model;

public record MappingRestoreResult(int restoredCount, boolean legacyRestore) {

    public static MappingRestoreResult none() {
        return new MappingRestoreResult(0, false);
    }
}
