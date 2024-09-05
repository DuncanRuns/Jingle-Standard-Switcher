package xyz.duncanruns.jingle.standardswitcher;

import com.google.common.io.Resources;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import xyz.duncanruns.jingle.Jingle;
import xyz.duncanruns.jingle.JingleAppLaunch;
import xyz.duncanruns.jingle.gui.JingleGUI;
import xyz.duncanruns.jingle.instance.OpenedInstance;
import xyz.duncanruns.jingle.plugin.PluginEvents;
import xyz.duncanruns.jingle.plugin.PluginManager;
import xyz.duncanruns.jingle.util.FileUtil;
import xyz.duncanruns.jingle.util.OpenUtil;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StandardSwitcher {
    private static final Path FOLDER = Jingle.FOLDER.resolve("standardswitcher").toAbsolutePath();
    private static final List<String> ILLEGAL_FILE_CHARACTERS = Arrays.asList("/", "\n", "\r", "\t", "\0", "\f", "`", "?", "*", "\\", "<", ">", "|", "\"", ":");

    public JPanel mainPanel;
    private JLabel whatsWrongLabel;
    private JButton createNewFileButton;
    private JButton switchToAnotherFileButton;
    private JPanel instancePanel;
    private JButton openStandardSwitcherFolderButton;
    private JLabel currentFileLabel;

    private boolean exists = false;
    private boolean isManaged = false;
    private boolean isGlobal = false;

    private Path usedFilePath;

    public StandardSwitcher() {
        this.instancePanel.setVisible(false);
        this.whatsWrongLabel.setVisible(false);
        openStandardSwitcherFolderButton.addActionListener(a -> OpenUtil.openFile(FOLDER.toString()));
        createNewFileButton.addActionListener(a -> createNewFileButtonPress());
        switchToAnotherFileButton.addActionListener(a -> switchToAnotherFileButtonPress());
    }

    public static void main(String[] args) throws IOException {
        JingleAppLaunch.launchWithDevPlugin(args, PluginManager.JinglePluginData.fromString(
                Resources.toString(Resources.getResource(StandardSwitcher.class, "/jingle.plugin.json"), Charset.defaultCharset())
        ), StandardSwitcher::initialize);
    }

    public static void initialize() {
        FOLDER.toFile().mkdir();

        StandardSwitcher standardSwitcher = new StandardSwitcher();
        JingleGUI.addPluginTab("Standard Switcher", standardSwitcher.mainPanel, standardSwitcher::reload);
        JingleGUI.get().addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                standardSwitcher.reload();
            }
        });
        PluginEvents.MAIN_INSTANCE_CHANGED.register(standardSwitcher::reload);
    }

    private static void setGlobalFile(OpenedInstance instance, Path newPath) throws IOException {
        FileUtil.writeString(instance.instancePath.resolve("config").resolve("mcsr").resolve("standardsettings.global"), newPath.toString());
    }

    private void switchToAnotherFileButtonPress() {
        OpenedInstance instance = Jingle.getMainInstance().orElse(null);
        if (instance == null) {
            reload();
            return;
        }

        Function<String, String> nameCleaner = s -> s.endsWith(".json") ? s.substring(0, s.length() - 5).trim() : s.trim();

        List<String> existingFileNames = Arrays.stream(Optional.ofNullable(FOLDER.toFile().list()).orElse(new String[]{})).filter(s -> s.endsWith(".json")).map(nameCleaner).collect(Collectors.toList());
        if (existingFileNames.isEmpty()) {
            JOptionPane.showMessageDialog(this.mainPanel, "There are no standard settings files stored with standard switcher!", "Jingle Standard Switcher: No files", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String current = nameCleaner.apply(usedFilePath.getFileName().toString());
        current = existingFileNames.contains(current) ? current : existingFileNames.get(0);
        Object ans = JOptionPane.showInputDialog(this.mainPanel, "Select a file:", "Jingle Standard Switcher: Select file", JOptionPane.QUESTION_MESSAGE, null, existingFileNames.toArray(), current);
        if (ans == null) return;
        Path newPath = FOLDER.resolve(ans + ".json");

        try {
            setGlobalFile(instance, newPath);
        } catch (Exception e) {
            Jingle.logError("Failed to set standardsettings.global!", e);
            JOptionPane.showMessageDialog(this.mainPanel, "Failed to set standardsettings.global! (Check logs)", "Jingle Standard Switcher: Failed to copy", JOptionPane.ERROR_MESSAGE);
            return;
        }

        reload();
    }

    private void createNewFileButtonPress() {
        OpenedInstance instance = Jingle.getMainInstance().orElse(null);
        if (instance == null) {
            reload();
            return;
        }

        Function<String, String> nameCleaner = s -> s == null ? null : (s.endsWith(".json") ? s.substring(0, s.length() - 5).trim() : s.trim());

        List<String> existingFileNames = Arrays.stream(Optional.ofNullable(FOLDER.toFile().list()).orElse(new String[]{})).filter(s -> s.endsWith(".json")).map(nameCleaner).collect(Collectors.toList());

        Function<String, String> asker = warning -> JOptionPane.showInputDialog(this.mainPanel, warning + "Your current standard settings will be copied, enter a new name for this standard settings config:", "Jingle Standard Switcher: Create New File", JOptionPane.QUESTION_MESSAGE);
        Function<String, Integer> wrongChecker = s -> {
            if (s.isEmpty()) return 1; // Empty
            if (existingFileNames.stream().anyMatch(s::equalsIgnoreCase)) return 2; // Already exists
            if (ILLEGAL_FILE_CHARACTERS.stream().anyMatch(s::contains)) return 3; // Invalid name
            return 0;
        };

        String ans = nameCleaner.apply(asker.apply(""));
        int issue;
        while (ans != null && (issue = wrongChecker.apply(ans)) > 0) {
            switch (issue) {
                case 1:
                    ans = asker.apply("Please enter a name!\n");
                    break;
                case 2:
                    ans = asker.apply("A file of this name already exists!\n");
                    break;
                case 3:
                    ans = asker.apply("File name is invalid!\n");
                    break;
            }
        }
        if (ans == null) return;

        Path oldPath = usedFilePath;
        Path newPath = FOLDER.resolve(ans + ".json");
        String s;
        try {
            s = FileUtil.readString(oldPath);
        } catch (Exception e) {
            Jingle.logError("Failed to read standard settings file!", e);
            JOptionPane.showMessageDialog(this.mainPanel, "Failed to read standard settings file! (Check logs)", "Jingle Standard Switcher: Failed to copy", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            FileUtil.writeString(newPath, s);
        } catch (Exception e) {
            Jingle.logError("Failed to write standard settings file!", e);
            JOptionPane.showMessageDialog(this.mainPanel, "Failed to write standard settings file! (Check logs)", "Jingle Standard Switcher: Failed to copy", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            setGlobalFile(instance, newPath);
        } catch (Exception e) {
            Jingle.logError("Failed to set standardsettings.global!", e);
            JOptionPane.showMessageDialog(this.mainPanel, "Failed to set standardsettings.global! (Check logs)", "Jingle Standard Switcher: Failed to copy", JOptionPane.ERROR_MESSAGE);
            return;
        }

        this.reload();
    }

    public void reload() {
        OpenedInstance instance = Jingle.getMainInstance().orElse(null);
        whatsWrongLabel.setVisible(false);
        instancePanel.setVisible(false);
        if (instance == null) {
            warn("Please open an instance!");
            return;
        }

        try {
            usedFilePath = instance.standardSettings.getUsedFilePath();
        } catch (Exception e) {
            Jingle.logError("Failed to get used standard settings path for the instance!", e);
            warn("Failed to get standard settings path! (Check logs)");
            return;
        }

        exists = Files.exists(usedFilePath);
        isManaged = usedFilePath.getParent().toAbsolutePath().equals(FOLDER);
        isGlobal = !instance.instancePath.resolve("config").resolve("mcsr").resolve("standardsettings.json").toAbsolutePath().equals(usedFilePath.toAbsolutePath());

        this.instancePanel.setVisible(true);

        if (!exists) {
            this.currentFileLabel.setText("Current Standard Settings File: (Does Not Exist)");
        } else if (!isGlobal) {
            this.currentFileLabel.setText("Current Standard Settings File: Non-global");
        } else if (isManaged) {
            this.currentFileLabel.setText("Current Standard Settings File: " + usedFilePath.getFileName().toString());
        } else {
            String string = usedFilePath.toString();
            this.currentFileLabel.setText("Current Standard Settings File: " + (string.length() > 50 ? ("..." + string.substring(string.length() - 47)) : string));
        }
    }

    private void warn(String text) {
        whatsWrongLabel.setVisible(true);
        whatsWrongLabel.setText(text);
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane1 = new JScrollPane();
        mainPanel.add(scrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        scrollPane1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(5, 1, new Insets(5, 5, 5, 5), -1, -1));
        scrollPane1.setViewportView(panel1);
        panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        whatsWrongLabel = new JLabel();
        whatsWrongLabel.setText("Please open an instance!");
        panel1.add(whatsWrongLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        instancePanel = new JPanel();
        instancePanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(instancePanel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        instancePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        currentFileLabel = new JLabel();
        currentFileLabel.setText("Current Standard Settings File: (Unknown)");
        instancePanel.add(currentFileLabel, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        createNewFileButton = new JButton();
        createNewFileButton.setText("Create New File");
        instancePanel.add(createNewFileButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setForeground(new Color(-65536));
        label1.setText("Please ensure that the in-game standard settings menu is closed before switching!");
        instancePanel.add(label1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        switchToAnotherFileButton = new JButton();
        switchToAnotherFileButton.setText("Switch to Another File");
        instancePanel.add(switchToAnotherFileButton, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 20, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        openStandardSwitcherFolderButton = new JButton();
        openStandardSwitcherFolderButton.setText("Open Standard Switcher Folder");
        panel1.add(openStandardSwitcherFolderButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
