package io.mcpm.handler.bun;

import io.mcpm.spi.PackageHandler;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class BunPackageHandlerTest {
    private final BunPackageHandler h = new BunPackageHandler();

    @Test void type() { assertThat(h.supportedType()).isEqualTo("bun"); }
    @Test void install() {
        var r = h.install(new PackageHandler.InstallRequest("@org/srv", "1.0", Map.of(), Map.of()));
        assertThat(r.success()).isTrue();
        assertThat(r.entryCommand()).isEqualTo("bun");
        assertThat(r.entryArgs()).contains("run", "@org/srv");
    }
    @Test void uninstall() { assertThat(h.uninstall(new PackageHandler.UninstallRequest("p", "1")).success()).isTrue(); }
}
