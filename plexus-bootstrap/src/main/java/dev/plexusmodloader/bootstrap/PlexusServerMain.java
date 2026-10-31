package dev.plexusmodloader.bootstrap;

/**
 * JVM entry point of a dedicated server the installer set up: {@code java -jar plexusmodloader-server.jar nogui}, the
 * way server hosts start a server jar. It is {@link PlexusLauncherMain} with {@code plexus.launchTarget} defaulting to
 * {@code dedicated_server}; the game jar is found through the launch jar's manifest {@code Class-Path}
 * ({@link ClassPathGameJarLocator}). Other system properties work as for {@link PlexusLauncherMain}.
 */
public final class PlexusServerMain {

    private PlexusServerMain() {}

    public static void main(String[] args) throws Throwable {
        LegacyPropertyNames.copyToCurrentNames(System.getProperties());
        if (System.getProperty("plexus.launchTarget") == null) {
            System.setProperty("plexus.launchTarget", "dedicated_server");
        }
        PlexusLauncherMain.main(args);
    }
}
