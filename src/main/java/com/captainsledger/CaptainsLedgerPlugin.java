package com.captainsledger;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import java.io.IOException;

/*
 * This plugin was developed with the name "Captain's Ledger" but was changed
 * to "Skipper's Ledger" to avoid confusion with the already existing "Captain's Log" plugin.
 */

@PluginDescriptor(
		name = "Skipper's Ledger",
		description = "Manage deep sea trawling sessions - timers, payments, crew tracking",
		tags = {"fishing", "trawling", "captain", "ledger", "trawlinghub", "sailing"},
		enabledByDefault = true
)
public class CaptainsLedgerPlugin extends Plugin
{
	@Inject private ClientToolbar clientToolbar;
	@Inject private CaptainsLedgerConfig config;
	@Inject private net.runelite.api.Client client;
	@Inject private HiscoreClient hiscoreClient;

	@Getter
	private LedgerSessionManager sessionManager;
	private CaptainsLedgerPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		System.out.println("=== Captain's Ledger STARTING ===");

		sessionManager = new LedgerSessionManager(client, hiscoreClient);
		panel = new CaptainsLedgerPanel(this, sessionManager, config);
		sessionManager.setPanelUpdateCallback(() -> SwingUtilities.invokeLater(panel::update));

		navButton = NavigationButton.builder()
				.tooltip("Skipper's Ledger")
				.icon(createNavigationIcon())
				.priority(10)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);

		System.out.println("=== Captain's Ledger STARTED ===");
	}

	@Override
	protected void shutDown()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}

		if (sessionManager != null)
		{
			sessionManager.shutDown();
		}

		panel = null;
		sessionManager = null;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (sessionManager == null || panel == null)
		{
			return;
		}

		sessionManager.onGameTick();
		SwingUtilities.invokeLater(panel::update);
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		if (sessionManager == null || panel == null)
		{
			return;
		}

		sessionManager.onPlayerSpawned(event.getPlayer());
		SwingUtilities.invokeLater(panel::update);
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		if (sessionManager == null || panel == null)
		{
			return;
		}

		sessionManager.onPlayerDespawned(event.getPlayer());
		SwingUtilities.invokeLater(panel::update);
	}

	private BufferedImage createNavigationIcon()
	{
		try
		{
			return ImageIO.read(getClass().getResourceAsStream("/icon.png"));
		}
		catch (IOException | IllegalArgumentException e)
		{
			throw new RuntimeException("Unable to load Captain's Ledger icon", e);
		}
	}

	@Provides
	CaptainsLedgerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CaptainsLedgerConfig.class);
	}

}


