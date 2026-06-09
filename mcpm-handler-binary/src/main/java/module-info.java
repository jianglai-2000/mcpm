module io.mcpm.handler.binary {
    requires io.mcpm.spi;
    requires org.slf4j;
    requires java.net.http;
    provides io.mcpm.spi.PackageHandler with io.mcpm.handler.binary.BinaryPackageHandler;
}
