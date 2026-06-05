package com.captainsledger;

public enum AccountType
{
    UNKNOWN(null),
    REGULAR(null),
    IRONMAN("/icons/ironman.png"),
    HARDCORE_IRONMAN("/icons/hardcore_ironman.png"),
    ULTIMATE_IRONMAN("/icons/ultimate_ironman.png"),
    GROUP_IRONMAN("/icons/group_ironman.png"),
    HARDCORE_GROUP_IRONMAN("/icons/hardcore_group_ironman.png");

    private final String iconPath;

    AccountType(String iconPath)
    {
        this.iconPath = iconPath;
    }

    public String getIconPath()
    {
        return iconPath;
    }

    public boolean isIronman()
    {
        return this == IRONMAN
                || this == HARDCORE_IRONMAN
                || this == ULTIMATE_IRONMAN
                || this == GROUP_IRONMAN
                || this == HARDCORE_GROUP_IRONMAN;
    }
}
