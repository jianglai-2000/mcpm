module io.mcpm.handler.uvx {
    requires io.mcpm.spi;
    requires org.slf4j;
    provides io.mcpm.spi.PackageHandler with io.mcpm.handler.uvx.UvxPackageHandler;
}
