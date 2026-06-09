package io.mcpm.handler.go;

import io.mcpm.spi.PackageHandler;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class GoPackageHandlerTest {
    private final GoPackageHandler h = new GoPackageHandler();

    @Test void type() { assertThat(h.supportedType()).isEqualTo("go"); }
    @Test void install() {
        var r = h.install(new PackageHandler.InstallRequest("pkg", "1.0", Map.of(), Map.of()));
        assertThat(r.success()).isTrue();
        assertThat(r.entryCommand()).isEqualTo("go");
        assertThat(r.entryArgs()).contains("run", "pkg@1.0");
    }
    @Test void uninstall() { assertThat(h.uninstall(new PackageHandler.UninstallRequest("p", "1")).success()).isTrue(); }
}
