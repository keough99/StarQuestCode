package com.whirlwindgames.dibujaron.sqempire.database.object;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import net.countercraft.movecraft.bungee.BungeePlayerHandler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.whirlwindgames.dibujaron.sqempire.Empire;
import com.whirlwindgames.dibujaron.sqempire.Planet;
import com.whirlwindgames.dibujaron.sqempire.SQEmpire;
import com.whirlwindgames.dibujaron.sqempire.Settings;
import com.whirlwindgames.dibujaron.sqempire.database.EmpireDB;
import com.whirlwindgames.dibujaron.sqempire.util.AsyncUtil;
import com.whirlwindgames.dibujaron.sqempire.util.RSReader;
import com.whirlwindgames.dibujaron.sqempire.util.SuperPS;

public class EmpirePlayer {
	private UUID id;
	private String name;
	private int empire;
	private int[] powerRatings;
	private long[] lastSeenTimes;
	
	private static HashMap<UUID, EmpirePlayer> cache = new HashMap<UUID, EmpirePlayer>();
	
	public static EmpirePlayer getOnlinePlayer(Player p){
		EmpirePlayer ep = cache.get(p.getUniqueId());
		if(ep == null){
			throw new NullPointerException("Requested online EmpirePlayer but none exists!");
		}
		return ep;
	}
	
	public void setEmpire(Empire e){
		empire = e.getID();
		publishData();
	}
	public static void loadPlayerData(final Player player) {
		// TODO Auto-generated method stub
		AsyncUtil.runAsync(new Runnable(){
			public void run(){
				final EmpirePlayer p = new EmpirePlayer(player.getUniqueId(), true);
				AsyncUtil.runSync(new Runnable(){
					public void run(){
						cache.put(player.getUniqueId(), p);
						System.out.println("Cache add: " + cache.size());
						if(p.empire != 0 && p.lastSeenTimes[0] == 0){
							//this person has an empire but was not generated by the normal system
							//must be from EmpireBuilder
							p.lastSeenTimes[0] = System.currentTimeMillis();
							p.publishData();
							player.sendMessage("[EmpireBuilder] you've been assigned empire " + Empire.fromID(p.empire).getName() + "!");
							SQEmpire.economy.depositPlayer(player, 10000);
							if(p.empire == Empire.ARATOR.getID()){
								SQEmpire.permission.playerAddGroup(player,"Arator0");
								BungeePlayerHandler.sendPlayer(player, "AratorSystem", "AratorSystem", 2598, 100, 1500);
								Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
										"eb janesudo Aratorians, please welcome your newest member " + player.getName() + "!");
							} else if(p.empire == Empire.REQUIEM.getID()){
								SQEmpire.permission.playerAddGroup(player,"Requiem0");
								BungeePlayerHandler.sendPlayer(player, "QuillonSystem", "QuillonSystem", 1375, 100, -2381);
								Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
										"eb janesudo Requiem, please welcome your newest member " + player.getName() + "!");
							} else if(p.empire == Empire.YAVARI.getID()){
								SQEmpire.permission.playerAddGroup(player,"Yavari0");
								BungeePlayerHandler.sendPlayer(player, "YavarSystem", "YavarSystem", 1375, 100, -2381);
								Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
										"eb janesudo Yavari, please welcome your newest member " + player.getName() + "!");
							}
							SQEmpire.permission.playerRemoveGroup(player,"Guest");

						}
					}
				});
			}
		});
	}
	public static void unloadPlayerData(Player player) {
		cache.remove(player.getUniqueId());
		System.out.println("Cache remove: " + cache.size());
	}
	public EmpirePlayer(UUID u){
		this(u, true);
	}
	
	String updateCommand = "INSERT INTO minecraft.empire_player(uuid,lname,empire,"
			+ "power_0,power_1,power_2,power_3,power_4,"
			+ "lastSeen_0,lastSeen_1,lastSeen_2,lastSeen_3,lastSeen_4)"
			+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE "
			+ "uuid=?,lname=?,empire=?,"
			+ "power_0=?,power_1=?,power_2=?,power_3=?,power_4=?,"
	        + "lastSeen_0=?,lastSeen_1=?,lastSeen_2=?,lastSeen_3=?,lastSeen_4=?";

	private EmpirePlayer(UUID u, boolean requestData){
		id = u;
		powerRatings = new int[5];
		lastSeenTimes = new long[5];
		if(requestData){
			requestData();
		}
	}
	
	private static LinkedList<EmpirePlayer> getPlayersOfflineMoreThan(Planet p, long ticks){
		int pid = p.getID();
		long earliestAcceptableLogoff = System.currentTimeMillis() - ticks;
		String query = "SELECT * from minecraft.empire_player WHERE lastSeen_" + pid + " < " + earliestAcceptableLogoff;
		RSReader rs = new RSReader(EmpireDB.requestData(query));
		LinkedList<EmpirePlayer> retval = new LinkedList<EmpirePlayer>();
		while(rs.next()){
			UUID u = UUID.fromString(rs.getString("uuid"));
			EmpirePlayer player = new EmpirePlayer(u, false);
			player.readData(rs);
			retval.add(player);
		}
		return retval;
	}
	
	public int getPowerOnPlanet(Planet p){
		return powerRatings[p.getID()];
	}
	
	public void updatePowerOnPlanet(Planet p, int power){
		powerRatings[p.getID()] = power;
	}
	
	public long getLastSeenOnPlanet(Planet p){
		return lastSeenTimes[p.getID()];
	}
	
	public void updateLastSeenOnPlanet(Planet p, long time){
		lastSeenTimes[p.getID()] = time;
	}
	
	public Empire getEmpire(){
		return Empire.fromID(empire);
	}
	
	private void requestData(){
		AsyncUtil.crashIfNotAsync();
		String query = "SELECT * from minecraft.empire_player WHERE uuid=\"" + id.toString() + "\"";
		RSReader rs = new RSReader(EmpireDB.requestData(query));

		if(rs.next()){
			readData(rs);
		} else {
			fillDefaultData();
		}
	}
	
	private void readData(RSReader rs){
		name = rs.getString("lname");
		empire = rs.getInt("empire");
		for(int i = 0; i < 5; i++){
			powerRatings[i] = rs.getInt("power_" + i);
			lastSeenTimes[i] = rs.getLong("lastSeen_" + i);
		}
	}
	
	private void fillDefaultData(){
		empire = 0;
		for(int i = 0; i < 5; i++){
			powerRatings[i] = 0;
			lastSeenTimes[i] = System.currentTimeMillis();
		}
	}
	
	public void publishData(){
		AsyncUtil.crashIfNotAsync();
		SuperPS ps = new SuperPS(EmpireDB.prepareStatement(updateCommand),13);
		ps.setDuplicate(1,id.toString());
		ps.setDuplicate(2,name);
		ps.setDuplicate(3,empire);
		for(int i = 0; i < 5; i++){
			ps.setDuplicate(i+4,powerRatings[i]);
			ps.setDuplicate(i+9,lastSeenTimes[i]);
		}
		ps.executeAndClose();
	}
}
