package com.gamer0kayf1n3.velocity_prefix_auth;

import com.gamer0kayf1n3.velocity_prefix_auth.MojangApiClient.PlayerInfo;
import java.sql.*;

public class NameDatabase {

    private final Connection connection;

    public NameDatabase(String databaseFile) throws SQLException {

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }
        
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_names (uuid TEXT PRIMARY KEY, name TEXT, expires_at INTEGER)");
        }
    }

    public void savePlayerName(PlayerInfo playerInfo) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("INSERT OR REPLACE INTO player_names (uuid, name, expires_at) VALUES (?, ?, ?)")) {
            stmt.setString(1, playerInfo.uuid);
            stmt.setString(2, playerInfo.username);
            stmt.setLong(3, playerInfo.expiresAt);
            stmt.executeUpdate();
        }
    }

    public PlayerInfo getPlayer(String name) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT uuid, expires_at FROM player_names WHERE name = ?")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {

                    PlayerInfo playerInfo = new PlayerInfo(rs.getString("uuid"), name, rs.getLong("expires_at"));

                    if (playerInfo.expiresAt < System.currentTimeMillis()) {
                        // Entry expired, delete it
                        deletePlayer(name);
                        return null;
                    }

                    return playerInfo;

                }
            }
        }
        return null;
    }

    public void deletePlayer(String name) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM player_names WHERE name = ?")) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
    }

    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }
}
