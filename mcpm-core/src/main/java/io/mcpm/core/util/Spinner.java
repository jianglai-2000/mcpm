package io.mcpm.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple terminal spinner using ANSI escape codes.
 * Runs in a separate thread and updates every ~100ms.
 * <p>
 * Usage:
 * <pre>
 *   var spinner = new Spinner("Downloading");
 *   spinner.start();
 *   // ... long operation ...
 *   spinner.stop("✓");
 * </pre>
 */
public class Spinner implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Spinner.class);

    private static final String[] FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final String ANSI_HIDE = "\u001B[?25l";
    private static final String ANSI_SHOW = "\u001B[?25h";
    private static final String ANSI_CLEAR_LINE = "\u001B[2K\r";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final String prefix;
    private Thread thread;
    private boolean ansiSupported;

    public Spinner(String prefix) {
        this.prefix = prefix;
        this.ansiSupported = detectAnsi();
    }

    /**
     * Start the spinner in a background thread.
     */
    public void start() {
        if (!ansiSupported) {
            System.err.print(prefix + "... ");
            System.err.flush();
            return;
        }
        running.set(true);
        System.err.print(ANSI_HIDE);
        thread = new Thread(() -> {
            int i = 0;
            while (running.get()) {
                System.err.print(ANSI_CLEAR_LINE + FRAMES[i % FRAMES.length] + " " + prefix);
                System.err.flush();
                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
                i++;
            }
        }, "spinner");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Stop the spinner and show a status symbol.
     */
    public void stop(String status) {
        running.set(false);
        if (thread != null) {
            try { thread.join(200); } catch (InterruptedException ignored) {}
        }
        if (!ansiSupported) {
            System.err.println(status);
            System.err.flush();
            return;
        }
        System.err.print(ANSI_CLEAR_LINE + status + " " + prefix + "   ");
        System.err.print(ANSI_SHOW);
        System.err.println();
        System.err.flush();
    }

    /**
     * Stop with a green checkmark (success).
     */
    public void success() {
        stop("\u001B[32m\u2713\u001B[0m"); // green ✓
    }

    /**
     * Stop with a red cross (failure).
     */
    public void fail() {
        stop("\u001B[31m\u2717\u001B[0m"); // red ✗
    }

    @Override
    public void close() {
        if (running.get()) {
            stop("");
        }
    }

    private static boolean detectAnsi() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            // Check for Windows Terminal or ConEmu (most modern terminals)
            String term = System.getenv("TERM_PROGRAM");
            String wtSession = System.getenv("WT_SESSION");
            return (term != null && term.contains("terminal"))
                    || wtSession != null
                    || "true".equals(System.getenv("ANSICON"));
        }
        return true; // Unix terminals almost always support ANSI
    }
}
