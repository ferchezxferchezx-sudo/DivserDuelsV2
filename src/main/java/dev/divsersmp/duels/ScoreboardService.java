package dev.divsersmp.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class ScoreboardService {
    private final JavaPlugin plugin;
    private final Map<UUID, Scoreboard> previous = new HashMap<>();
    private final Map<Object, Integer> runningTasks = new HashMap<>();
    private final Map<Object, List<String>> templates = new HashMap<>();
    private final Map<Object, String> titles = new HashMap<>();
    private final Map<Object, Integer> intervals = new HashMap<>();

    public ScoreboardService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void applyDuelScoreboard(DuelSession session) {
        List<Player> players = new ArrayList<>();
        if (session.getPlayerA() != null && session.getPlayerA().isOnline()) players.add(session.getPlayerA());
        if (session.getPlayerB() != null && session.getPlayerB().isOnline()) players.add(session.getPlayerB());
        if (players.isEmpty()) return;

        File cfgFile = new File(plugin.getDataFolder(), "scoreboard.yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);
        List<String> lines = cfg.getStringList("duel.lines");
        String title = cfg.getString("duel.title", "&6&lDUEL");
        int interval = cfg.getInt("update-interval-ticks", 20);

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("duels", "dummy", ChatColor.translateAlternateColorCodes('&', title));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Save previous scoreboards and set ours
        for (Player p : players) {
            previous.put(p.getUniqueId(), p.getScoreboard());
            p.setScoreboard(board);
        }

        templates.put(session, lines);
        titles.put(session, title);
        intervals.put(session, interval);
        // start updater task
        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> updateDuelBoard(obj, session), 0L, interval).getTaskId();
        runningTasks.put(session, taskId);
    }

    public void applyPartyScoreboard(TeamDuelSession session) {
        List<Player> players = new ArrayList<>();
        for (UUID member : session.getParty().getMembers()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null && p.isOnline()) players.add(p);
        }
        if (players.isEmpty()) return;

        File cfgFile = new File(plugin.getDataFolder(), "scoreboard.yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);
        List<String> lines = cfg.getStringList("party.lines");
        String title = cfg.getString("party.title", "&3&lPARTY DUEL");
        int interval = cfg.getInt("update-interval-ticks", 20);

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("partyduel", "dummy", ChatColor.translateAlternateColorCodes('&', title));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (Player p : players) {
            previous.put(p.getUniqueId(), p.getScoreboard());
            p.setScoreboard(board);
        }

        templates.put(session, lines);
        titles.put(session, title);
        intervals.put(session, interval);
        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> updatePartyBoard(obj, session), 0L, interval).getTaskId();
        runningTasks.put(session, taskId);
    }

    private void updateDuelBoard(Objective obj, DuelSession session) {
        if (obj == null || session == null) return;
        // Build lines from placeholders
        List<String> lines = templates.getOrDefault(session, Arrays.asList(
            "",
            "Winrate: %winrate%",
            "Arena: %duels_arena%",
            "Kit: %duels_kit%",
            "Mode: %duels_mode%",
            "",
            "You: %player_health_rounded%❤",
            "Opponent: %duels_opponent_health%❤",
            "Opponent: %duels_opponent%",
            "",
            "Duration: %duels_match_time%",
            "Winstreak: %duels_winstreak%"
        ));
        // clear existing scores
        for (String entry : obj.getScoreboard().getEntries()) {
            obj.getScoreboard().resetScores(entry);
        }
        int score = lines.size();
        for (String raw : lines) {
            String replaced = replacePlaceholders(raw, session, null);
            String colored = ChatColor.translateAlternateColorCodes('&', replaced);
            obj.getScore(colored).setScore(score--);
        }
    }

    private void updatePartyBoard(Objective obj, TeamDuelSession session) {
        if (obj == null || session == null) return;
        List<String> lines = templates.getOrDefault(session, Arrays.asList(
            "&8┏ &dTeam Clash &8┓",
            "&7Arena: &e%duels_arena%",
            "&7Leader: &6%party_leader%",
            "&7Teams: &a%party_lime_count% &7| &c%party_red_count%",
            "&7Alive: &a%party_lime_alive% &7| &c%party_red_alive%",
            "&7Players: &e%party_size%",
            "&7Alive: &a%party_lime_alive% &7| &c%party_red_alive%",
            "&d------------------",
            "%party_teams%",
            "&8┗ &bFight together &8┛"
        ));
        for (String entry : obj.getScoreboard().getEntries()) {
            obj.getScoreboard().resetScores(entry);
        }
        List<String> resolved = new ArrayList<>();
        for (String raw : lines) {
            if (raw.contains("%party_teams%")) {
                resolved.addAll(buildPartyTeamLines(session));
            } else {
                resolved.add(raw);
            }
        }
        int score = resolved.size();
        for (String raw : resolved) {
            String replaced = replacePlaceholders(raw, null, session);
            String colored = ChatColor.translateAlternateColorCodes('&', replaced);
            obj.getScore(colored).setScore(score--);
        }
    }

    private String replacePlaceholders(String raw, DuelSession duel, TeamDuelSession party) {
        String s = raw;
        if (duel != null) {
            DuelRequest r = duel.getRequest();
            Player pA = duel.getPlayerA();
            Player pB = duel.getPlayerB();

            s = s.replace("%duels_arena%", r.getArenaName() == null ? "-" : r.getArenaName());
            s = s.replace("%duels_opponent%", pB != null && pB.isOnline() ? pB.getName() : (r.getTargetName() != null ? r.getTargetName() : "-"));
            s = s.replace("%duels_opponent_health%", pB != null && pB.isOnline() ? String.format(Locale.ROOT, "%.0f", pB.getHealth()) : "0");
            s = s.replace("%duels_match_time%", formatDuration(System.currentTimeMillis() - duel.getStartedAt()));
            s = s.replace("%duels_winstreak%", String.valueOf(duel.getManager().getWins(pA)));
            s = s.replace("%duels_player_a_health%", pA != null && pA.isOnline() ? String.format(Locale.ROOT, "%.0f", pA.getHealth()) : "0");
            s = s.replace("%duels_player_b_health%", pB != null && pB.isOnline() ? String.format(Locale.ROOT, "%.0f", pB.getHealth()) : "0");
            s = s.replace("%duels_player_ping%", pA != null && pA.isOnline() ? String.valueOf(pA.getPing()) : "0");
            s = s.replace("%duels_kit%", r.getKitName() == null ? "Classic" : r.getKitName());
            s = s.replace("%duels_mode%", r.getModeName() == null ? "1v1" : r.getModeName());
            s = s.replace("%keep_inventory_on_off%", r.isKeepInventory() ? "On" : "Off");
        }
        if (party != null) {
            int total = party.getParty().getMembers().size();
            int alive = countAlivePlayers(party);
            int enemyAlive = Math.max(0, total - alive);
            int eliminated = total - alive;
            s = s.replace("%party_size%", String.valueOf(total));
            String leaderName = "-";
            try {
                if (party.getParty().getLeaderId() != null) {
                    Player leader = Bukkit.getPlayer(party.getParty().getLeaderId());
                    if (leader != null) leaderName = leader.getName();
                }
            } catch (Exception ignored) {}
            s = s.replace("%party_leader%", leaderName);
            s = s.replace("%party_alive%", String.valueOf(alive));
            s = s.replace("%enemy_alive%", String.valueOf(enemyAlive));
            s = s.replace("%players_alive%", String.valueOf(alive));
            s = s.replace("%party_ready_count%", String.valueOf(party.getParty().getReadyCount()));
            s = s.replace("%match_kills%", String.valueOf(eliminated));
            s = s.replace("%match_deaths%", String.valueOf(eliminated));
            s = s.replace("%match_time%", formatDuration(System.currentTimeMillis() - party.getStartedAt()));
            s = s.replace("%keep_inventory_on_off%", party.getParty().isKeepInventory() ? "On" : "Off");
            s = s.replace("%duels_arena%", party.getParty().getArenaName() == null ? "-" : party.getParty().getArenaName());
            s = s.replace("%party_red_count%", String.valueOf(countTeamPlayers(party, "red")));
            s = s.replace("%party_lime_count%", String.valueOf(countTeamPlayers(party, "lime")));
            s = s.replace("%party_red_alive%", String.valueOf(countTeamAlive(party, "red")));
            s = s.replace("%party_lime_alive%", String.valueOf(countTeamAlive(party, "lime")));
            s = s.replace("%party_teams%", String.join("\n", buildPartyTeamLines(party)));
        }
        // common placeholders
        s = s.replace("%player_ping%", "0");
        s = s.replace("%player_health_rounded%", "❤");
        return s;
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, remainingSeconds);
    }

    private int countAlivePlayers(TeamDuelSession session) {
        int alive = 0;
        for (UUID member : session.getParty().getMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && player.isOnline() && player.getGameMode() != GameMode.SPECTATOR && player.getHealth() > 0.0) {
                alive++;
            }
        }
        return alive;
    }

    private List<String> buildPartyTeamLines(TeamDuelSession session) {
        List<String> result = new ArrayList<>();
        if (session == null || session.getParty() == null) {
            return result;
        }
        Party party = session.getParty();
        result.add("§cRed Team:");
        for (UUID member : party.getMembers()) {
            if ("red".equalsIgnoreCase(party.getTeam(member))) {
                Player player = Bukkit.getPlayer(member);
                if (player != null) {
                    if (session.isEliminated(member)) {
                        result.add("§8- " + player.getName());
                    } else {
                        result.add("§c- " + player.getName());
                    }
                }
            }
        }
        result.add("§aLime Team:");
        for (UUID member : party.getMembers()) {
            if ("lime".equalsIgnoreCase(party.getTeam(member))) {
                Player player = Bukkit.getPlayer(member);
                if (player != null) {
                    if (session.isEliminated(member)) {
                        result.add("§8- " + player.getName());
                    } else {
                        result.add("§a- " + player.getName());
                    }
                }
            }
        }
        return result;
    }

    private int countTeamPlayers(TeamDuelSession session, String team) {
        if (session == null || session.getParty() == null) return 0;
        return (int) session.getParty().getMembers().stream()
                .filter(member -> team.equalsIgnoreCase(session.getParty().getTeam(member)))
                .count();
    }

    private int countTeamAlive(TeamDuelSession session, String team) {
        if (session == null || session.getParty() == null) return 0;
        return (int) session.getParty().getMembers().stream()
                .filter(member -> team.equalsIgnoreCase(session.getParty().getTeam(member)))
                .filter(member -> {
                    Player player = Bukkit.getPlayer(member);
                    return player != null && player.isOnline() && player.getGameMode() != GameMode.SPECTATOR && player.getHealth() > 0.0;
                })
                .count();
    }

    public void removeScoreboardForSession(Object session) {
        Integer task = runningTasks.remove(session);
        if (task != null) Bukkit.getScheduler().cancelTask(task);
        // restore previous for players involved
        if (session instanceof DuelSession) {
            DuelSession ds = (DuelSession) session;
            for (Player p : Arrays.asList(ds.getPlayerA(), ds.getPlayerB())) {
                if (p == null) continue;
                Scoreboard prev = previous.remove(p.getUniqueId());
                if (prev != null) p.setScoreboard(prev);
            }
        } else if (session instanceof TeamDuelSession) {
            TeamDuelSession ts = (TeamDuelSession) session;
            for (UUID id : ts.getParty().getMembers()) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                Scoreboard prev = previous.remove(p.getUniqueId());
                if (prev != null) p.setScoreboard(prev);
            }
        }
    }
}
