package com.captainsledger;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.util.Text;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LedgerSessionManager
{
    private static final int MAX_ACTIVE_CREW = 9;

    private final Client client;
    private final HiscoreClient hiscoreClient;
    private final ExecutorService hiscoreExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, AccountType> accountTypeCache = new HashMap<>();
    private final Set<String> pendingAccountTypeLookups = new HashSet<>();
    private Runnable panelUpdateCallback = () -> {};

    @Getter
    private final Map<String, PlayerSession> sessions = new HashMap<>();
    @Getter
    private final Map<String, PaymentOwed> paymentOwed = new HashMap<>();
    @Getter
    private final Set<String> ignoredPlayers = new HashSet<>();
    private final Set<String> currentlySeenPlayers = new HashSet<>();
    private final Set<String> testPlayers = new HashSet<>();
    private final Map<String, Player> knownPlayers = new HashMap<>();

    @Getter
    private boolean tripActive = false;
    @Getter
    private int hourlyRate = 1_000_000;

    public LedgerSessionManager(Client client, HiscoreClient hiscoreClient)
    {
        this.client = client;
        this.hiscoreClient = hiscoreClient;
    }

    public void setPanelUpdateCallback(Runnable panelUpdateCallback)
    {
        this.panelUpdateCallback = panelUpdateCallback == null ? () -> {} : panelUpdateCallback;
    }

    public void shutDown()
    {
        hiscoreExecutor.shutdownNow();
    }

    public boolean hasSessions()
    {
        return !sessions.isEmpty();
    }

    public int getMaxActiveCrew()
    {
        return MAX_ACTIVE_CREW;
    }

    public int getActiveCrewCount()
    {
        return (int) sessions.values().stream()
                .filter(session -> !session.isWaitingForCrewSlot())
                .count();
    }

    public boolean hasIgnoredPlayers()
    {
        return !ignoredPlayers.isEmpty();
    }

    public void startOrResumeTrip()
    {
        if (sessions.isEmpty())
        {
            startTrip();
        }
        else
        {
            resumeTrip();
        }
    }

    public void startTrip()
    {
        tripActive = true;
        currentlySeenPlayers.clear();

        scanKnownPlayers();
    }

    public void resumeTrip()
    {
        tripActive = true;
        currentlySeenPlayers.clear();

        Instant now = Instant.now();

        sessions.values().forEach(session -> {
            session.setDone(false);
            session.setIsOnBoat(true);
            session.setJoinTime(now);
            session.setGracePeriodEnd(null);
            session.setLeftBoatTime(null);
            session.setStoredSeconds(0);
            session.setReturnConfirmationStartTime(null);
            session.setAwaitingPaymentConfirmation(false);

            currentlySeenPlayers.add(session.getUsername());
        });

        scanKnownPlayers();
    }

    public void endTrip()
    {
        sessions.values().forEach(session -> {
            session.setAccumulatedSeconds(session.getDisplayedSeconds());
            session.setStoredSeconds(0);
            session.setJoinTime(null);
            session.setIsOnBoat(false);
            session.setGracePeriodEnd(null);
            session.setLeftBoatTime(null);
            session.setReturnConfirmationStartTime(null);
            session.setAwaitingPaymentConfirmation(false);
        });

        tripActive = false;
        currentlySeenPlayers.clear();
    }

    public void resetAll()
    {
        sessions.clear();
        paymentOwed.clear();
        currentlySeenPlayers.clear();
        ignoredPlayers.clear();
        testPlayers.clear();
        tripActive = false;
    }

    public void onPlayerSpawned(Player player)
    {
        if (player == null || player.getName() == null || isLocalPlayer(player))
        {
            return;
        }

        knownPlayers.put(player.getName(), player);

        if (tripActive)
        {
            addPlayerIfOnSameTile(player);
        }
    }

    public void onPlayerDespawned(Player player)
    {
        if (player == null || player.getName() == null || isLocalPlayer(player))
        {
            return;
        }

        String name = player.getName();
        knownPlayers.remove(name);

        if (tripActive)
        {
            markPlayerOffBoat(name);
        }
    }



    public void onGameTick()
    {
        if (!tripActive)
        {
            return;
        }

        Set<String> playersCurrentlyOnTile = new HashSet<>();

        Player local = client.getLocalPlayer();
        if (local != null && isPlayerOnSameTile(local))
        {
            playersCurrentlyOnTile.add(local.getName());
            addPlayerIfOnSameTile(local);
        }

        for (Player player : knownPlayers.values())
        {
            if (isPlayerOnSameTile(player))
            {
                playersCurrentlyOnTile.add(player.getName());
                addPlayerIfOnSameTile(player);
            }
        }

        WorldView worldView = client.getTopLevelWorldView();
        if (worldView != null && worldView.players() != null)
        {
            for (Player player : worldView.players())
            {
                if (isPlayerOnSameTile(player))
                {
                    playersCurrentlyOnTile.add(player.getName());
                    addPlayerIfOnSameTile(player);
                }
            }
        }

        for (String name : new HashSet<>(currentlySeenPlayers))
        {
            if (!playersCurrentlyOnTile.contains(name))
            {
                markPlayerOffBoat(name);
            }
        }

        promoteWaitingPlayers();
    }

    private void scanKnownPlayers()
    {
        for (Player player : knownPlayers.values())
        {
            addPlayerIfOnSameTile(player);
        }

        WorldView worldView = client.getTopLevelWorldView();
        if (worldView != null && worldView.players() != null)
        {
            for (Player player : worldView.players())
            {
                addPlayerIfOnSameTile(player);
            }
        }
    }

    private boolean isPlayerOnSameTile(Player player)
    {
        if (player == null || player.getName() == null || player.getWorldLocation() == null)
        {
            return false;
        }

        if (isLocalPlayer(player))
        {
            return false;
        }

        if (ignoredPlayers.contains(player.getName()))
        {
            return false;
        }

        Player local = client.getLocalPlayer();
        if (local == null || local.getWorldLocation() == null)
        {
            return false;
        }

        WorldPoint playerLocation = player.getWorldLocation();
        WorldPoint localLocation = local.getWorldLocation();

        return playerLocation.getX() == localLocation.getX()
                && playerLocation.getY() == localLocation.getY()
                && playerLocation.getPlane() == localLocation.getPlane();
    }

    private boolean isLocalPlayer(Player player)
    {
        Player local = client.getLocalPlayer();

        return local != null
                && local.getName() != null
                && player != null
                && player.getName() != null
                && local.getName().equals(player.getName());
    }

    private void markPlayerOffBoat(String name)
    {
        if (testPlayers.contains(name))
        {
            currentlySeenPlayers.add(name);

            PlayerSession testSession = sessions.get(name);
            if (testSession != null)
            {
                testSession.setIsOnBoat(true);
            }

            return;
        }

        currentlySeenPlayers.remove(name);

        PlayerSession session = sessions.get(name);
        if (session == null || !session.isOnBoat())
        {
            return;
        }

        long timeAtDetectionLoss = session.getTotalSeconds();

        session.setStoredSeconds(timeAtDetectionLoss);
        session.setIsOnBoat(false);
        session.setDone(false);
        session.setLeftBoatTime(null);
        session.setGracePeriodEnd(null);
    }

    private void addPlayerIfOnSameTile(Player player)
    {
        if (!isPlayerOnSameTile(player))
        {
            return;
        }

        String name = player.getName();

        if (!sessions.containsKey(name) && isNewPlayerDetectionDisabled())
        {
            return;
        }

        currentlySeenPlayers.add(name);

        PlayerSession session = sessions.computeIfAbsent(name, n -> {
            PlayerSession newSession = new PlayerSession(n, true);
            newSession.setWaitingForCrewSlot(getActiveCrewCount() >= MAX_ACTIVE_CREW);
            return newSession;
        });

        queueAccountTypeLookup(session);

        if (!session.isOnBoat())
        {
            session.setIsOnBoat(true);
            session.setLeftBoatTime(null);
            session.setGracePeriodEnd(null);
        }

        promoteWaitingPlayers();
    }

    private boolean isNewPlayerDetectionDisabled()
    {
        return !sessions.isEmpty() && currentlySeenPlayers.isEmpty();
    }

    private void promoteWaitingPlayers()
    {
        int activeCrewCount = getActiveCrewCount();

        if (activeCrewCount >= MAX_ACTIVE_CREW)
        {
            return;
        }

        sessions.values().stream()
                .filter(PlayerSession::isWaitingForCrewSlot)
                .sorted((a, b) -> {
                    Instant aJoin = a.getJoinTime();
                    Instant bJoin = b.getJoinTime();

                    if (aJoin == null && bJoin == null)
                    {
                        return a.getUsername().compareToIgnoreCase(b.getUsername());
                    }

                    if (aJoin == null)
                    {
                        return 1;
                    }

                    if (bJoin == null)
                    {
                        return -1;
                    }

                    return aJoin.compareTo(bJoin);
                })
                .limit(MAX_ACTIVE_CREW - activeCrewCount)
                .forEach(session -> session.setWaitingForCrewSlot(false));
    }

    private void queueAccountTypeLookup(PlayerSession session)
    {
        if (session == null || session.getAccountType() != AccountType.UNKNOWN)
        {
            return;
        }

        String username = session.getUsername();
        String cacheKey = normalizeUsername(username);

        AccountType cachedType = accountTypeCache.get(cacheKey);
        if (cachedType != null)
        {
            session.setAccountType(cachedType);
            applyDefaultDepositingForAccountType(session, cachedType);
            return;
        }

        if (!pendingAccountTypeLookups.add(cacheKey))
        {
            return;
        }

        hiscoreExecutor.submit(() -> {
            AccountType accountType = lookupAccountType(username);

            synchronized (this)
            {
                accountTypeCache.put(cacheKey, accountType);
                pendingAccountTypeLookups.remove(cacheKey);

                PlayerSession currentSession = sessions.get(username);
                if (currentSession != null)
                {
                    currentSession.setAccountType(accountType);
                    applyDefaultDepositingForAccountType(currentSession, accountType);
                }
            }

            panelUpdateCallback.run();
        });
    }

    private void applyDefaultDepositingForAccountType(PlayerSession session, AccountType accountType)
    {
        if (session == null || accountType == null)
        {
            return;
        }

        if (session.isDepositingManuallyChanged())
        {
            return;
        }

        if (accountType == AccountType.UNKNOWN)
        {
            return;
        }

        session.setIsDepositing(!accountType.isIronman());
    }

    private AccountType lookupAccountType(String username)
    {
        if (isOnHiscoreEndpoint(username, HiscoreEndpoint.HARDCORE_IRONMAN))
        {
            return AccountType.HARDCORE_IRONMAN;
        }

        if (isOnHiscoreEndpoint(username, HiscoreEndpoint.ULTIMATE_IRONMAN))
        {
            return AccountType.ULTIMATE_IRONMAN;
        }

        if (isOnHiscoreEndpoint(username, HiscoreEndpoint.IRONMAN))
        {
            return AccountType.IRONMAN;
        }

        return AccountType.REGULAR;
    }

    private boolean isOnHiscoreEndpoint(String username, HiscoreEndpoint endpoint)
    {
        try
        {
            HiscoreResult result = hiscoreClient.lookup(username, endpoint);
            return result != null;
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    private String normalizeUsername(String username)
    {
        return username == null
                ? ""
                : username.replace('\u00A0', ' ')
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    public void setHourlyRate(int rate)
    {
        this.hourlyRate = Math.max(0, rate);
    }

    public void removePlayer(String username)
    {
        sessions.remove(username);
        currentlySeenPlayers.remove(username);
        testPlayers.remove(username);
        ignoredPlayers.add(username);
        promoteWaitingPlayers();
    }

    public void stopIgnoringPlayer(String username)
    {
        ignoredPlayers.remove(username);
    }

    public void addTestPlayers()
    {
        tripActive = true;

        for (int i = 1; i <= MAX_ACTIVE_CREW; i++)
        {
            String username = "player" + i;

            ignoredPlayers.remove(username);
            testPlayers.add(username);
            currentlySeenPlayers.add(username);

            PlayerSession session = sessions.computeIfAbsent(username, name -> new PlayerSession(name, false));
            session.setIsOnBoat(true);
            session.setWaitingForCrewSlot(false);
            session.setDone(false);
            session.setAwaitingPaymentConfirmation(false);

            if (session.getJoinTime() == null)
            {
                session.setJoinTime(Instant.now());
            }
        }

        promoteWaitingPlayers();
    }

    public void requestPaymentCalculation(String username)
    {
        PlayerSession session = sessions.get(username);
        if (session == null || session.isDepositing())
        {
            return;
        }

        long totalSecondsForPayment = !session.isOnBoat() && session.getStoredSeconds() > 0
                ? session.getStoredSeconds()
                : session.getTotalSeconds();

        long payableSeconds = Math.max(0, totalSecondsForPayment - session.getPaymentBaselineSeconds());

        session.setPendingPaymentSeconds(payableSeconds);
        session.setAccumulatedSeconds(totalSecondsForPayment);
        session.setJoinTime(null);
        session.setAwaitingPaymentConfirmation(true);
    }

    public void endDepositingPlayerTrip(String username)
    {
        PlayerSession session = sessions.get(username);
        if (session == null)
        {
            return;
        }

        session.setAccumulatedSeconds(session.getTotalSeconds());
        session.setStoredSeconds(session.getAccumulatedSeconds());
        session.setJoinTime(null);
        session.setIsOnBoat(false);
        session.setAwaitingPaymentConfirmation(false);

        sessions.remove(username);
        currentlySeenPlayers.remove(username);
        testPlayers.remove(username);
        ignoredPlayers.add(username);
        promoteWaitingPlayers();
    }

    public void confirmPaymentAndContinue(String username)
    {
        PlayerSession session = sessions.get(username);
        if (session == null)
        {
            return;
        }

        addPaymentOwed(session);
        resetSessionForContinuation(session);
    }

    public void confirmPaymentAndRemove(String username)
    {
        PlayerSession session = sessions.get(username);
        if (session == null)
        {
            return;
        }

        addPaymentOwed(session);
        session.setPendingPaymentSeconds(0);
        sessions.remove(username);
        currentlySeenPlayers.remove(username);
        testPlayers.remove(username);
        ignoredPlayers.add(username);
        promoteWaitingPlayers();
    }

    public void cancelPaymentCalculation(String username)
    {
        PlayerSession session = sessions.get(username);
        if (session == null)
        {
            return;
        }

        session.setPendingPaymentSeconds(0);
        session.setAwaitingPaymentConfirmation(false);
        session.setJoinTime(Instant.now());
    }

    private void addPaymentOwed(PlayerSession session)
    {
        if (session == null || session.isDepositing())
        {
            return;
        }

        long seconds = session.getPendingPaymentSeconds();
        long gp = calculateOwed(seconds);

        paymentOwed.compute(session.getUsername(), (name, existing) -> {
            if (existing == null)
            {
                return new PaymentOwed(name, seconds, gp, session.getAccountType());
            }

            existing.add(seconds, gp);
            existing.setAccountType(session.getAccountType());
            return existing;
        });
    }

    private void resetSessionForContinuation(PlayerSession session)
    {
        long totalSeconds = session.getAccumulatedSeconds();

        session.setPaymentBaselineSeconds(totalSeconds);
        session.setPendingPaymentSeconds(0);
        session.setJoinTime(Instant.now());
        session.setIsOnBoat(true);
        session.setDone(false);
        session.setPaid(false);
        session.setCurrentPaidGp(0);
        session.setGracePeriodEnd(null);
        session.setLeftBoatTime(null);
        session.setReturnConfirmationStartTime(null);
        session.setAwaitingPaymentConfirmation(false);

        currentlySeenPlayers.add(session.getUsername());
    }

    public void setPaid(String username, boolean paid)
    {
        PaymentOwed owed = paymentOwed.get(username);
        if (owed != null)
        {
            owed.setPaid(paid);
        }
    }

    public long calculateCurrentOwed(PlayerSession session)
    {
        if (session == null || session.isDepositing())
        {
            return 0;
        }

        long payableSeconds = Math.max(0, session.getDisplayedSeconds() - session.getPaymentBaselineSeconds());
        return calculateOwed(payableSeconds);
    }

    private long calculateOwed(PlayerSession session)
    {
        return calculateOwed(session.getDisplayedSeconds());
    }

    private long calculateOwed(long seconds)
    {
        double hours = seconds / 3600.0;
        long rawOwed = Math.round(hours * hourlyRate);
        return Math.round(rawOwed / 1000.0) * 1000;
    }

    public void setDepositing(String username, boolean depositing)
    {
        PlayerSession session = sessions.get(username);
        if (session != null)
        {
            session.markDepositingManuallyChanged(depositing);
        }
    }

    public static class PaymentOwed
    {
        @Getter
        private final String username;
        @Getter
        private long seconds;
        @Getter
        private long gp;
        @Getter
        @Setter
        private boolean paid;
        @Getter
        @Setter
        private AccountType accountType;

        public PaymentOwed(String username, long seconds, long gp, AccountType accountType)
        {
            this.username = username;
            this.seconds = seconds;
            this.gp = gp;
            this.accountType = accountType;
        }

        public void add(long seconds, long gp)
        {
            if (paid)
            {
                this.seconds = 0;
                this.gp = 0;
            }

            this.seconds += seconds;
            this.gp += gp;
            this.paid = false;
        }
    }
}