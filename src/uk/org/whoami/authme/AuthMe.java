/*
 * Copyright 2011 Sebastian Köhler <sebkoehler@whoami.org.uk>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.whoami.authme;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import uk.org.whoami.authme.api.API;
import uk.org.whoami.authme.cache.auth.PlayerAuth;
import uk.org.whoami.authme.cache.auth.PlayerCache;
import uk.org.whoami.authme.cache.backup.FileCache;
import uk.org.whoami.authme.cache.limbo.LimboCache;
import uk.org.whoami.authme.cache.limbo.LimboPlayer;
import uk.org.whoami.authme.commands.AdminCommand;
import uk.org.whoami.authme.commands.ChangePasswordCommand;
import uk.org.whoami.authme.commands.LoginCommand;
import uk.org.whoami.authme.commands.LogoutCommand;
import uk.org.whoami.authme.commands.RegisterCommand;
import uk.org.whoami.authme.commands.UnregisterCommand;
import uk.org.whoami.authme.datasource.CacheDataSource;
import uk.org.whoami.authme.datasource.DataSource;
import uk.org.whoami.authme.datasource.FileDataSource;
import uk.org.whoami.authme.datasource.MiniConnectionPoolManager.TimeoutException;
import uk.org.whoami.authme.datasource.MySQLDataSource;
import uk.org.whoami.authme.listener.AuthMeBlockListener;
import uk.org.whoami.authme.listener.AuthMeEntityListener;
import uk.org.whoami.authme.listener.AuthMePlayerListener;
import uk.org.whoami.authme.listener.AuthMeSpoutListener;
import uk.org.whoami.authme.plugin.manager.CitizensCommunicator;
import uk.org.whoami.authme.plugin.manager.CombatTagComunicator;
import uk.org.whoami.authme.settings.Messages;
import uk.org.whoami.authme.settings.PlayersLogs;
import uk.org.whoami.authme.settings.Settings;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.Server;

import uk.org.whoami.authme.commands.PasspartuCommand;
import uk.org.whoami.authme.datasource.SqliteDataSource;

public class AuthMe extends JavaPlugin {

    private DataSource database = null;
    private Settings settings;
	@SuppressWarnings("unused")
	private Messages m;
    private PlayersLogs pllog;
	public Management management;
    public static Server server;
    public static Plugin authme;
    public static Permission permission;
	private static AuthMe instance;
    private Utils utils = Utils.getInstance();
    private JavaPlugin plugin;
    private FileCache playerBackup = new FileCache();

    
    @Override
    public void onEnable() {
    	instance = this;
    	authme = instance;
    	API.setPlugin(this);
        /*
         *  Metric part from Hidendra Stats
         */
        try {
        Metrics metrics = new Metrics();
        metrics.beginMeasuringPlugin(this);
            } catch (IOException e) {
            // Failed to submit the stats :-(
        }
         
        settings = new Settings(this);
        settings.loadConfigOptions();
        
        m = Messages.getInstance();
        pllog = PlayersLogs.getInstance();
        
        server = getServer();
        
        /*
         *  Back style on start if avaible
         */
        if(Settings.isBackupActivated && Settings.isBackupOnStart) {
        Boolean Backup = new PerformBackup().PerformBackup();
        if(Backup) ConsoleLogger.info("Backup Complete");
            else ConsoleLogger.showError("Error while making Backup");
        }
        
        /*
         * Backend MYSQL - FILE - SQLITE
         */
        switch (Settings.getDataSource) {
            case FILE:
                try {
                    database = new FileDataSource();
                } catch (IOException ex) {
                    ConsoleLogger.showError(ex.getMessage());
                    if (Settings.isStopEnabled) {
                    	ConsoleLogger.info("Can't use flat file... shutdown...");
                    	server.shutdown();
                    } 
                    if (!Settings.isStopEnabled)
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                break;
            case MYSQL:
                try {
                    database = new MySQLDataSource();
                } catch (ClassNotFoundException ex) {
                    ConsoleLogger.showError(ex.getMessage());
                    if (Settings.isStopEnabled) {
                    	ConsoleLogger.info("Can't use MySQL... shutdown...");
                    	server.shutdown();
                    } 
                    if (!Settings.isStopEnabled)
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                } catch (SQLException ex) {
                    ConsoleLogger.showError(ex.getMessage());
                    if (Settings.isStopEnabled) {
                    	ConsoleLogger.info("Can't use MySQL... shutdown...");
                    	server.shutdown();
                    }
                    if (!Settings.isStopEnabled)
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                } catch(TimeoutException ex) {
                    ConsoleLogger.showError(ex.getMessage());
                    if (Settings.isStopEnabled) {
                    	ConsoleLogger.info("Can't use MySQL... shutdown...");
                    	server.shutdown();
                    }
                    if (!Settings.isStopEnabled)
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                break;
            case SQLITE:
                try {
                     database = new SqliteDataSource();
                } catch (ClassNotFoundException ex) {
                    ConsoleLogger.showError(ex.getMessage());
                    if (Settings.isStopEnabled) {
                    	ConsoleLogger.info("Can't use sqlite... shutdown...");
                    	server.shutdown();
                    }
                    if (!Settings.isStopEnabled)
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                } catch (SQLException ex) {
                    ConsoleLogger.showError(ex.getMessage());
                    if (Settings.isStopEnabled) {
                    	ConsoleLogger.info("Can't use sqlite... shutdown...");
                    	server.shutdown();
                    }
                    if (!Settings.isStopEnabled)
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                break;
        }

        if (Settings.isCachingEnabled) {
            database = new CacheDataSource(database);
        }
        
        management =  new Management(database);
        
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new AuthMePlayerListener(this,database),this);
        pm.registerEvents(new AuthMeBlockListener(database),this);
        pm.registerEvents(new AuthMeEntityListener(database),this);
        if (pm.isPluginEnabled("Spout")) 
        	pm.registerEvents(new AuthMeSpoutListener(database),this);
        
        //Find Permissions
        if(Settings.isPermissionCheckEnabled) {
        RegisteredServiceProvider<Permission> permissionProvider =
                getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null)
            permission = permissionProvider.getProvider();
        else {
            
            ConsoleLogger.showError("Vault and Permissions plugins is needed for enable AuthMe Reloaded!");
            this.getServer().getPluginManager().disablePlugin(this);   
            }
        }
        
        //System.out.println("[debug perm]"+permission);
        
        this.getCommand("authme").setExecutor(new AdminCommand(database));
        this.getCommand("register").setExecutor(new RegisterCommand(database));
        this.getCommand("login").setExecutor(new LoginCommand());
        this.getCommand("changepassword").setExecutor(new ChangePasswordCommand(database));
        this.getCommand("logout").setExecutor(new LogoutCommand(this,database));
        this.getCommand("unregister").setExecutor(new UnregisterCommand(this, database));
        this.getCommand("passpartu").setExecutor(new PasspartuCommand(database));

        
        //
        // Check for correct sintax in config file!
        //
        
        if (!new File(getDataFolder() + File.separator + "players.yml").exists()) {
        	pllog = new PlayersLogs();
        }
        
        if(!Settings.isForceSingleSessionEnabled) {
            ConsoleLogger.info("ATTENTION by disabling ForceSingleSession Your server protection is set to low");
        }
        
        if (Settings.reloadSupport)
        onReload();

        if (server.getOnlinePlayers().length < 1) {
        	PlayersLogs.players.clear();
        	pllog.save();
        }
        
        ConsoleLogger.info("Authme " + this.getDescription().getVersion() + " enabled");
    }

    @Override
    public void onDisable() {
        if (Bukkit.getOnlinePlayers() != null)
        for(Player player : Bukkit.getOnlinePlayers()) {
        		this.savePlayer(player);
        }
        pllog.save();
        
        if (database != null) {
            database.close();
        }
        //utils = Utils.getInstance();
        
        /*
         *  Back style on start if avaible
         */
        if(Settings.isBackupActivated && Settings.isBackupOnStop) {
        Boolean Backup = new PerformBackup().PerformBackup();
        if(Backup) ConsoleLogger.info("Backup Complete");
            else ConsoleLogger.showError("Error while making Backup");
        }       
        ConsoleLogger.info("Authme " + this.getDescription().getVersion() + " disabled");
        
        
    }

	private void onReload() {
    	if (Bukkit.getServer().getOnlinePlayers() != null && !PlayersLogs.players.isEmpty()) {
    		for (Player player : Bukkit.getServer().getOnlinePlayers()) {
    			if (PlayersLogs.players.contains(player.getName())) {
    				String name = player.getName().toLowerCase();
    		        PlayerAuth pAuth = database.getAuth(name);
    	            // if Mysql is unavaible
    	            if(pAuth == null)
    	                break;
    	            PlayerAuth auth = new PlayerAuth(name, pAuth.getHash(), pAuth.getIp(), new Date().getTime());
    	            database.updateSession(auth);
    				PlayerCache.getInstance().addPlayer(auth); 
    			}
    		}
    	}
        return;
    }
    
	public static AuthMe getInstance() {
		return instance;
	}
	
	public void savePlayer(Player player) throws IllegalStateException {
		try {
	      if ((CitizensCommunicator.isNPC(player)) || (Utils.getInstance().isUnrestricted(player)) || (CombatTagComunicator.isNPC(player))) {
	          return;
	        }
		} catch (Exception e) { }
		
		try {
	        String name = player.getName().toLowerCase();
	        if ((PlayerCache.getInstance().isAuthenticated(name)) && (!player.isDead()) && 
	          (Settings.isSaveQuitLocationEnabled.booleanValue())) {
	          PlayerAuth auth = new PlayerAuth(player.getName().toLowerCase(), (int)player.getLocation().getX(), (int)player.getLocation().getY(), (int)player.getLocation().getZ());
	          this.database.updateQuitLoc(auth);
	        }

	        if (LimboCache.getInstance().hasLimboPlayer(name))
	        {
	          LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(name);
	          if (Settings.protectInventoryBeforeLogInEnabled.booleanValue()) {
	            player.getInventory().setArmorContents(limbo.getArmour());
	            player.getInventory().setContents(limbo.getInventory());
	          }
	          player.teleport(limbo.getLoc());
	          this.utils.addNormal(player, limbo.getGroup());
	          player.setOp(limbo.getOperator());

	          this.plugin.getServer().getScheduler().cancelTask(limbo.getTimeoutTaskId());
	          LimboCache.getInstance().deleteLimboPlayer(name);
	          if (this.playerBackup.doesCacheExist(name)) {
	            this.playerBackup.removeCache(name);
	          }
	        }

	        PlayerCache.getInstance().removePlayer(name);
	        player.saveData();
	      } catch (Exception ex) {
	    	  ex.printStackTrace();
	      }
	}
}