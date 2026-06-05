package com.captainsledger;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;

import java.time.Duration;
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
    private static final Duration AWAY_GRACE_PERIOD = Duration.ofMinutes(5);
    private static final Duration RETURN_CONFIRMATION_PERIOD = Duration.ofMinutes(2);

    private final Client client;
    private final HiscoreClient hiscoreClient;
    private final ExecutorService hiscoreExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, AccountType> accountTypeCache = new HashMap<>();
    private final Set<String> pendingAccountTypeLookups = new HashSet<>();
    private Runnable panelUpdateCallback = () -> {};

    @Getter
    private final Map<String, PlayerSession> sessions = new HashMap<>();
    private final Set<String> currentlySeenPlayers = new HashSet<>();
    private final Set<String> ignoredPlayers = new HashSet<>();
    private final Set<String> waitingForStepAwayPlayers = new HashSet<>();
    private final Map<String, Player> knownPlayers = new HashMap<>();
    boolean forceResumeEndedPlayers = false;

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
        ignoredPlayers.clear();
        waitingForStepAwayPlayers.clear();

        scanKnownPlayers();
    }

    public void resumeTrip()
    {
        tripActive = true;
        currentlySeenPlayers.clear();
        ignoredPlayers.clear();
        waitingForStepAwayPlayers.clear();

        Instant now = Instant.now();

        sessions.values().forEach(session -> {
            if (!session.isDone())
            {
                return;
            }

            long resumeSeconds = session.getDisplayedSeconds();

            if (session.isPaid())
            {
                session.setAccumulatedSeconds(0);
                session.setCurrentPaidGp(0);
                session.setPaid(false);
            }
            else
            {
                session.setAccumulatedSeconds(resumeSeconds);
            }

            session.setDone(false);
            session.setIsOnBoat(true);
            session.setJoinTime(now);
            session.setGracePeriodEnd(null);
            session.setLeftBoatTime(null);
            session.setStoredSeconds(0);

            currentlySeenPlayers.add(session.getUsername());
        });
    }

    public void endTrip()
    {
        sessions.values().forEach(session -> {
            long finalSeconds = session.getTotalSeconds();

            session.setAccumulatedSeconds(finalSeconds);
            session.setStoredSeconds(0);
            session.setJoinTime(null);
            session.setIsOnBoat(false);
            session.setGracePeriodEnd(null);
            session.setLeftBoatTime(null);
            session.setReturnConfirmationStartTime(null);
            session.setDone(true);
        });

        tripActive = false;
        currentlySeenPlayers.clear();

    }

    public void resetAll()
    {
        sessions.clear();
        currentlySeenPlayers.clear();
        ignoredPlayers.clear();
        waitingForStepAwayPlayers.clear();
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

            if (!waitingForStepAwayPlayers.contains(local.getName()) || forceResumeEndedPlayers)
            {
                addPlayerIfOnSameTile(local);
            }
        }

        for (Player player : knownPlayers.values())
        {
            if (isPlayerOnSameTile(player))
            {
                playersCurrentlyOnTile.add(player.getName());

                if (!waitingForStepAwayPlayers.contains(player.getName()) || forceResumeEndedPlayers)
                {
                    addPlayerIfOnSameTile(player);
                }
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

                    if (!waitingForStepAwayPlayers.contains(player.getName()) || forceResumeEndedPlayers)
                    {
                        addPlayerIfOnSameTile(player);
                    }
                }
            }
        }

        waitingForStepAwayPlayers.removeIf(name -> !playersCurrentlyOnTile.contains(name));

        for (String name : new HashSet<>(currentlySeenPlayers))
        {
            if (!playersCurrentlyOnTile.contains(name))
            {
                markPlayerOffBoat(name);
            }
        }

        updateSessionStates();

        forceResumeEndedPlayers = false;
    }

    private void updateSessionStates()
    {
        Instant now = Instant.now();

        sessions.values().forEach(session -> {
            if (!session.isOnBoat()
                    && !session.isDone()
                    && session.getGracePeriodEnd() != null
                    && now.isAfter(session.getGracePeriodEnd()))
            {
                session.setAccumulatedSeconds(session.getTotalSeconds());
                session.setDone(true);
                session.setGracePeriodEnd(null);
                session.setLeftBoatTime(null);
                session.setJoinTime(null);
                session.setReturnConfirmationStartTime(null);
            }

            if (session.isDone()
                    && session.isOnBoat()
                    && session.getReturnConfirmationStartTime() != null
                    && Duration.between(session.getReturnConfirmationStartTime(), now).compareTo(RETURN_CONFIRMATION_PERIOD) >= 0)
            {
                if (session.isPaid())
                {
                    session.setAccumulatedSeconds(0);
                    session.setCurrentPaidGp(0);
                    session.setPaid(false);
                }
                else
                {
                    session.setAccumulatedSeconds(session.getDisplayedSeconds());
                }

                session.setDone(false);
                session.setGracePeriodEnd(null);
                session.setLeftBoatTime(null);
                session.setReturnConfirmationStartTime(null);
                session.setJoinTime(Instant.now());
                session.setIsOnBoat(true);
            }
        });
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

    private void resumeDetectedEndedPlayersNow()
    {
        Instant now = Instant.now();

        sessions.values().forEach(session -> {
            if (!session.isDone() || !session.isOnBoat())
            {
                return;
            }

            resumeSessionImmediately(session, now);
        });
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
        currentlySeenPlayers.remove(name);

        PlayerSession session = sessions.get(name);
        if (session == null || !session.isOnBoat())
        {
            return;
        }

        if (session.isDone())
        {
            session.setJoinTime(null);
            session.setIsOnBoat(false);
            session.setLeftBoatTime(null);
            session.setGracePeriodEnd(null);
            return;
        }

        session.setAccumulatedSeconds(session.getTotalSeconds());
        session.setJoinTime(null);
        session.setIsOnBoat(false);
        session.setDone(false);
        session.setLeftBoatTime(Instant.now());
        session.setGracePeriodEnd(Instant.now().plus(AWAY_GRACE_PERIOD));
    }

    private void addPlayerIfOnSameTile(Player player)
    {
        if (!isPlayerOnSameTile(player))
        {
            return;
        }

        String name = player.getName();
        currentlySeenPlayers.add(name);

        PlayerSession session = sessions.computeIfAbsent(name,
                n -> new PlayerSession(n, true));

        queueAccountTypeLookup(session);

        if (session.isDone())
        {
            if (session.getReturnConfirmationStartTime() == null)
            {
                session.setReturnConfirmationStartTime(Instant.now());
            }

            session.setIsOnBoat(true);

            if (forceResumeEndedPlayers)
            {
                resumeSessionImmediately(session, Instant.now());
            }

            return;
        }

        if (!session.isOnBoat())
        {
            if (!session.isDone()
                    && session.getLeftBoatTime() != null
                    && session.getGracePeriodEnd() != null
                    && Instant.now().isBefore(session.getGracePeriodEnd()))
            {
                session.setAccumulatedSeconds(session.getTotalSeconds());
            }

            session.setGracePeriodEnd(null);
            session.setLeftBoatTime(null);
            session.setJoinTime(Instant.now());
            session.setIsOnBoat(true);
            return;
        }

        session.setIsOnBoat(true);
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

    private void resumeSessionImmediately(PlayerSession session, Instant now)
    {
        if (session.isPaid())
        {
            session.setAccumulatedSeconds(0);
            session.setCurrentPaidGp(0);
            session.setPaid(false);
        }
        else
        {
            session.setAccumulatedSeconds(session.getDisplayedSeconds());
        }

        session.setDone(false);
        session.setGracePeriodEnd(null);
        session.setLeftBoatTime(null);
        session.setReturnConfirmationStartTime(null);
        session.setJoinTime(now);
        session.setIsOnBoat(true);

        currentlySeenPlayers.add(session.getUsername());
        waitingForStepAwayPlayers.remove(session.getUsername());
    }

    private void resumeAllEndedPlayersImmediately()
    {
        Instant now = Instant.now();

        sessions.values().forEach(session -> {
            if (!session.isDone())
            {
                return;
            }

            resumeSessionImmediately(session, now);
        });
    }

    public void setHourlyRate(int rate)
    {
        this.hourlyRate = Math.max(0, rate);
    }

    public void removePlayer(String username)
    {
        sessions.remove(username);
        currentlySeenPlayers.remove(username);
        ignoredPlayers.add(username);
    }

    public void endPlayerTrip(String username)
    {
        PlayerSession session = sessions.get(username);
        if (session == null)
        {
            return;
        }

        long finalSeconds = session.isOnBoat()
                ? session.getTotalSeconds()
                : session.getAccumulatedSeconds();

        session.setAccumulatedSeconds(finalSeconds);
        session.setStoredSeconds(0);
        session.setJoinTime(null);
        session.setIsOnBoat(false);
        session.setGracePeriodEnd(null);
        session.setLeftBoatTime(null);
        session.setReturnConfirmationStartTime(null);
        session.setDone(true);

        currentlySeenPlayers.remove(username);
        waitingForStepAwayPlayers.add(username);
    }

    public void setPaid(String username, boolean paid)
    {
        PlayerSession session = sessions.get(username);
        if (session == null)
        {
            return;
        }

        if (paid && !session.isPaid())
        {
            long paidGp = calculateOwed(session);
            session.setCurrentPaidGp(paidGp);
            session.setTotalPaidGp(session.getTotalPaidGp() + paidGp);
            session.setPaid(true);
        }
        else if (!paid && session.isPaid())
        {
            session.setTotalPaidGp(Math.max(0, session.getTotalPaidGp() - session.getCurrentPaidGp()));
            session.setCurrentPaidGp(0);
            session.setPaid(false);
        }
    }

    private long calculateOwed(PlayerSession session)
    {
        double hours = session.getDisplayedSeconds() / 3600.0;
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

}