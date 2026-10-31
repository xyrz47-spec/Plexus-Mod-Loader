package dev.plexusmodloader.installer;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;

/**
 * The installer window: client (the Minecraft Launcher) or dedicated server, Minecraft version, folder, memory,
 * install and uninstall. When done it shows a system notification and, unless the player unticked it or there are
 * warnings to read, closes. The window starts in the system's language; the menu in its corner switches it.
 */
final class InstallerWindow {

    /** The languages of the window, in the order of the menu. */
    enum Language {
        ENGLISH("English"),
        KOREAN("한국어");

        private final String name;

        Language(String name) {
            this.name = name;
        }

        /** The system's language if the window has it, otherwise English. */
        static Language system() {
            return Locale.getDefault().getLanguage().equals("ko") ? KOREAN : ENGLISH;
        }

        /** The name the menu shows, in the language itself. */
        @Override
        public String toString() {
            return name;
        }
    }

    private final InstallerPayload payload;
    private final Language language;
    private final Map<String, String> text;
    private final JFrame frame;
    private static final int FIELD_COLUMNS = 26;

    private final JToggleButton client;
    private final JToggleButton server;
    private final JComboBox<Language> languages;
    private final boolean[] advancedOpen = {false};
    private final Runnable refresh;
    private final JComboBox<String> version;
    private final JTextField directory;
    private final JTextField serverDirectory;
    private final JCheckBox profile;
    private final JComboBox<String> maximumMemory;
    private final JComboBox<String> minimumMemory;
    private final JCheckBox closeWhenDone;
    private final JButton install;
    private final JButton uninstall;
    private final JTextArea log;

