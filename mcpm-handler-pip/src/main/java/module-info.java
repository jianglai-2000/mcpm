module io.mcpm.handler.pip {
    requires io.mcpm.spi;
    requires org.slf4j;
    provides io.mcpm.spi.PackageHandler with io.mcpm.handler.pip.PipPackageHandler;
}
