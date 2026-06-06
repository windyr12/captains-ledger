package com.captainsledger;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;

public class PlayerSession
{
    @Getter
    private final String username;

    @Setter
    @Getter
    private Instant joinTime;
    @Setter
    @Getter
    private long accumulatedSeconds = 0;
    @Setter
    @Getter
    private long storedSeconds = 0;
    @Getter
    private boolean isOnBoat = true;
    @Getter
    private boolean isDepositing;
    @Setter
    @Getter
    private boolean depositingManuallyChanged = false;
    @Setter
    @Getter
    private Instant gracePeriodEnd = null;
    @Setter
    @Getter
    private Instant leftBoatTime = null;
    @Setter
    @Getter
    private boolean done = false;
    @Setter
    @Getter
    private boolean paid = false;
    @Setter
    @Getter
    private long totalPaidGp = 0;
    @Setter
    @Getter
    private long currentPaidGp = 0;
    @Setter
    @Getter
    private Instant returnConfirmationStartTime = null;
    @Setter
    @Getter
    private AccountType accountType = AccountType.UNKNOWN;
    @Setter
    @Getter
    private boolean awaitingPaymentConfirmation = false;
    @Setter
    @Getter
    private long paymentBaselineSeconds = 0;
    @Setter
    @Getter
    private long pendingPaymentSeconds = 0;
    @Setter
    @Getter
    private boolean waitingForCrewSlot = false;

    public PlayerSession(String username, boolean isDepositing)
    {
        this.username = username;
        this.isDepositing = isDepositing;
        this.joinTime = Instant.now();
    }

    public void setIsOnBoat(boolean onBoat) { this.isOnBoat = onBoat; }

    public void setIsDepositing(boolean depositing) { this.isDepositing = depositing; }

    public void markDepositingManuallyChanged(boolean depositing)
    {
        this.isDepositing = depositing;
        this.depositingManuallyChanged = true;
    }

    public long getTotalSeconds()
    {
        if (joinTime != null)
        {
            return accumulatedSeconds + Duration.between(joinTime, Instant.now()).getSeconds();
        }

        return accumulatedSeconds;
    }

    public long getDisplayedSeconds()
    {
        if (done)
        {
            return accumulatedSeconds;
        }

        return getTotalSeconds();
    }
}