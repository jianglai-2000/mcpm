module io.mcpm.handler.npx {
    requires io.mcpm.spi;
    requires org.slf4j;
    provides io.mcpm.spi.PackageHandler with io.mcpm.handler.npx.NpxPackageHandler;
}
