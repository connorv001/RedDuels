package com.github.radagastthered.redduels.redduels.object;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;

public class Duel {

    // expiration time for the duel, in milliseconds
    // 180,000 milliseconds = 3 minutes
    public static final long EXPIRE_TIME = 180000;

    // basic information about the duel
    private long timeCreated;
    private Player callingPlayer;
    private Player challengedPlayer;
    private Player victor = null;
    private Player loser = null;
    private boolean accepted = false;

    // the shared data object that coordinates all RedDuels objects
    private SharedData data;

    // the world where this duel will take place
    // the world of a queued duel is always null
    private World world = null;

    // information about both players before they were teleported into the duel
    private PlayerState callingPlayerState;
    private PlayerState challengedPlayerState;

    /*
    Duels have two players involved
    A SharedData object is also passed in so that the duel object can keep track of information more easily
     */
    public Duel (Player callingPlayer, Player challengedPlayer, SharedData data) {
        this.data = data;
        this.timeCreated = System.currentTimeMillis();
        this.callingPlayer = callingPlayer;
        this.challengedPlayer = challengedPlayer;
    }

    // fancy getters and setters
    public boolean isAccepted() {
        return accepted;
    }
    public void accept() {
        accepted = true;
    }
    public Player getCallingPlayer() {
        return callingPlayer;
    }
    public Player getChallengedPlayer() {
        return challengedPlayer;
    }
    public boolean isExpired() {
        return ((System.currentTimeMillis() - timeCreated) >= EXPIRE_TIME);
    }

    // handles beginning the duel
    public void beginDuel(DuelType duelType) {
        // instantiates the new world
        WorldCreator wc = new WorldCreator(duelType.worldName);
        world = wc.createWorld();
        // make it nice and sunny
        world.setTime(1000);
        // record PlayerStates
        callingPlayerState = new PlayerState(callingPlayer);
        challengedPlayerState = new PlayerState(challengedPlayer);
        // get the players' inventories for manipulation
        PlayerInventory callingPlayerInventory = callingPlayer.getInventory();
        PlayerInventory challengedPlayerInventory = challengedPlayer.getInventory();
        // clear the players' inventories
        callingPlayerInventory.clear();
        challengedPlayerInventory.clear();
        // give the players items to use
        callingPlayerInventory.setContents(duelType.items.clone());
        challengedPlayerInventory.setContents(duelType.items.clone());
        // give the players armor (this has to be equipped in a fiddly way)
        callingPlayerInventory.setArmorContents(duelType.armor.clone());
        challengedPlayerInventory.setArmorContents(duelType.armor.clone());
        // heal the players
        callingPlayer.setHealth(20.0d);
        challengedPlayer.setHealth(20.0d);
        // feed the players
        callingPlayer.setFoodLevel(20);
        challengedPlayer.setFoodLevel(20);
        // teleport the players in
        int[] a = duelType.player1SpawnLocation;
        int[] b = duelType.player2SpawnLocation;
        Location player1SpawnLocation = new Location(world, a[0], a[1], a[2]);
        Location player2SpawnLocation = new Location(world, b[0], b[1], b[2]);
        // players teleport in facing each other
        player1SpawnLocation.setDirection(player2SpawnLocation.clone().subtract(player1SpawnLocation).toVector());
        player2SpawnLocation.setDirection(player1SpawnLocation.clone().subtract(player2SpawnLocation).toVector());
        callingPlayer.teleport(player1SpawnLocation);
        challengedPlayer.teleport(player2SpawnLocation);
        // ensure the players are in adventure mode
        callingPlayer.setGameMode(GameMode.ADVENTURE);
        challengedPlayer.setGameMode(GameMode.ADVENTURE);
        // listeners
        data.plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onEntityDamage(EntityDamageEvent event) {
                // only listen for players getting hurt
                if (event.getEntity() instanceof Player) {
                    Player player = (Player) event.getEntity();
                    // check if it's a player we are interested in
                    if (player == callingPlayer || player == challengedPlayer) {
                        // check if they are about to die
                        if (player.getHealth() - event.getDamage() < 1) {
                            // end the duel
                            loser = player;
                            victor = loser == callingPlayer ? challengedPlayer : callingPlayer;
                            HandlerList.unregisterAll(this);
                            endDuel();
                            event.setCancelled(true);
                        }
                    }
                }
            }
        }, data.plugin);
    }

    /*
    Ends a duel
     */
    private void endDuel() {
        // if this method has been called and the victor is null, something terrible has happened
        if (victor != null) {
            // restore player states
            callingPlayerState.restore();
            challengedPlayerState.restore();
            // clean up the world
            for (Entity e : world.getEntities()) {
                e.remove();
            }
            // unload the world, and do not save
            Bukkit.unloadWorld(world, false);
            // global message about the duel
            data.plugin.getServer().broadcastMessage(data.cfg.formatDuelResolution(victor.getDisplayName() + " has defeated " + loser.getDisplayName() + " in a duel!"));
            // move this duel to resolvedDuels
            data.ongoingDuels.remove(this);
            data.resolvedDuels.add(this);
        }
    }

    // utility method for removing expired duels
    public static void removeExpiredDuels(SharedData data) {
        ArrayList<Duel> duels = data.queuedDuels;
        for (int i = 0; i < duels.size(); i++) {
            Duel d = duels.get(i);
            if (d.isExpired()) {
                d.getCallingPlayer().sendMessage(data.cfg.formatError("Your duel offer to " + d.getChallengedPlayer().getDisplayName() + " has expired"));
                duels.remove(i);
            }
        }
    }
    // utility method for getting a duel with specific players
    public static Duel getDuel(ArrayList<Duel> duels, Player callingPlayer, Player challengedPlayer) {
        for (Duel d : duels) {
            if (d.getCallingPlayer() == callingPlayer && d.getChallengedPlayer() == challengedPlayer) {
                return d;
            }
        }
        return null;
    }
    // utility method for deleting all where two players are involved
    public static void deleteDuelsBetween(ArrayList<Duel> duels, Player playerA, Player playerB) {
        for (int i = 0; i < duels.size(); i++){
            Duel d = duels.get(i);
            if ((d.getCallingPlayer() == playerA && d.getChallengedPlayer() == playerB) || (d.getCallingPlayer() == playerB && d.getChallengedPlayer() == playerA)) {
                duels.remove(i);
            }
        }
    }
    // utility method for deleting all duels where a certain player is involved
    public static void deleteDuelsWhereInvolved(ArrayList<Duel> duels, Player player) {
        for (int i = 0; i < duels.size(); i++) {
            Duel d = duels.get(i);
            if (d.getCallingPlayer() == player || d.getChallengedPlayer() == player) {
                duels.remove(i);
            }
        }
    }
    // utility method for checking whether a player is involved in a duel
    public static boolean involvedInDuel(ArrayList<Duel> duels, Player player) {
        for (Duel d : duels) {
            if (d.getCallingPlayer() == player || d.getChallengedPlayer() == player) {
                return true;
            }
        }
        return false;
    }
    // utility method for checking whether two players are in some way involved via the duel system
    // returns true if the players are involved in a duel object, returns false otherwise
    public static boolean bothInvolved(ArrayList<Duel> duels, Player playerA, Player playerB) {
        for (int i = 0; i < duels.size(); i++){
            Duel d = duels.get(i);
            if ((d.getCallingPlayer() == playerA && d.getChallengedPlayer() == playerB) || (d.getCallingPlayer() == playerB && d.getChallengedPlayer() == playerA)) {
                return true;
            }
        }
        return false;
    }
}
