package ru.allfire.coreprotectionassistant.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface IDatabase {
    boolean connect();
    void disconnect();
    Connection getConnection() throws SQLException;
    void createTables();
}
