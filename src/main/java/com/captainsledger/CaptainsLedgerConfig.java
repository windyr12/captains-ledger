package com.captainsledger;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("captainsledger")
public interface CaptainsLedgerConfig extends Config
{
	@ConfigItem(
			keyName = "hourlyRate",
			name = "Default Hourly Rate",
			description = "Default GP per hour",
			position = 1
	)
	default int hourlyRate()
	{
		return 1_000_000;
	}
}