    private InstallerWindow(InstallerPayload payload, Language language) {
        this.payload = payload;
        this.language = language;
        this.text = language == Language.KOREAN ? KOREAN : ENGLISH;
        frame = new JFrame(text("title").formatted(payload.plexusVersion()));
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setIconImage(ProfileIcon.image(64));

        JLabel header = new JLabel(
                "Plexus Mod Loader " + payload.plexusVersion(), new ImageIcon(ProfileIcon.image(40)), JLabel.LEADING);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));
        header.setIconTextGap(10);
        languages = new JComboBox<>(Language.values());
        languages.setSelectedItem(language);
        languages.setToolTipText("Language / 언어");
        languages.getAccessibleContext().setAccessibleName("Language / 언어");
        languages.addActionListener(event -> {
            if (languages.getSelectedItem() instanceof Language chosen && chosen != this.language) {
                switchTo(chosen);
            }
        });
        // The menu sits at the right of the title, at its own height.
        JPanel languageCorner = new JPanel(new GridBagLayout());
        languageCorner.add(languages);
        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.add(header, BorderLayout.WEST);
        top.add(languageCorner, BorderLayout.EAST);

        // Client or server: two halves of one control, the same width as the fields below.
        client = new JToggleButton(text("client"), true);
        server = new JToggleButton(text("server"));
        ButtonGroup target = new ButtonGroup();
        target.add(client);
        target.add(server);
        JPanel targets = new JPanel(new GridLayout(1, 2, 0, 0));
        targets.add(client);
        targets.add(server);

        version = new JComboBox<>(payload.minecraftVersions().toArray(String[]::new));
        version.setSelectedItem(payload.newestMinecraftVersion());
        directory = new JTextField(MinecraftDirectories.defaultDirectory().toString(), FIELD_COLUMNS);
        serverDirectory = new JTextField(
                Path.of(System.getProperty("user.home"), "Plexus Mod Loader Server")
                        .toString(),
                FIELD_COLUMNS);
        profile = new JCheckBox(text("profile"), true);
        OptionalLong physical = JavaMemory.physicalMegabytes();
        maximumMemory = new JComboBox<>(
                new String[] {text("launcherDefault"), "2G", "3G", "4G", "6G", "8G", "10G", "12G", "16G"});
        maximumMemory.setEditable(true);
        long recommended = JavaMemory.recommendedMaximum(physical);
        if (recommended != 0) {
            maximumMemory.setSelectedItem(JavaMemory.format(recommended));
        }
        minimumMemory = new JComboBox<>(new String[] {text("notSet"), "1G", "2G", "4G"});
        minimumMemory.setEditable(true);
        JLabel memoryHint = new JLabel(
                physical.isPresent()
                        ? text("pcMemory").formatted(Math.round(physical.getAsLong() / 1024.0))
                        : text("memoryHint"));
        memoryHint.setFont(memoryHint.getFont().deriveFont(memoryHint.getFont().getSize2D() - 1f));
        memoryHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        profile.addActionListener(event -> {
            maximumMemory.setEnabled(profile.isSelected());
            minimumMemory.setEnabled(profile.isSelected());
        });
        closeWhenDone = new JCheckBox(text("closeWhenDone"), true);
        install = new JButton(text("install"));
        install.addActionListener(event -> run(true));
        uninstall = new JButton(text("uninstall"));
        uninstall.addActionListener(event -> run(false));
        log = new JTextArea(6, FIELD_COLUMNS);
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);

        // One grid for everything, so the names and the fields line up whether "Advanced" is open or not. The main
        // view holds what most players change; the rest is in rows that "Advanced" shows.
        JLabel targetLabel = new JLabel(text("target"));
        JLabel serverLabel = new JLabel(text("serverDirectory"));
        JLabel versionLabel = new JLabel(text("minecraft"));
        JLabel memoryLabel = new JLabel(text("maxMemory"));
        JLabel directoryLabel = new JLabel(text("directory"));
        JLabel minimumLabel = new JLabel(text("minMemory"));
        List<JLabel> labels =
                List.of(targetLabel, serverLabel, versionLabel, memoryLabel, directoryLabel, minimumLabel);
        int labelWidth = labels.stream()
                .mapToInt(label -> label.getPreferredSize().width)
                .max()
                .orElse(0);
        for (JLabel label : labels) {
            label.setPreferredSize(new Dimension(labelWidth, label.getPreferredSize().height));
        }
        JPanel serverFolder = folderField(serverDirectory);
        JPanel launcherFolder = folderField(directory);
        // Every control is as wide as a folder field, so the window keeps its width when rows appear or disappear.
        int controlWidth = Math.max(serverFolder.getPreferredSize().width, launcherFolder.getPreferredSize().width);
        for (JComponent control : List.of(targets, version, maximumMemory, minimumMemory)) {
            control.setPreferredSize(new Dimension(controlWidth, control.getPreferredSize().height));
        }

        JPanel form = new JPanel(new GridBagLayout());
        Rows rows = new Rows(form);
        rows.wide(top, new Insets(0, 0, 14, 0));
        rows.add(targetLabel, targets);
        rows.add(serverLabel, serverFolder);
        rows.add(versionLabel, version);
        rows.add(memoryLabel, maximumMemory);
        rows.add(null, memoryHint, new Insets(0, 0, 4, 0));
        List<JComponent> advancedRows = List.of(
                rows.add(directoryLabel, launcherFolder),
                rows.add(minimumLabel, minimumMemory),
                rows.add(null, profile),
                rows.add(null, closeWhenDone),
                rows.add(null, uninstall));
        JScrollPane logPane = new JScrollPane(log);
        rows.wide(logPane, new Insets(8, 0, 0, 0));

        // A server needs its folder, so that row is in the main view; the launcher folder, the profile and uninstall
        // belong to the client.
        List<JComponent> serverOnly = List.of(serverLabel, serverFolder);
        List<JComponent> clientOnly = List.of(directoryLabel, launcherFolder, profile, uninstall);
        List<JComponent> advancedOnly = new ArrayList<>(advancedRows);
        advancedOnly.addAll(List.of(directoryLabel, minimumLabel, logPane));
        JButton toggle = new JButton(text("advancedShow"));
        toggle.setBorderPainted(false);
        toggle.setContentAreaFilled(false);
        toggle.setFocusPainted(false);
        toggle.setMargin(new Insets(0, 0, 0, 0));
        refresh = () -> {
            boolean isServer = server.isSelected();
            advancedOnly.forEach(component -> component.setVisible(advancedOpen[0]));
            serverOnly.forEach(component -> component.setVisible(isServer));
            clientOnly.forEach(component -> component.setVisible(!isServer && advancedOpen[0]));
            maximumMemory.setEnabled(isServer || profile.isSelected());
            minimumMemory.setEnabled(isServer || profile.isSelected());
            toggle.setText(text(advancedOpen[0] ? "advancedHide" : "advancedShow"));
            frame.pack();
        };
        client.addActionListener(event -> refresh.run());
        server.addActionListener(event -> refresh.run());
        toggle.addActionListener(event -> {
            advancedOpen[0] = !advancedOpen[0];
            refresh.run();
        });
        install.setPreferredSize(
                new Dimension(Math.max(install.getPreferredSize().width, 110), install.getPreferredSize().height + 6));
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(12, 0, 0, 0)));
        buttons.add(toggle, BorderLayout.WEST);
        buttons.add(install, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        frame.setContentPane(content);
        frame.getRootPane().setDefaultButton(install);
        refresh.run();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
    }

    /** A folder field with its "Browse…" button, as one control. */
    private JPanel folderField(JTextField field) {
        JButton browse = new JButton(text("browse"));
        browse.addActionListener(event -> chooseDirectory(field));
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.add(field, BorderLayout.CENTER);
        panel.add(browse, BorderLayout.EAST);
        return panel;
    }

    /** Rows of a two-column grid: a name on the left, a control filling the right. */
    private static final class Rows {

        private final JPanel panel;
        private int row;

        Rows(JPanel panel) {
            this.panel = panel;
        }

        /** Adds a row and returns its control; {@code label} may be {@code null} for a control under the fields. */
        JComponent add(JLabel label, JComponent control) {
            return add(label, control, new Insets(4, 0, 4, 0));
        }

        JComponent add(JLabel label, JComponent control, Insets insets) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row++;
            c.insets = insets;
            c.anchor = GridBagConstraints.WEST;
            if (label != null) {
                c.gridx = 0;
                c.insets = new Insets(insets.top, 0, insets.bottom, 12);
                panel.add(label, c);
                c.insets = insets;
            }
            c.gridx = 1;
            c.weightx = 1;
            c.fill = control instanceof JLabel || control instanceof JCheckBox || control instanceof JButton
                    ? GridBagConstraints.NONE
                    : GridBagConstraints.HORIZONTAL;
            panel.add(control, c);
            return control;
        }

        /** A component across both columns. */
        void wide(JComponent component, Insets insets) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.gridwidth = 2;
            c.insets = insets;
            c.anchor = GridBagConstraints.WEST;
            c.fill = component instanceof JLabel ? GridBagConstraints.NONE : GridBagConstraints.BOTH;
            c.weightx = 1;
            panel.add(component, c);
        }
    }

    static void open(InstallerPayload payload) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            // The default look and feel works as well.
        }
        new InstallerWindow(payload, Language.system()).frame.setVisible(true);
    }

    /**
     * Replaces this window with one in {@code chosen}, at the same place and with what the player already chose. The
     * memory boxes keep a typed size; their default entry is a word of the old language and becomes the new one's.
     */
    private void switchTo(Language chosen) {
        InstallerWindow next = new InstallerWindow(payload, chosen);
        next.client.setSelected(client.isSelected());
        next.server.setSelected(server.isSelected());
        next.version.setSelectedItem(version.getSelectedItem());
        next.directory.setText(directory.getText());
        next.serverDirectory.setText(serverDirectory.getText());
        next.profile.setSelected(profile.isSelected());
        next.closeWhenDone.setSelected(closeWhenDone.isSelected());
        next.keepSize(next.maximumMemory, size(maximumMemory, "launcherDefault"), "launcherDefault");
        next.keepSize(next.minimumMemory, size(minimumMemory, "notSet"), "notSet");
        next.log.setText(log.getText());
        next.advancedOpen[0] = advancedOpen[0];
        next.refresh.run();
        next.frame.setLocationRelativeTo(frame);
        next.frame.setVisible(true);
        frame.dispose();
    }

    /** Selects a typed size in a memory box, or its default entry when the size is "". */
    private void keepSize(JComboBox<String> box, String size, String defaultKey) {
        box.setSelectedItem(size.isEmpty() ? text(defaultKey) : size);
    }

    private String text(String key) {
        return text.get(key);
    }

    private void chooseDirectory(JTextField field) {
        JFileChooser chooser = new JFileChooser(field.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getPath());
        }
    }

    private void run(boolean installing) {
        boolean isServer = !client.isSelected();
        Path folder = Path.of((isServer ? serverDirectory : directory).getText().strip());
        String minecraft = (String) version.getSelectedItem();
        JavaMemory memory;
        try {
            memory = installing && (isServer || profile.isSelected())
                    ? JavaMemory.parse(size(maximumMemory, "launcherDefault"), size(minimumMemory, "notSet"))
                    : JavaMemory.LAUNCHER_DEFAULT;
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(), text("badMemory"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (installing && !isServer && MinecraftDirectories.runningLauncher().isPresent()) {
            int answer = JOptionPane.showConfirmDialog(
                    frame,
                    text("launcherRunning"),
                    "Plexus Mod Loader",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) {
                return;
            }
        }
        setBusy(true);
        log.setText("");
        boolean withProfile = profile.isSelected();
        new SwingWorker<List<String>, String>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                if (isServer) {
                    return new ServerInstaller(payload, ServerInstaller.httpDownloader())
                            .install(folder, minecraft, memory, this::publish);
                }
                LauncherProfileInstaller installer = new LauncherProfileInstaller(payload, Clock.systemUTC());
                if (installing) {
                    return installer.install(folder, minecraft, withProfile, memory, this::publish);
                }
                installer.uninstall(folder, minecraft, this::publish);
                return List.of();
            }

            @Override
            protected void process(List<String> lines) {
                lines.forEach(line -> log.append(line + "\n"));
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    List<String> warnings = get();
                    warnings.forEach(warning -> log.append("! " + warning + "\n"));
                    succeeded(installing, isServer, folder, minecraft, warnings);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    String message = cause instanceof InstallerException ? cause.getMessage() : cause.toString();
                    log.append("Error: " + message + "\n");
                    DesktopNotification.show(
                            "Plexus Mod Loader " + payload.plexusVersion(),
                            text(installing ? "notifyFailed" : "notifyRemoveFailed"),
                            true,
                            () -> {},
                            () -> {});
                    JOptionPane.showMessageDialog(frame, message, text("failed"), JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    /** The typed size of a memory box, or "" when it shows the default entry. */
    private String size(JComboBox<String> box, String defaultKey) {
        Object value = box.getEditor().getItem();
        String typed = value == null ? "" : value.toString().strip();
        return typed.equals(text(defaultKey)) ? "" : typed;
    }

    /**
     * Notifies the player and closes the installer, unless they unticked "close" or there are warnings to read;
     * without a notification area the old dialog shows instead.
     */
    private void succeeded(boolean installing, boolean isServer, Path folder, String minecraft, List<String> warnings) {
        boolean close = closeWhenDone.isSelected() && warnings.isEmpty();
        String message;
        Runnable onClick;
        if (isServer) {
            message = text("notifyServerInstalled").formatted(minecraft);
            onClick = () -> openFolder(folder);
        } else if (installing) {
            message = text("notifyInstalled").formatted(minecraft, minecraft);
            onClick = () -> openMods(folder);
        } else {
            message = text("notifyRemoved").formatted(minecraft);
            onClick = () -> {};
        }
        boolean notified = DesktopNotification.show(
                "Plexus Mod Loader " + payload.plexusVersion(),
                message,
                false,
                onClick,
                close ? InstallerWindow::exit : () -> {});
        if (close) {
            frame.setVisible(false);
        }
        if (!notified || !warnings.isEmpty()) {
            if (isServer) {
                finishedServer(folder, minecraft, warnings);
            } else if (installing) {
                finished(folder, minecraft, warnings);
            } else {
                JOptionPane.showMessageDialog(frame, text("removed").formatted(minecraft));
            }
            if (close) {
                exit();
            }
        }
    }

    private static void exit() {
        System.exit(0);
    }

    private void openMods(Path folder) {
        openFolder(folder.resolve("mods"));
    }

    private void openFolder(Path folder) {
        if (Files.isDirectory(folder) && Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(folder.toFile());
            } catch (IOException | UnsupportedOperationException e) {
                log.append("Could not open " + folder + ": " + e.getMessage() + "\n");
            }
        }
    }

    private void finishedServer(Path folder, String minecraft, List<String> warnings) {
        String message = text("serverInstalled").formatted(payload.plexusVersion(), minecraft, folder);
        if (!warnings.isEmpty()) {
            message += "\n\n" + String.join("\n", warnings);
        }
        Object[] options = {text("openFolder"), text("close")};
        int choice = JOptionPane.showOptionDialog(
                frame,
                message,
                "Plexus Mod Loader",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[1]);
        if (choice == 0) {
            openFolder(folder);
        }
    }

    private void finished(Path folder, String minecraft, List<String> warnings) {
        String message = text("installed").formatted(payload.plexusVersion(), minecraft, minecraft);
        if (!warnings.isEmpty()) {
            message += "\n\n" + String.join("\n", warnings);
        }
        Object[] options = {text("openMods"), text("close")};
        int choice = JOptionPane.showOptionDialog(
                frame,
                message,
                "Plexus Mod Loader",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[1]);
        if (choice == 0) {
            openMods(folder);
        }
    }

    private void setBusy(boolean busy) {
        install.setEnabled(!busy);
        uninstall.setEnabled(!busy);
        // The running install writes to this window, so it stays until the install ends.
        languages.setEnabled(!busy);
    }

    private static final Map<String, String> ENGLISH = Map.ofEntries(
            Map.entry("title", "Plexus Mod Loader %s installer"),
            Map.entry("target", "Install for"),
            Map.entry("client", "Client"),
            Map.entry("server", "Server"),
            Map.entry("serverDirectory", "Server folder"),
            Map.entry(
                    "notifyServerInstalled",
                    "Server for Minecraft %s installed. Start it with run.bat or run.sh. Click to open the folder."),
            Map.entry(
                    "serverInstalled",
                    "Plexus Mod Loader %s server for Minecraft %s is installed in\n%s\n\nStart it with run.bat (Windows) or"
                            + " run.sh, or java -jar plexusmodloader-server.jar nogui. It needs Java 25 or newer.\nThe first"
                            + " start stops at Minecraft's EULA: read it and set eula=true in eula.txt.\nPut mods into"
                            + " the mods folder."),
            Map.entry("openFolder", "Open folder"),
            Map.entry("minecraft", "Minecraft version"),
            Map.entry("directory", "Launcher folder"),
            Map.entry("browse", "Browse…"),
            Map.entry("profile", "Create a launcher profile"),
            Map.entry("maxMemory", "Memory (-Xmx)"),
            Map.entry("minMemory", "Starting memory (-Xms)"),
            Map.entry("launcherDefault", "Launcher default"),
            Map.entry("notSet", "Not set"),
            Map.entry("pcMemory", "This PC has %d GB"),
            Map.entry("memoryHint", "For example 4G"),
            Map.entry("badMemory", "Check the memory"),
            Map.entry("closeWhenDone", "Close the installer when done"),
            Map.entry("advancedShow", "Advanced ▸"),
            Map.entry("advancedHide", "Advanced ▾"),
            Map.entry(
                    "notifyInstalled",
                    "Installed for Minecraft %s. Choose the profile \"Plexus Mod Loader %s\" in the Minecraft Launcher."
                            + " Click to open the mods folder."),
            Map.entry("notifyRemoved", "Removed from Minecraft %s."),
            Map.entry("notifyFailed", "Installation failed. The installer window says why."),
            Map.entry("notifyRemoveFailed", "Uninstall failed. The installer window says why."),
            Map.entry("install", "Install"),
            Map.entry("uninstall", "Uninstall"),
            Map.entry(
                    "launcherRunning",
                    "The Minecraft Launcher is running. Close it first, or it may drop the new"
                            + " profile when it exits.\nInstall anyway?"),
            Map.entry(
                    "installed",
                    "Plexus Mod Loader %s for Minecraft %s is installed.\n\nIn the Minecraft Launcher, choose"
                            + " the profile \"Plexus Mod Loader %s\" and press Play.\nPut Plexus mods (and supported Fabric mods) into"
                            + " the mods folder."),
            Map.entry("removed", "Plexus Mod Loader for Minecraft %s is removed. The mods folder was left as it is."),
            Map.entry("failed", "Installation failed"),
            Map.entry("openMods", "Open mods folder"),
            Map.entry("close", "Close"));

    private static final Map<String, String> KOREAN = Map.ofEntries(
            Map.entry("title", "Plexus 모드 로더 %s 설치"),
            Map.entry("target", "설치 대상"),
            Map.entry("client", "클라이언트"),
            Map.entry("server", "서버"),
            Map.entry("serverDirectory", "서버 폴더"),
            Map.entry("notifyServerInstalled", "Minecraft %s 서버를 설치했습니다. run.bat 또는 run.sh로 시작하세요. 누르면 폴더가 열립니다."),
            Map.entry(
                    "serverInstalled",
                    "Minecraft %2$s용 Plexus 모드 로더 %1$s 서버를 설치했습니다.\n%3$s\n\nrun.bat(Windows)이나 run.sh, 또는"
                            + " java -jar plexusmodloader-server.jar nogui로 시작합니다. Java 25 이상이 필요합니다.\n처음 시작하면"
                            + " Minecraft EULA 확인에서 멈춥니다. EULA를 읽고 eula.txt에 eula=true로 적으세요.\n모드는 mods"
                            + " 폴더에 넣습니다."),
            Map.entry("openFolder", "폴더 열기"),
            Map.entry("minecraft", "Minecraft 버전"),
            Map.entry("directory", "런처 폴더"),
            Map.entry("browse", "찾아보기…"),
            Map.entry("profile", "런처 프로필 만들기"),
            Map.entry("maxMemory", "메모리 (-Xmx)"),
            Map.entry("minMemory", "시작 메모리 (-Xms)"),
            Map.entry("launcherDefault", "런처 기본값"),
            Map.entry("notSet", "지정 안 함"),
            Map.entry("pcMemory", "이 PC의 메모리: %d GB"),
            Map.entry("memoryHint", "예: 4G"),
            Map.entry("badMemory", "메모리 값을 확인하세요"),
            Map.entry("closeWhenDone", "완료되면 설치 프로그램 닫기"),
            Map.entry("advancedShow", "고급 설정 ▸"),
            Map.entry("advancedHide", "고급 설정 ▾"),
            Map.entry(
                    "notifyInstalled",
                    "Minecraft %s용으로 설치했습니다. Minecraft Launcher에서 \"Plexus Mod Loader %s\" 프로필을 고르세요."
                            + " 누르면 mods 폴더가 열립니다."),
            Map.entry("notifyRemoved", "Minecraft %s에서 제거했습니다."),
            Map.entry("notifyFailed", "설치하지 못했습니다. 이유는 설치 프로그램 창에 있습니다."),
            Map.entry("notifyRemoveFailed", "제거하지 못했습니다. 이유는 설치 프로그램 창에 있습니다."),
            Map.entry("install", "설치"),
            Map.entry("uninstall", "제거"),
            Map.entry(
                    "launcherRunning",
                    "Minecraft Launcher가 실행 중입니다. 런처가 꺼질 때 새 프로필을 지울 수 있으니" + " 먼저 닫아 주세요.\n그래도 설치할까요?"),
            Map.entry(
                    "installed",
                    "Minecraft %2$s용 Plexus 모드 로더 %1$s를 설치했습니다.\n\nMinecraft Launcher에서"
                            + " \"Plexus Mod Loader %3$s\" 프로필을 고르고 플레이를 누르세요.\nPlexus 모드(와 지원되는 Fabric 모드)는"
                            + " mods 폴더에 넣습니다."),
            Map.entry("removed", "Minecraft %s용 Plexus 모드 로더를 제거했습니다. mods 폴더는 그대로 두었습니다."),
            Map.entry("failed", "설치하지 못했습니다"),
            Map.entry("openMods", "mods 폴더 열기"),
            Map.entry("close", "닫기"));
}
