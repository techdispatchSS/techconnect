package silver.solutions.techconnect.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Records a field-by-field diff for the audit trail (FR-09) while applying it, so a field can
 * never be changed on an entity without the audit trail knowing, or vice versa. Used by
 * {@code AdminUserService.update} and {@code AuthService.updateProfile}, which otherwise
 * duplicated the same "compare, record if different, apply" logic per field.
 */
public final class ChangeTracker {

    private final Map<String, Object> changes = new LinkedHashMap<>();

    /**
     * Applies {@code newValue} via {@code setter} and records the change — but only if it
     * actually differs from {@code oldValue}, so a same-value resubmission stays silent rather
     * than cluttering the trail with a no-op entry.
     */
    public <T> void apply(String field, T oldValue, T newValue, Consumer<T> setter) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        record(field, oldValue, newValue);
        setter.accept(newValue);
    }

    /**
     * Records a change without applying it — for a field whose change carries side effects
     * {@link #apply} cannot express (e.g. a role change also bumping the token version), where
     * the caller applies the change itself.
     */
    public void record(String field, Object oldValue, Object newValue) {
        changes.put(field, Map.of("from", String.valueOf(oldValue), "to", String.valueOf(newValue)));
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public Map<String, Object> changes() {
        return changes;
    }
}
