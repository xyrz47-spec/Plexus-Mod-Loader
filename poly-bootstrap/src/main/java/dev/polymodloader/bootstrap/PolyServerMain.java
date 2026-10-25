package dev.polymodloader.bootstrap;

/**
 * JVM entry point of a dedicated server the installer set up: {@code java -jar polymodloader-server.jar nogui}, the
 * way server hosts start a server jar. It is {@link PolyLauncherMain} with {@code poly.launchTarget} defaulting to
 * {@code dedicated_server}; the game jar is found through the launch jar's manifest {@code Class-Path}
 * ({@link ClassPathGameJarLocator}). Other system properties work as for {@link PolyLauncherMain}.
 */
public final class PolyServerMain {

    private PolyServerMain() {}

    public static void main(String[] args) throws Throwable {
        if (System.getProperty("poly.launchTarget") == null) {
            System.setProperty("poly.launchTarget", "dedicated_server");
        }
        PolyLauncherMain.main(args);
    }
}
