package com.captainsledger;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.Text;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
public class CaptainsLedgerPanel extends PluginPanel {
    private static final int ACCOUNT_ICON_SIZE = 16;
    private static final int NAME_COLUMN_WIDTH = 10;
    private static final int DEP_COLUMN_WIDTH = 34;
    private static final int PAID_COLUMN_WIDTH = 34;
    private static final int ACTION_COLUMN_WIDTH = 58;
    private static final int ROW_HEIGHT_ACTIVE = 62;
    private static final int ROW_HEIGHT_ENDED = 50;

    private final LedgerSessionManager sessionManager;
    private final Client client;
    private final SpriteManager spriteManager;
    private final ItemManager itemManager;
    private final Map<AccountType, ImageIcon> accountIcons = new EnumMap<>(AccountType.class);

    private final JPanel playersPanel = new JPanel();
    private final JPanel endedPlayersPanel = new JPanel();
    private final JPanel ignoredPlayersPanel = new JPanel();
    private final JPanel ignoredSection = new JPanel(new BorderLayout());
    private final JTextField hourlyRateField = new JTextField(8);
    private final JButton startButton = new JButton("Start Trip");
    private final JButton endButton = new JButton("End Trip");
    private final JLabel totalOwedLabel = new JLabel("Total GP: 0k");
    private final TitledBorder activeCrewBorder = BorderFactory.createTitledBorder("Active Crew (0/9)");


    public CaptainsLedgerPanel(
            CaptainsLedgerPlugin plugin,
            LedgerSessionManager sessionManager,
            CaptainsLedgerConfig config,
            Client client,
            SpriteManager spriteManager,
            ItemManager itemManager
    ) {
        final JButton resetButton = new JButton("Reset All");
        final JButton addTestPlayersButton = new JButton("Add Test Crew");
        this.sessionManager = sessionManager;
        this.client = client;
        this.spriteManager = spriteManager;
        this.itemManager = itemManager;
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

        /* ADD THIS TO TEST WITH FULL CREW
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 2;
        addTestPlayersButton.addActionListener(e -> {
            sessionManager.addTestPlayers();
            update();
        });
        */

        topPanel.add(addTestPlayersButton, c);

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

        ignoredPlayersPanel.setLayout(new BoxLayout(ignoredPlayersPanel, BoxLayout.Y_AXIS));
        ignoredPlayersPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel listsPanel = new JPanel();
        listsPanel.setLayout(new BoxLayout(listsPanel, BoxLayout.Y_AXIS));
        listsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel activeSection = new JPanel(new BorderLayout());
        activeSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
        activeSection.setBorder(activeCrewBorder);
        activeSection.add(createActiveHeaderRow(), BorderLayout.NORTH);
        activeSection.add(playersPanel, BorderLayout.CENTER);

        JPanel endedSection = new JPanel(new BorderLayout());
        endedSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
        endedSection.setBorder(BorderFactory.createTitledBorder("Payment Owed"));
        endedSection.add(createEndedHeaderRow(), BorderLayout.NORTH);
        endedSection.add(endedPlayersPanel, BorderLayout.CENTER);

        ignoredSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
        ignoredSection.setBorder(BorderFactory.createTitledBorder("Ignored Players"));
        ignoredSection.add(ignoredPlayersPanel, BorderLayout.CENTER);

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
        ignoredPlayersPanel.removeAll();

        activeCrewBorder.setTitle("Active Crew ("
                + sessionManager.getActiveCrewCount()
                + "/"
                + sessionManager.getMaxActiveCrew()
                + ")");

        sessionManager.getSessions().values().stream()
                .sorted(Comparator.comparing(PlayerSession::getUsername))
                .forEach(session -> playersPanel.add(createPlayerRow(session)));

        sessionManager.getPaymentOwed().values().stream()
                .sorted(Comparator.comparing(LedgerSessionManager.PaymentOwed::getUsername))
                .forEach(payment -> endedPlayersPanel.add(createPaymentOwedRow(payment)));

        sessionManager.getIgnoredPlayers().stream()
                .sorted(String::compareToIgnoreCase)
                .forEach(username -> ignoredPlayersPanel.add(createIgnoredPlayerRow(username)));

        Container parent = ignoredSection.getParent();
        if (sessionManager.hasIgnoredPlayers() && parent == null) {
            JPanel listsPanel = (JPanel) ((JScrollPane) getComponent(1)).getViewport().getView();
            listsPanel.add(Box.createVerticalStrut(8));
            listsPanel.add(ignoredSection);
        } else if (!sessionManager.hasIgnoredPlayers() && parent != null) {
            parent.remove(ignoredSection);
        }

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

        ignoredPlayersPanel.revalidate();
        ignoredPlayersPanel.repaint();

        revalidate();
        repaint();
    }

