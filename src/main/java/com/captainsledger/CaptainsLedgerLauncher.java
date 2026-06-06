
/*
 * Development launcher for running this plugin locally from the IDE/Gradle.
 * Not used by RuneLite when the plugin is loaded normally.
 */

package com.captainsledger;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CaptainsLedgerLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(CaptainsLedgerPlugin.class);
        RuneLite.main(args);
    }
}
