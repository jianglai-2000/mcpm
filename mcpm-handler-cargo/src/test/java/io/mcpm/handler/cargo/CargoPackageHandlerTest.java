package io.mcpm.handler.cargo;

import io.mcpm.spi.PackageHandler;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class CargoPackageHandlerTest {
    private final CargoPackageHandler h = new CargoPackageHandler();

    @Test void type() { assertThat(h.supportedType()).isEqualTo("cargo"); }
    @Test void install() {
        var r = h.install(new PackageHandler.InstallRequest("my-srv", "1.0", Map.of(), Map.of()));
        assertThat(r.success()).isTrue();
        assertThat(r.entryCommand()).isEqualTo("cargo");
        assertThat(r.entryArgs()).contains("run", "--package", "my-srv");
    }
    @Test void uninstall() { assertThat(h.uninstall(new PackageHandler.UninstallRequest("p", "1")).success()).isTrue(); }
}
