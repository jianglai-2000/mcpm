module io.mcpm.registry.builtin {
    requires io.mcpm.spi;
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;
    requires java.net.http;
    provides io.mcpm.spi.RegistryProvider with io.mcpm.registry.builtin.BuiltinRegistryProvider;
}
