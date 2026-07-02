package red.man10.tablist.config

import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

data class TabListConfig(
    val privateServers: Set<String>,
) {
    companion object {
        private const val CONFIG_FILE_NAME = "config.yml"
        private const val PRIVATE_SERVERS_KEY = "private-servers"

        fun load(dataDirectory: Path): TabListConfig {
            Files.createDirectories(dataDirectory)
            val configPath = dataDirectory.resolve(CONFIG_FILE_NAME)
            if (Files.notExists(configPath)) {
                copyDefaultConfig(configPath)
            }

            val loader = YamlConfigurationLoader.builder()
                .path(configPath)
                .build()
            val root = loader.load()
            return fromNode(root)
        }

        private fun copyDefaultConfig(configPath: Path) {
            val resourceStream: InputStream = TabListConfig::class.java.classLoader
                .getResourceAsStream(CONFIG_FILE_NAME)
                ?: throw IllegalStateException("Default $CONFIG_FILE_NAME not found in plugin resources")
            resourceStream.use { input ->
                Files.copy(input, configPath, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        private fun fromNode(root: ConfigurationNode): TabListConfig {
            val privateServers = root.node(PRIVATE_SERVERS_KEY)
                .childrenList()
                .mapNotNull { node -> node.string?.takeIf { it.isNotBlank() } }
                .toSet()
            return TabListConfig(privateServers = privateServers)
        }
    }
}
