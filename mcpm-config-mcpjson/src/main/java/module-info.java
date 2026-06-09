module io.mcpm.config.mcpjson {
    requires io.mcpm.spi;
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;
    provides io.mcpm.spi.ConfigFormatProvider with io.mcpm.config.mcpjson.McpJsonConfigFormatProvider;
}
