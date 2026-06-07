package com.captainsledger;

import lombok.Getter;

@Getter
public enum AccountType
{
    UNKNOWN(-1),
    REGULAR(-1),
    IRONMAN(2),
    HARDCORE_IRONMAN(10),
    ULTIMATE_IRONMAN(3);

    private final int modIcon;

    AccountType(int modIcon)
    {
        this.modIcon = modIcon;
    }

    public boolean hasModIcon()
    {
        return modIcon >= 0;
    }

    public boolean isIronman()
    {
        return this == IRONMAN
                || this == HARDCORE_IRONMAN
                || this == ULTIMATE_IRONMAN;
    }
}