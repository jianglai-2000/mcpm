module io.mcpm.handler.docker {
    requires io.mcpm.spi;
    requires org.slf4j;
    provides io.mcpm.spi.PackageHandler with io.mcpm.handler.docker.DockerPackageHandler;
}
