package com.captainsledger;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.Text;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.Comparator;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;

public class CaptainsLedgerPanel extends PluginPanel {
    private static final int ACCOUNT_ICON_SIZE = 16;
    private static final int NAME_COLUMN_WIDTH = 10;
    private static final int DEP_COLUMN_WIDTH = 34;
    private static final int PAID_COLUMN_WIDTH = 34;
    private static final int ACTION_COLUMN_WIDTH = 54;
    private static final int ROW_HEIGHT_ACTIVE = 62;
    private static final int ROW_HEIGHT_ENDED = 50;

    private final LedgerSessionManager sessionManager;
    private final Map<AccountType, ImageIcon> accountIcons = new EnumMap<>(AccountType.class);

    private final JPanel playersPanel = new JPanel();
    private final JPanel endedPlayersPanel = new JPanel();
    private final JTextField hourlyRateField = new JTextField(8);
    private final JButton startButton = new JButton("Start Trip");
    private final JButton endButton = new JButton("End Trip");
    private final JLabel totalOwedLabel = new JLabel("Total GP: 0k");

    public CaptainsLedgerPanel(CaptainsLedgerPlugin plugin, LedgerSessionManager sessionManager, CaptainsLedgerConfig config) {
        final JButton resetButton = new JButton("Reset All");
        this.sessionManager = sessionManager;
        loadAccountIcons();

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 5, 5, 5);

        c.gridx = 0;
        c.gridy = 0;
        topPanel.add(new JLabel("Hourly Rate (GP):"), c);

        c.gridx = 1;
        hourlyRateField.setText(String.valueOf(config.hourlyRate()));
        hourlyRateField.addActionListener(e -> updateHourlyRate());
        topPanel.add(hourlyRateField, c);

        c.gridx = 0;
        c.gridy = 1;
        startButton.addActionListener(e -> {
            sessionManager.startOrResumeTrip();
            update();
        });
        topPanel.add(startButton, c);