    private JPanel createPlayerRow(PlayerSession session) {
        if (!session.isDepositing() && session.isAwaitingPaymentConfirmation()) {
            return createPaymentConfirmationRow(session);
        }

        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBorder(new EmptyBorder(3, 3, 3, 3));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT_ACTIVE));

        long owed = sessionManager.calculateCurrentOwed(session);
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
        depositingBox.setEnabled(!session.isWaitingForCrewSlot());
        depositingBox.setHorizontalAlignment(SwingConstants.CENTER);
        depositingBox.setMargin(new Insets(0, 0, 0, 0));
        depositingBox.setOpaque(false);
        depositingBox.addActionListener(e -> {
            sessionManager.setDepositing(session.getUsername(), depositingBox.isSelected());
            update();
        });

        JButton actionButton = session.isDepositing()
                ? createEndDepositingTripButton(session)
                : createCalculatePaymentButton(session);
        actionButton.setEnabled(!session.isWaitingForCrewSlot());

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

        JPanel actions = new JPanel(new GridBagLayout());
        actions.setOpaque(false);
        actions.setPreferredSize(new Dimension(ACTION_COLUMN_WIDTH, ROW_HEIGHT_ACTIVE));
        actions.setMaximumSize(new Dimension(ACTION_COLUMN_WIDTH, ROW_HEIGHT_ACTIVE));
        actions.add(actionButton);

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

    private JPanel createPaymentConfirmationRow(PlayerSession session) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBorder(new EmptyBorder(3, 3, 3, 3));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT_ACTIVE));

        String safeName = Text.escapeJagex(session.getUsername());
        JLabel label = new JLabel("<html><b>" + safeName + "</b><br>Continue trip?</html>");
        label.setForeground(Color.WHITE);

        JPanel left = new JPanel(new BorderLayout(4, 0));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(NAME_COLUMN_WIDTH + ACCOUNT_ICON_SIZE + 8, ROW_HEIGHT_ACTIVE));
        left.setMaximumSize(new Dimension(NAME_COLUMN_WIDTH + ACCOUNT_ICON_SIZE + 8, ROW_HEIGHT_ACTIVE));
        left.add(createAccountIconLabel(session), BorderLayout.WEST);
        left.add(label, BorderLayout.CENTER);

        JButton yesButton = createCompactButton("Yes", "Add payment owed and continue trip for this player");
        yesButton.addActionListener(e -> {
            sessionManager.confirmPaymentAndContinue(session.getUsername());
            update();
        });

        JButton noButton = createCompactButton("No", "Add payment owed and remove from active crew");
        noButton.addActionListener(e -> {
            sessionManager.confirmPaymentAndRemove(session.getUsername());
            update();
        });

        JButton cancelButton = createCompactButton("×", "Cancel");
        cancelButton.setForeground(Color.RED);
        cancelButton.addActionListener(e -> {
            sessionManager.cancelPaymentCalculation(session.getUsername());
            update();
        });

        JPanel yesNoButtons = new JPanel();
        yesNoButtons.setOpaque(false);
        yesNoButtons.setLayout(new BoxLayout(yesNoButtons, BoxLayout.Y_AXIS));

        yesButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        noButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        yesNoButtons.add(Box.createVerticalGlue());
        yesNoButtons.add(yesButton);
        yesNoButtons.add(Box.createVerticalStrut(2));
        yesNoButtons.add(noButton);
        yesNoButtons.add(Box.createVerticalGlue());

        JPanel cancelColumn = new JPanel(new GridBagLayout());
        cancelColumn.setOpaque(false);
        cancelColumn.setPreferredSize(new Dimension(24, ROW_HEIGHT_ACTIVE));
        cancelColumn.setMaximumSize(new Dimension(24, ROW_HEIGHT_ACTIVE));
        cancelColumn.add(cancelButton);

        JPanel buttonColumns = new JPanel(new BorderLayout(2, 0));
        buttonColumns.setOpaque(false);
        buttonColumns.add(yesNoButtons, BorderLayout.CENTER);
        buttonColumns.add(cancelColumn, BorderLayout.EAST);

        row.add(left, BorderLayout.CENTER);
        row.add(buttonColumns, BorderLayout.EAST);

        return row;
    }

    private JPanel createPaymentOwedRow(LedgerSessionManager.PaymentOwed payment) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBorder(new EmptyBorder(3, 3, 3, 3));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT_ENDED));

        String timeStr = formatTime(payment.getSeconds());
        String safeName = Text.escapeJagex(payment.getUsername());

        JLabel nameLabel = new JLabel("<html><b><font color='#ffcc55'>"
                + safeName + "</font></b><br>" + timeStr + " | " + formatMoneyK(payment.getGp()) + "</html>");
        nameLabel.setPreferredSize(new Dimension(NAME_COLUMN_WIDTH, ROW_HEIGHT_ENDED - 8));
        nameLabel.setMaximumSize(new Dimension(NAME_COLUMN_WIDTH, ROW_HEIGHT_ENDED - 8));

        JCheckBox paidBox = new JCheckBox();
        paidBox.setSelected(payment.isPaid());
        paidBox.setMargin(new Insets(0, 0, 0, 0));
        paidBox.setOpaque(false);
        paidBox.addActionListener(e -> {
            sessionManager.setPaid(payment.getUsername(), paidBox.isSelected());
            update();
        });

        JPanel left = new JPanel(new BorderLayout(4, 0));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(NAME_COLUMN_WIDTH + ACCOUNT_ICON_SIZE + 8, ROW_HEIGHT_ENDED));
        left.setMaximumSize(new Dimension(NAME_COLUMN_WIDTH + ACCOUNT_ICON_SIZE + 8, ROW_HEIGHT_ENDED));
        left.add(createAccountIconLabel(payment.getAccountType()), BorderLayout.WEST);
        left.add(nameLabel, BorderLayout.CENTER);

        JPanel paidColumn = new JPanel(new GridBagLayout());
        paidColumn.setOpaque(false);
        paidColumn.setPreferredSize(new Dimension(PAID_COLUMN_WIDTH, ROW_HEIGHT_ENDED));
        paidColumn.setMaximumSize(new Dimension(PAID_COLUMN_WIDTH, ROW_HEIGHT_ENDED));
        paidColumn.add(paidBox);

        JPanel emptyActionColumn = new JPanel();
        emptyActionColumn.setOpaque(false);
        emptyActionColumn.setPreferredSize(new Dimension(ACTION_COLUMN_WIDTH, ROW_HEIGHT_ENDED));
        emptyActionColumn.setMaximumSize(new Dimension(ACTION_COLUMN_WIDTH, ROW_HEIGHT_ENDED));

        JPanel right = new JPanel(new BorderLayout(2, 0));
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(PAID_COLUMN_WIDTH + ACTION_COLUMN_WIDTH, ROW_HEIGHT_ENDED));
        right.setMaximumSize(new Dimension(PAID_COLUMN_WIDTH + ACTION_COLUMN_WIDTH, ROW_HEIGHT_ENDED));
        right.add(paidColumn, BorderLayout.WEST);
        right.add(emptyActionColumn, BorderLayout.EAST);

        row.add(left, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);

        return row;
    }


    private JPanel createIgnoredPlayerRow(String username) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBorder(new EmptyBorder(3, 3, 3, 3));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel nameLabel = new JLabel(Text.escapeJagex(username));
        nameLabel.setForeground(Color.WHITE);

        JButton removeButton = new JButton("Remove");
        removeButton.setMargin(new Insets(0, 4, 0, 4));
        removeButton.setPreferredSize(new Dimension(72, 20));
        removeButton.setMaximumSize(new Dimension(72, 20));
        removeButton.setToolTipText("Stop ignoring this player");
        removeButton.addActionListener(e -> {
            sessionManager.stopIgnoringPlayer(username);
            update();
        });

        JPanel buttonPanel = new JPanel(new GridBagLayout());
        buttonPanel.setOpaque(false);
        buttonPanel.setPreferredSize(new Dimension(76, 26));
        buttonPanel.setMaximumSize(new Dimension(76, 26));
        buttonPanel.add(removeButton);

        row.add(nameLabel, BorderLayout.CENTER);
        row.add(buttonPanel, BorderLayout.EAST);

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
        if (session.isWaitingForCrewSlot()) {
            return "#888888";
        }

        if (session.isDone()) {
            return "#ff5555";
        }

        if (session.isOnBoat()) {
            return "#55ff55";
        }

        return "#ffffff";
    }

    private void loadAccountIcons() {
        IndexedSprite[] modIcons = client.getModIcons();

        if (modIcons == null) {
            log.debug("Skipper's Ledger mod icons are not available yet");
            return;
        }

        for (AccountType accountType : AccountType.values()) {
            if (!accountType.hasModIcon()) {
                continue;
            }

            int modIcon = accountType.getModIcon();

            if (modIcon < 0 || modIcon >= modIcons.length || modIcons[modIcon] == null) {
                log.debug("Skipper's Ledger account mod icon not found: {} ({})", accountType, modIcon);
                continue;
            }

            accountIcons.put(accountType, scaleIcon(toBufferedImage(modIcons[modIcon]), ACCOUNT_ICON_SIZE, ACCOUNT_ICON_SIZE));
            log.debug("Skipper's Ledger loaded account mod icon {}", accountType);
        }
    }

    private BufferedImage toBufferedImage(IndexedSprite sprite) {
        BufferedImage image = new BufferedImage(sprite.getWidth(), sprite.getHeight(), BufferedImage.TYPE_INT_ARGB);
        byte[] pixels = sprite.getPixels();
        int[] palette = sprite.getPalette();

        for (int y = 0; y < sprite.getHeight(); y++) {
            for (int x = 0; x < sprite.getWidth(); x++) {
                int index = pixels[y * sprite.getWidth() + x] & 0xFF;

                if (index == 0) {
                    image.setRGB(x, y, 0x00000000);
                } else {
                    image.setRGB(x, y, 0xFF000000 | palette[index]);
                }
            }
        }

        return image;
    }

    private ImageIcon scaleIcon(BufferedImage image, int width, int height) {
        Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private JLabel createAccountIconLabel(PlayerSession session) {
        return createAccountIconLabel(session.getAccountType());
    }

    private JLabel createAccountIconLabel(AccountType accountType) {
        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(ACCOUNT_ICON_SIZE + 4, ACCOUNT_ICON_SIZE));
        iconLabel.setMinimumSize(new Dimension(ACCOUNT_ICON_SIZE + 4, ACCOUNT_ICON_SIZE));

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
            case REGULAR:
                return "Regular";
            case UNKNOWN:
            default:
                return "Unknown";
        }
    }

    private JButton createCalculatePaymentButton(PlayerSession session) {
        JButton button = new JButton();

        AsyncBufferedImage coinsImage = itemManager.getImage(995, 100, true);
        button.setIcon(createCroppedCoinIcon(coinsImage, 14, 14));
        coinsImage.onLoaded(() -> SwingUtilities.invokeLater(() -> {
            button.setIcon(createCroppedCoinIcon(coinsImage, 14, 14));
            button.revalidate();
            button.repaint();
        }));

        button.setText(null);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setVerticalAlignment(SwingConstants.CENTER);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setVerticalTextPosition(SwingConstants.CENTER);
        button.setIconTextGap(0);

        button.setMargin(new Insets(0, 0, 0, 0));
        button.setPreferredSize(new Dimension(42, 22));
        button.setMaximumSize(new Dimension(42, 22));
        button.setToolTipText("Store payment");
        button.addActionListener(e -> {
            sessionManager.requestPaymentCalculation(session.getUsername());
            update();
        });

        return button;
    }

    private ImageIcon createCroppedCoinIcon(BufferedImage image, int width, int height) {
        int cropX = 0;
        int cropY = 10;
        int cropWidth = image.getWidth();
        int cropHeight = image.getHeight() - cropY;

        BufferedImage cropped = image.getSubimage(cropX, cropY, cropWidth, cropHeight);
        return scaleIcon(cropped, width, height);
    }

    private JButton createEndDepositingTripButton(PlayerSession session) {
        JButton button = createCompactButton("End", "End this depositing player's trip and ignore them");
        button.addActionListener(e -> {
            sessionManager.endDepositingPlayerTrip(session.getUsername());
            update();
        });
        return button;
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
        long totalGp = sessionManager.getPaymentOwed().values().stream()
                .mapToLong(LedgerSessionManager.PaymentOwed::getGp)
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
        StringBuilder sb = new StringBuilder();

        long totalGp = sessionManager.getPaymentOwed().values().stream()
                .mapToLong(LedgerSessionManager.PaymentOwed::getGp)
                .sum();

        sb.append("**Skipper's Ledger - Payment Summary**\n");

        if (sessionManager.getPaymentOwed().isEmpty()) {
            sb.append("_No payments owed._");
        } else {
            sessionManager.getPaymentOwed().values().stream()
                    .sorted(Comparator.comparing(LedgerSessionManager.PaymentOwed::getUsername))
                    .forEach(payment -> {
                        String paidStatus = payment.isPaid() ? "✅ Paid" : "❌ Unpaid";

                        sb.append("• **")
                                .append(escapeDiscordMarkdown(payment.getUsername()))
                                .append("** — ")
                                .append(formatTime(payment.getSeconds()))
                                .append(" — ")
                                .append(formatMoneyK(payment.getGp()))
                                .append(" — ")
                                .append(paidStatus)
                                .append("\n");
                    });
        }

        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(sb.toString()), null);
    }

    private String escapeDiscordMarkdown(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace("|", "\\|");
    }
}