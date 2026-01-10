/* GoCommand.java */
package mybot;

/**
 * Data holder for UCI "go" command parameters.
 * Holds time controls, depth limits, and other search parameters.
 */
public class GoCommand {
    public long whiteTimeMs = 0;
    public long blackTimeMs = 0;
    public long whiteIncrementMs = 0;
    public long blackIncrementMs = 0;
    public long moveTimeMs = 0;
    public int maxDepth = 0;
    public boolean infinite = false;

    /**
     * Check if this command has time control parameters.
     */
    public boolean hasTimeControl() {
        return whiteTimeMs > 0 || blackTimeMs > 0 || moveTimeMs > 0;
    }

    /**
     * Check if this command has a depth limit.
     */
    public boolean hasDepthLimit() {
        return maxDepth > 0;
    }
}