        c.gridx = 1;
        endButton.setEnabled(false);
        endButton.addActionListener(e -> {
            sessionManager.endTrip();
            update();
        });
        topPanel.add(endButton, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        resetButton.addActionListener(e -> resetAll());
        topPanel.add(resetButton, c);

        JLabel titleLabel = new JLabel("Skipper's Ledger");
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setBorder(new EmptyBorder(0, 0, 10, 0));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(topPanel, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);

        playersPanel.setLayout(new BoxLayout(playersPanel, BoxLayout.Y_AXIS));
        playersPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        endedPlayersPanel.setLayout(new BoxLayout(endedPlayersPanel, BoxLayout.Y_AXIS));
        endedPlayersPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel listsPanel = new JPanel();
        listsPanel.setLayout(new BoxLayout(listsPanel, BoxLayout.Y_AXIS));
        listsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel activeSection = new JPanel(new BorderLayout());
        activeSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
        activeSection.setBorder(BorderFactory.createTitledBorder("Active Crew"));
        activeSection.add(createActiveHeaderRow(), BorderLayout.NORTH);
        activeSection.add(playersPanel, BorderLayout.CENTER);

        JPanel endedSection = new JPanel(new BorderLayout());
        endedSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
        endedSection.setBorder(BorderFactory.createTitledBorder("Ended Trips"));
        endedSection.add(createEndedHeaderRow(), BorderLayout.NORTH);
        endedSection.add(endedPlayersPanel, BorderLayout.CENTER);

        listsPanel.add(activeSection);
        listsPanel.add(Box.createVerticalStrut(8));
        listsPanel.add(endedSection);

        JScrollPane scrollPane = new JScrollPane(listsPanel);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        totalOwedLabel.setHorizontalAlignment(SwingConstants.CENTER);
        totalOwedLabel.setForeground(Color.WHITE);
        totalOwedLabel.setBorder(new EmptyBorder(6, 4, 6, 4));
        bottomPanel.add(totalOwedLabel, BorderLayout.NORTH);

        JButton copyButton = new JButton("Copy Summary to Clipboard");
        copyButton.addActionListener(e -> copyToClipboard());
        bottomPanel.add(copyButton, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        update();
    }



    public void update() {
        playersPanel.removeAll();
        endedPlayersPanel.removeAll();

        sessionManager.getSessions().values().stream()
                .filter(session -> !session.isDone())
                .sorted(Comparator.comparing(PlayerSession::getUsername))
                .forEach(session -> playersPanel.add(createPlayerRow(session)));

        sessionManager.getSessions().values().stream()
                .filter(PlayerSession::isDone)
                .sorted(Comparator.comparing(PlayerSession::getUsername))
                .forEach(session -> endedPlayersPanel.add(createEndedPlayerRow(session)));

        boolean active = sessionManager.isTripActive();
        boolean hasExistingSessions = sessionManager.hasSessions();

        startButton.setText(hasExistingSessions ? "Resume Trip" : "Start Trip");
        startButton.setEnabled(!active);
        endButton.setEnabled(active);

        updateTotalOwed();

        playersPanel.revalidate();
        playersPanel.repaint();

        endedPlayersPanel.revalidate();
        endedPlayersPanel.repaint();

        revalidate();
        repaint();
    }

    private JPanel createPlayerRow(PlayerSession session) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBorder(new EmptyBorder(3, 3, 3, 3));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT_ACTIVE));

        long owed = calculateOwed(session);
        String timeStr = formatTime(session.getDisplayedSeconds());
        String moneyText = session.isDepositing() ? "" : " | " + formatMoneyK(owed);
        String safeName = Text.escapeJagex(session.getUsername());
        String nameColor = getPlayerNameColor(session);

        JLabel nameLabel = new JLabel("<html><b><font color='" + nameColor + "'>"
                + safeName + "</font></b><br>"
                + timeStr + moneyText + "</html>");
        nameLabel.setPreferredSize(new Dimension(NAME_COLUMN_WIDTH, ROW_HEIGHT_ACTIVE - 8));
        nameLabel.setMaximumSize(new Dimension(NAME_COLUMN_WIDTH, ROW_HEIGHT_ACTIVE - 8));

        JCheckBox depositingBox = new JCheckBox();
        depositingBox.setToolTipText("Depositing");
        depositingBox.setSelected(session.isDepositing());
        depositingBox.setHorizontalAlignment(SwingConstants.CENTER);
        depositingBox.setMargin(new Insets(0, 0, 0, 0));
        depositingBox.setOpaque(false);
        depositingBox.addActionListener(e -> {
            sessionManager.setDepositing(session.getUsername(), depositingBox.isSelected());
            update();
        });

        JButton endPlayerBtn = createCompactButton("End", "End this player's trip");
        endPlayerBtn.addActionListener(e -> {
            sessionManager.endPlayerTrip(session.getUsername());
            update();
        });

        JButton deleteBtn = createDeleteButton(session);

        JPanel left = new JPanel(new BorderLayout(4, 0));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(NAME_COLUMN_WIDTH + ACCOUNT_ICON_SIZE + 8, ROW_HEIGHT_ACTIVE));
        left.setMaximumSize(new Dimension(NAME_COLUMN_WIDTH + ACCOUNT_ICON_SIZE + 8, ROW_HEIGHT_ACTIVE));
        left.add(createAccountIconLabel(session), BorderLayout.WEST);
        left.add(nameLabel, BorderLayout.CENTER);

        JPanel depColumn = new JPanel(new GridBagLayout());
        depColumn.setOpaque(false);
        depColumn.setPreferredSize(new Dimension(DEP_COLUMN_WIDTH, ROW_HEIGHT_ACTIVE));
        depColumn.setMaximumSize(new Dimension(DEP_COLUMN_WIDTH, ROW_HEIGHT_ACTIVE));
        depColumn.add(depositingBox);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.setPreferredSize(new Dimension(ACTION_COLUMN_WIDTH, ROW_HEIGHT_ACTIVE));
        actions.setMaximumSize(new Dimension(ACTION_COLUMN_WIDTH, ROW_HEIGHT_ACTIVE));

        endPlayerBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        deleteBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        actions.add(Box.createVerticalGlue());
        actions.add(endPlayerBtn);
        actions.add(Box.createVerticalStrut(2));
        actions.add(deleteBtn);
        actions.add(Box.createVerticalGlue());

        JPanel right = new JPanel(new BorderLayout(2, 0));
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(DEP_COLUMN_WIDTH + ACTION_COLUMN_WIDTH, ROW_HEIGHT_ACTIVE));
        right.setMaximumSize(new Dimension(DEP_COLUMN_WIDTH + ACTION_COLUMN_WIDTH, ROW_HEIGHT_ACTIVE));
        right.add(depColumn, BorderLayout.WEST);
        right.add(actions, BorderLayout.EAST);

        row.add(left, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);

        return row;
    }

    private JPanel createEndedPlayerRow(PlayerSession session) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBorder(new EmptyBorder(3, 3, 3, 3));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT_ENDED));

        long owed = calculateOwed(session);
        String owedText = session.isDepositing() ? "" : " | " + formatMoneyK(owed);
        String timeStr = formatTime(session.getDisplayedSeconds());
        String safeName = Text.escapeJagex(session.getUsername());
        String nameColor = getPlayerNameColor(session);

        JLabel nameLabel = new JLabel("<html><b><font color='" + nameColor + "'>"
                + safeName + "</font></b><br>" + timeStr + owedText + "</html>");
        nameLabel.setPreferredSize(new Dimension(NAME_COLUMN_WIDTH, ROW_HEIGHT_ENDED - 8));
        nameLabel.setMaximumSize(new Dimension(NAME_COLUMN_WIDTH, ROW_HEIGHT_ENDED - 8));

        JCheckBox paidBox = new JCheckBox();
        paidBox.setEnabled(!session.isDepositing());
        paidBox.setSelected(session.isPaid());
        paidBox.setMargin(new Insets(0, 0, 0, 0));
        paidBox.setOpaque(false);
        paidBox.addActionListener(e -> sessionManager.setPaid(session.getUsername(), paidBox.isSelected()));

        JButton deleteBtn = createDeleteButton(session);

        JPanel left = new JPanel(new BorderLayout(4, 0));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(NAME_COLUMN_WIDTH + ACCOUNT_ICON_SIZE + 8, ROW_HEIGHT_ENDED));
        left.setMaximumSize(new Dimension(NAME_COLUMN_WIDTH + ACCOUNT_ICON_SIZE + 8, ROW_HEIGHT_ENDED));
        left.add(createAccountIconLabel(session), BorderLayout.WEST);
        left.add(nameLabel, BorderLayout.CENTER);

        JPanel paidColumn = new JPanel(new GridBagLayout());
        paidColumn.setOpaque(false);
        paidColumn.setPreferredSize(new Dimension(PAID_COLUMN_WIDTH, ROW_HEIGHT_ENDED));
        paidColumn.setMaximumSize(new Dimension(PAID_COLUMN_WIDTH, ROW_HEIGHT_ENDED));
        paidColumn.add(paidBox);

        JPanel actions = new JPanel(new GridBagLayout());
        actions.setOpaque(false);
        actions.setPreferredSize(new Dimension(ACTION_COLUMN_WIDTH, ROW_HEIGHT_ENDED));
        actions.setMaximumSize(new Dimension(ACTION_COLUMN_WIDTH, ROW_HEIGHT_ENDED));
        actions.add(deleteBtn);

        JPanel right = new JPanel(new BorderLayout(2, 0));
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(PAID_COLUMN_WIDTH + ACTION_COLUMN_WIDTH, ROW_HEIGHT_ENDED));
        right.setMaximumSize(new Dimension(PAID_COLUMN_WIDTH + ACTION_COLUMN_WIDTH, ROW_HEIGHT_ENDED));
        right.add(paidColumn, BorderLayout.WEST);
        right.add(actions, BorderLayout.EAST);

        row.add(left, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);

        return row;
    }

    private JPanel createActiveHeaderRow() {
        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 3, 2, 3));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

        JLabel nameHeader = createHeaderLabel("Player");
        nameHeader.setPreferredSize(new Dimension(NAME_COLUMN_WIDTH + ACCOUNT_ICON_SIZE + 8, 18));

        JLabel depHeader = createHeaderLabel("Dep");
        depHeader.setHorizontalAlignment(SwingConstants.CENTER);
        depHeader.setPreferredSize(new Dimension(DEP_COLUMN_WIDTH, 18));

        JPanel rightHeaders = new JPanel(new BorderLayout(2, 0));
        rightHeaders.setOpaque(false);
        rightHeaders.setPreferredSize(new Dimension(DEP_COLUMN_WIDTH + ACTION_COLUMN_WIDTH, 18));
        rightHeaders.add(depHeader, BorderLayout.WEST);

        header.add(nameHeader, BorderLayout.CENTER);
        header.add(rightHeaders, BorderLayout.EAST);

        return header;
    }

    private JPanel createEndedHeaderRow() {
        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 3, 2, 3));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

        JLabel nameHeader = createHeaderLabel("Player");
        nameHeader.setPreferredSize(new Dimension(NAME_COLUMN_WIDTH + ACCOUNT_ICON_SIZE + 8, 18));

        JLabel paidHeader = createHeaderLabel("Paid");
        paidHeader.setHorizontalAlignment(SwingConstants.CENTER);
        paidHeader.setPreferredSize(new Dimension(PAID_COLUMN_WIDTH, 18));

        JPanel rightHeaders = new JPanel(new BorderLayout(2, 0));
        rightHeaders.setOpaque(false);
        rightHeaders.setPreferredSize(new Dimension(PAID_COLUMN_WIDTH + ACTION_COLUMN_WIDTH, 18));
        rightHeaders.add(paidHeader, BorderLayout.WEST);

        header.add(nameHeader, BorderLayout.CENTER);
        header.add(rightHeaders, BorderLayout.EAST);

        return header;
    }

    private JLabel createHeaderLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.LIGHT_GRAY);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        return label;
    }

    private String getPlayerNameColor(PlayerSession session) {
        if (session.isDone()) {
            return "#ff5555";
        }

        if (session.isOnBoat()) {
            return "#55ff55";
        }

        return "#ffffff";
    }

    private void loadAccountIcons() {
        for (AccountType accountType : AccountType.values()) {
            String iconPath = accountType.getIconPath();

            if (iconPath == null) {
                continue;
            }

            try {
                java.io.InputStream inputStream = getClass().getResourceAsStream(iconPath);
                if (inputStream == null) {
                    System.out.println("Skipper's Ledger: account icon resource not found: " + iconPath);
                    continue;
                }

                BufferedImage image = ImageIO.read(inputStream);
                if (image == null) {
                    System.out.println("Skipper's Ledger: account icon is not a readable image: " + iconPath);
                    continue;
                }

                Image scaled = image.getScaledInstance(
                        ACCOUNT_ICON_SIZE,
                        ACCOUNT_ICON_SIZE,
                        Image.SCALE_SMOOTH
                );

                accountIcons.put(accountType, new ImageIcon(scaled));
                System.out.println("Skipper's Ledger: loaded account icon " + accountType + " from " + iconPath);
            } catch (IOException | IllegalArgumentException e) {
                System.out.println("Skipper's Ledger: unable to load account icon " + iconPath);
                e.printStackTrace();
            }
        }
    }

    private JLabel createAccountIconLabel(PlayerSession session) {
        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(ACCOUNT_ICON_SIZE + 4, ACCOUNT_ICON_SIZE));
        iconLabel.setMinimumSize(new Dimension(ACCOUNT_ICON_SIZE + 4, ACCOUNT_ICON_SIZE));

        AccountType accountType = session.getAccountType();
        ImageIcon icon = accountIcons.get(accountType);

        if (icon != null) {
            iconLabel.setIcon(icon);
            iconLabel.setToolTipText(formatAccountTypeName(accountType));
        }

        return iconLabel;
    }

    private String formatAccountTypeName(AccountType accountType) {
        if (accountType == null) {
            return "Unknown";
        }

        switch (accountType) {
            case IRONMAN:
                return "Ironman";
            case HARDCORE_IRONMAN:
                return "Hardcore Ironman";
            case ULTIMATE_IRONMAN:
                return "Ultimate Ironman";
            case GROUP_IRONMAN:
                return "Group Ironman";
            case HARDCORE_GROUP_IRONMAN:
                return "Hardcore Group Ironman";
            case REGULAR:
                return "Regular";
            case UNKNOWN:
            default:
                return "Unknown";
        }
    }

    private JButton createDeleteButton(PlayerSession session) {
        JButton deleteBtn = new JButton("×");
        deleteBtn.setForeground(Color.RED);
        deleteBtn.setMargin(new Insets(0, 4, 0, 4));
        deleteBtn.setPreferredSize(new Dimension(42, 18));
        deleteBtn.setMaximumSize(new Dimension(42, 18));
        deleteBtn.setToolTipText("Remove player");
        deleteBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Remove " + session.getUsername() + " from the ledger?",
                    "Confirm",
                    JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                sessionManager.removePlayer(session.getUsername());
                update();
            }
        });
        return deleteBtn;
    }

    private JButton createCompactButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setMargin(new Insets(0, 3, 0, 3));
        button.setPreferredSize(new Dimension(42, 18));
        button.setMaximumSize(new Dimension(42, 18));
        button.setToolTipText(tooltip);
        return button;
    }

    private void updateTotalOwed() {
        long totalGp = sessionManager.getSessions().values().stream()
                .filter(session -> !session.isDepositing())
                .mapToLong(session -> {
                    long currentOwed = session.isPaid() ? 0 : calculateOwed(session);
                    return session.getTotalPaidGp() + currentOwed;
                })
                .sum();

        totalOwedLabel.setText("Total GP: " + formatMoneyK(totalGp));
    }

    private long calculateOwed(PlayerSession session) {
        double hours = session.getDisplayedSeconds() / 3600.0;
        long rawOwed = Math.round(hours * sessionManager.getHourlyRate());
        return Math.round(rawOwed / 1000.0) * 1000;
    }

    private String formatMoneyK(long amount) {
        return String.format("%,dk", amount / 1000);
    }

    private String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return String.format("%dh %02dm", hours, minutes);
    }

    private void updateHourlyRate() {
        try {
            String text = hourlyRateField.getText().replaceAll("[^0-9]", "");
            int rate = Integer.parseInt(text);
            sessionManager.setHourlyRate(rate);
        } catch (Exception ignored) {
            hourlyRateField.setText(String.valueOf(sessionManager.getHourlyRate()));
        }
    }

    private void resetAll() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Reset all players and timers?",
                "Confirm Reset",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            sessionManager.resetAll();
            update();
        }
    }

    private void copyToClipboard() {
        StringBuilder sb = new StringBuilder("Skipper's Ledger - Trip Summary**\n\n");

        sessionManager.getSessions().values().stream()
                .filter(PlayerSession::isDone)
                .filter(s -> !s.isDepositing())
                .sorted(Comparator.comparing(PlayerSession::getUsername))
                .forEach(s -> {
                    long owed = calculateOwed(s);
                    String paidStatus = s.isPaid() ? "Paid" : "Unpaid";

                    sb.append(String.format("%-12s | %s | %s | %s\n",
                            s.getUsername(),
                            formatTime(s.getDisplayedSeconds()),
                            formatMoneyK(owed),
                            paidStatus));
                });

        boolean hasEndedBankingPlayers = sessionManager.getSessions().values().stream()
                .anyMatch(s -> s.isDone() && !s.isDepositing());

        if (!hasEndedBankingPlayers) {
            sb.append("No banking players recorded.");
        }

        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(sb.toString()), null);
    }
}
