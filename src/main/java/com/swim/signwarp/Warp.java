package com.swim.signwarp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Warp {
    // 資料庫連線字串
    private static final String DB_URL = "jdbc:sqlite:" + JavaPlugin.getPlugin(SignWarp.class).getDataFolder() + File.separator + "warps.db";
    private static final Logger logger = JavaPlugin.getPlugin(SignWarp.class).getLogger();
    // 資料庫連線
    private final String warpName;// 位置名稱
    private final Location location;// 位置
    private final String createdAt;// 創建時間
    private final String creator;// 創建者
    private final String creatorUuid; // 新增 UUID 欄位
    private final boolean isPrivate;// 是否私有

    public Warp(String warpName, Location location, String createdAt, String creator, String creatorUuid, boolean isPrivate) {
        this.warpName = warpName;
        this.location = location;
        this.createdAt = createdAt;
        this.creator = creator;
        this.creatorUuid = creatorUuid;
        this.isPrivate = isPrivate;
    }

    public String getCreatorUuid() {
        return creatorUuid;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public String getName() {
        return warpName;
    }

    public Location getLocation() {
        return location;
    }

    public String getFormattedCreatedAt() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy | hh:mm:ss a");
        LocalDateTime dateTime = LocalDateTime.parse(createdAt);
        return dateTime.format(formatter);
    }

    // 新增 getter 取得 creator
    public String getCreator() {
        return creator;
    }

    public void save() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "INSERT OR REPLACE INTO warps (name, world, x, y, z, yaw, pitch, created_at, creator, creator_uuid, is_private) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE((SELECT created_at FROM warps WHERE name = ?), ?), ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, warpName);
                pstmt.setString(2, Objects.requireNonNull(location.getWorld()).getName());
                pstmt.setDouble(3, location.getX());
                pstmt.setDouble(4, location.getY());
                pstmt.setDouble(5, location.getZ());
                pstmt.setFloat(6, location.getYaw());
                pstmt.setFloat(7, location.getPitch());
                pstmt.setString(8, warpName);
                pstmt.setString(9, createdAt);
                pstmt.setString(10, creator);
                pstmt.setString(11, creatorUuid);
                pstmt.setInt(12, isPrivate ? 1 : 0); // 新增此行
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Failed to save warp '" + warpName + "': " + e.getMessage());
            // For debugging, you might want to include the stack trace in log
            logger.severe(() -> {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                return sw.toString();
            });
        }
    }

    public void remove() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "DELETE FROM warps WHERE name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, warpName);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Failed to remove warp '" + warpName + "': " + e.getMessage());
            if (logger.isLoggable(Level.FINE)) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                logger.fine(sw.toString());
            }
        }
    }

    /**
     * 修改 getByName()，多取得 creator 欄位
     */
    public static Warp getByName(String warpName) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM warps WHERE name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, warpName);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    String worldName = rs.getString("world");
                    World world = Bukkit.getWorld(worldName);
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    float yaw = rs.getFloat("yaw");
                    float pitch = rs.getFloat("pitch");
                    String createdAt = rs.getString("created_at");
                    String creator = rs.getString("creator");
                    String creatorUuid = rs.getString("creator_uuid");
                    boolean isPrivate = rs.getInt("is_private") == 1; // 新增此行
                    if (creator == null) creator = "unknown";
                    if (creatorUuid == null) creatorUuid = "";
                    if (createdAt == null) createdAt = LocalDateTime.now().toString();
                    Location location = new Location(world, x, y, z, yaw, pitch);
                    return new Warp(warpName, location, createdAt, creator, creatorUuid, isPrivate);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get warp '" + warpName + "': " + e.getMessage());
            if (logger.isLoggable(Level.FINE)) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                logger.fine(sw.toString());
            }
        }
        return null;
    }

    /**
     * 修改 getAll()，查詢時一併取得 creator 欄位
     */
    public static List<Warp> getAll() {
        List<Warp> warps = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM warps";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) {
                    String name = rs.getString("name");
                    String worldName = rs.getString("world");
                    World world = Bukkit.getWorld(worldName);
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    float yaw = rs.getFloat("yaw");
                    float pitch = rs.getFloat("pitch");
                    String createdAt = rs.getString("created_at");
                    String creator = rs.getString("creator");
                    String creatorUuid = rs.getString("creator_uuid");
                    boolean isPrivate = rs.getInt("is_private") == 1; // 新增此行
                    if (creator == null) creator = "unknown";
                    if (creatorUuid == null) creatorUuid = "";
                    if (createdAt == null) createdAt = LocalDateTime.now().toString();
                    Location location = new Location(world, x, y, z, yaw, pitch);
                    warps.add(new Warp(name, location, createdAt, creator, creatorUuid, isPrivate));
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get all warps: " + e.getMessage());
            if (logger.isLoggable(Level.FINE)) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                logger.fine(sw.toString());
            }
        }
        return warps;
    }
    private static boolean getDefaultVisibility() {
        return JavaPlugin.getPlugin(SignWarp.class).getConfig().getBoolean("default-visibility", false);
    }
    public static Warp createNew(String name, Location location, Player creator) {
        boolean isPrivate = getDefaultVisibility();
        return new Warp(name, location, LocalDateTime.now().toString(),
                creator.getName(), creator.getUniqueId().toString(), isPrivate);
    }
    /**
     * 修改 createTable()，除了原有欄位外，增加 creator 欄位與 migration 檢查
     */
    public static void createTable() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "CREATE TABLE IF NOT EXISTS warps (" +
                    "name TEXT PRIMARY KEY, " +
                    "world TEXT, " +
                    "x REAL, " +
                    "y REAL, " +
                    "z REAL, " +
                    "yaw REAL, " +
                    "pitch REAL, " +
                    "created_at TEXT, " +
                    "creator TEXT, " +
                    "creator_uuid TEXT, " +
                    "is_private INTEGER DEFAULT 0" + // 新增此行
                    ")";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }

            // 新增 migration 檢查
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getColumns(null, null, "warps", "is_private");
            if (!rs.next()) {
                String alterSql = "ALTER TABLE warps ADD COLUMN is_private INTEGER DEFAULT 0";
                try (Statement alterStmt = conn.createStatement()) {
                    alterStmt.execute(alterSql);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to create warps table: " + e.getMessage());
            if (logger.isLoggable(Level.FINE)) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                logger.fine(sw.toString());
            }
        }
    }
}
