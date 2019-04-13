/*
 * MIT License
 *
 * Copyright (c) 2019 Niklas Seyfarth
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package mr.minecraft15.onlinetime;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.md_5.bungee.api.plugin.Plugin;

import java.sql.*;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DatabaseStorage implements PlayerNameStorage, OnlineTimeStorage {

    private static final String CREATE_ONLINE_TIME_TABLE_SQL = "CREATE TABLE IF NOT EXISTS `online_time` (" +
            "`id`   INT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
            "`uuid` BINARY(16) NOT NULL UNIQUE," +
            "`name` CHAR(16) CHARACTER SET ascii UNIQUE," +
            "`time` BIGINT UNSIGNED NOT NULL DEFAULT 0" +
            ") ENGINE InnoDB";

    private static final String GET_BY_UUID_SQL = "SELECT `name`, `time` FROM `online_time` WHERE `uuid` = ?";
    private static final String GET_BY_NAME_SQL = "SELECT `uuid` AS uuid FROM `online_time` WHERE `name` = ?";
    private static final String UPDATE_TIME_SQL = "UPDATE `online_time` SET `time` = `time` + ? WHERE `uuid` = ?";
    private static final String UNSET_TAKEN_NAME_SQL = "UPDATE `online_time` SET name = NULL WHERE `uuid` = ?";
    private static final String INSERT_OR_UPDATE_ENTRY_SQL = "INSERT INTO `online_time` (`uuid`, `name`, `time`) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE `name` = ?, `time` = `time` + ?";

    private final Plugin plugin;

    private final HikariDataSource dataSource;
    private final ReadWriteLock poolLock;

    public DatabaseStorage(Plugin plugin, Properties properties) throws StorageException {
        this.plugin = Objects.requireNonNull(plugin);
        this.poolLock = new ReentrantReadWriteLock();

        HikariConfig databaseConfig = new HikariConfig(properties);
        databaseConfig.setPoolName("OnlineTime-DatabasePool");
        dataSource = new HikariDataSource(databaseConfig);

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(CREATE_ONLINE_TIME_TABLE_SQL);
        } catch (SQLException ex) {
            throw new StorageException(ex);
        }
    }

    @Override
    public Optional<UUID> getUuid(String name) throws StorageException {
        Objects.requireNonNull(name);
        poolLock.readLock().lock();
        try {
            checkClosed();
            try (Connection connection = dataSource.getConnection()){
                return getUuid(connection, name);
            }
        } catch (SQLException ex) {
            throw new StorageException(ex);
        } finally {
            poolLock.readLock().unlock();
        }
    }

    private Optional<UUID> getUuid(Connection connection, String name) throws SQLException {
        try (PreparedStatement getByNameStmnt = connection.prepareStatement(GET_BY_NAME_SQL)) {
            getByNameStmnt.setString(1, name);
            try (ResultSet result = getByNameStmnt.executeQuery()) {
                if (result.first()) {
                    plugin.getLogger().info(fromBytes(result.getBytes("uuid")).toString());
                    return Optional.of(fromBytes(result.getBytes("uuid")));
                } else {
                    return Optional.empty();
                }
            }
        }
    }

    @Override
    public Optional<String> getName(UUID uuid) throws StorageException {
        Objects.requireNonNull(uuid);
        poolLock.readLock().lock();
        try {
            checkClosed();
            try (Connection connection = dataSource.getConnection()) {
                return getName(connection, uuid);
            }
        } catch (SQLException ex) {
            throw new StorageException(ex);
        } finally {
            poolLock.readLock().unlock();
        }
    }

    private Optional<String> getName(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement getByUuidStmnt = connection.prepareStatement(GET_BY_UUID_SQL)) {
            getByUuidStmnt.setBytes(1, toBytes(uuid));
            try (ResultSet result = getByUuidStmnt.executeQuery()) {
                if (result.first()) {
                    return Optional.of(result.getString("name"));
                } else {
                    return Optional.empty();
                }
            }
        }
    }

    @Override
    public OptionalLong getOnlineTime(UUID uuid) throws StorageException {
        Objects.requireNonNull(uuid);
        poolLock.readLock().lock();
        try {
            checkClosed();
            try (Connection connection = dataSource.getConnection()) {
                return getOnlineTime(connection, uuid);
            }
        } catch (SQLException ex) {
            throw new StorageException(ex);
        } finally {
            poolLock.readLock().unlock();
        }
    }

    private OptionalLong getOnlineTime(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement getByUuidStmnt = connection.prepareStatement(GET_BY_UUID_SQL)) {
            getByUuidStmnt.setBytes(1, toBytes(uuid));
            try (ResultSet result = getByUuidStmnt.executeQuery()) {
                if (result.first()) {
                    return OptionalLong.of(result.getLong("time"));
                } else {
                    return OptionalLong.empty();
                }
            }
        }
    }

    @Override
    public void addOnlineTime(UUID uuid, long additionalOnlineTime) throws StorageException {
        Objects.requireNonNull(uuid);
        poolLock.readLock().lock();
        try {
            checkClosed();
            try (Connection connection = dataSource.getConnection()) {
                addOnlineTime(connection, uuid, additionalOnlineTime);
            }
        } catch (SQLException ex) {
            throw new StorageException(ex);
        } finally {
            poolLock.readLock().unlock();
        }
    }

    private void addOnlineTime(Connection connection, UUID uuid, long additionalOnlineTime) throws SQLException {
        if (getName(connection, uuid).isPresent()) {
            try (PreparedStatement updateTimeStmnt = connection.prepareStatement(UPDATE_TIME_SQL)) {
                updateTimeStmnt.setLong(1, additionalOnlineTime);
                updateTimeStmnt.setBytes(2, toBytes(uuid));
                updateTimeStmnt.executeUpdate();
            }
        } else {
            try (PreparedStatement insertOrUpdateEntryStmnt = connection.prepareStatement(INSERT_OR_UPDATE_ENTRY_SQL)) {
                insertOrUpdateEntryStmnt.setBytes(1, toBytes(uuid));
                insertOrUpdateEntryStmnt.setNull(2, Types.CHAR);
                insertOrUpdateEntryStmnt.setNull(4, Types.CHAR);
                insertOrUpdateEntryStmnt.setLong(3, additionalOnlineTime);
                insertOrUpdateEntryStmnt.setLong(5, additionalOnlineTime);
                insertOrUpdateEntryStmnt.executeUpdate();
            }
        }
    }

    @Override
    public void setEntry(UUID uuid, String name) throws StorageException {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(name);
        poolLock.readLock().lock();
        try {
            checkClosed();
            try (Connection connection = dataSource.getConnection()) {
                setEntry(connection, uuid, name);
            }
        } catch (SQLException ex) {
            throw new StorageException(ex);
        } finally {
            poolLock.readLock().unlock();
        }
    }

    private void setEntry(Connection connection, UUID uuid, String name) throws SQLException {
        Optional<UUID> oldNameHolder = getUuid(connection, name);
        if (oldNameHolder.filter(oldUuid -> !oldUuid.equals(uuid)).isPresent()) { // name not unique ? update on duplicate uuid
            try (PreparedStatement unsetTakenNameStmnt = connection.prepareStatement(UNSET_TAKEN_NAME_SQL)) {
                unsetTakenNameStmnt.setBytes(1, toBytes(oldNameHolder.get()));
                unsetTakenNameStmnt.executeUpdate();
            }
        }
        try (PreparedStatement insertOrUpdateEntryStmnt = connection.prepareStatement(INSERT_OR_UPDATE_ENTRY_SQL)) {
            insertOrUpdateEntryStmnt.setBytes(1, toBytes(uuid));
            insertOrUpdateEntryStmnt.setString(2, name);
            insertOrUpdateEntryStmnt.setString(4, name);
            insertOrUpdateEntryStmnt.setLong(3, 0);
            insertOrUpdateEntryStmnt.setLong(5, 0);
            insertOrUpdateEntryStmnt.executeUpdate();
        }
    }

    private static byte[] toBytes(UUID id) {
        byte[] result = new byte[16];
        long lsb = id.getLeastSignificantBits();
        for (int i = 15; i >= 8; i--) {
            result[i] = (byte) (lsb & 0xffL);
            lsb >>= 8;
        }
        long msb = id.getMostSignificantBits();
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (msb & 0xffL);
            msb >>= 8;
        }
        return result;
    }

    private static UUID fromBytes(byte[] bytes) {
        long msb = 0;
        for (int i = 0; i < 8; i++) {
            msb <<= 8L;
            msb |= Byte.toUnsignedLong(bytes[i]);
        }
        long lsb = 0;
        for (int i = 8; i < 16; i++) {
            lsb <<= 8L;
            lsb |= Byte.toUnsignedLong(bytes[i]);;
        }
        return new UUID(msb, lsb);
    }

    private void checkClosed() throws StorageException {
        if (isClosed()) {
            throw new StorageException("closed");
        }
    }

    public boolean isClosed() {
        poolLock.readLock().lock();
        try {
            return dataSource.isClosed();
        } finally {
            poolLock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        if (isClosed()) {
            return;
        }
        poolLock.writeLock().lock();
        try {
            if (!isClosed()) {
                dataSource.close();
            }
        } finally {
            poolLock.writeLock().unlock();
        }
    }
}