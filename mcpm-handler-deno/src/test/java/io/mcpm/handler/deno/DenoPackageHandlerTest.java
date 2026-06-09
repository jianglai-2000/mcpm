package io.mcpm.handler.deno;

import io.mcpm.spi.PackageHandler;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class DenoPackageHandlerTest {
    private final DenoPackageHandler h = new DenoPackageHandler();

    @Test void type() { assertThat(h.supportedType()).isEqualTo("deno"); }
    @Test void install() {
        var r = h.install(new PackageHandler.InstallRequest("jsr:@org/srv", "1.0", Map.of(), Map.of()));
        assertThat(r.success()).isTrue();
        assertThat(r.entryCommand()).isEqualTo("deno");
        assertThat(r.entryArgs()).contains("run", "--allow-all", "jsr:@org/srv");
    }
    @Test void uninstall() { assertThat(h.uninstall(new PackageHandler.UninstallRequest("p", "1")).success()).isTrue(); }
}
