module io.mcpm.core {
    requires io.mcpm.spi;
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;
    requires java.net.http;

    exports io.mcpm.core.config;
    exports io.mcpm.core.install;
    exports io.mcpm.core.registry;
    exports io.mcpm.core.state;
    exports io.mcpm.core.manifest;
    exports io.mcpm.core.cache;
    exports io.mcpm.core.util;
    exports io.mcpm.core.settings;
    exports io.mcpm.core.i18n;

    uses io.mcpm.spi.PackageHandler;
    uses io.mcpm.spi.RegistryProvider;
    uses io.mcpm.spi.ConfigFormatProvider;
    uses io.mcpm.spi.LifecycleHook;
}
