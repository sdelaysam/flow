package flow;

import android.app.Activity;

/**
 * History callback, should be implemented to track history events.
 *
 * @author sdelaysam
 */

public interface HistoryCallback {
    /**
     * Called by Flow when history cleared.
     * Default implementation would call {@link Activity#finish()}
     */
    void onHistoryCleared();
}
