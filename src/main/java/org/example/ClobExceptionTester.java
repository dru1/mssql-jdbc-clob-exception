package org.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import picocli.CommandLine;

import javax.annotation.Nonnull;
import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.sql.*;
import java.util.Arrays;
import java.util.List;

public class ClobExceptionTester {

    private static final Logger logger = LoggerFactory.getLogger(ClobExceptionTester.class);

    private static final String DEFAULT_HOST = "localhost:1433";
    private static final String DEFAULT_ENCRYPT = "false";
    private static final String DEFAULT_TRUST_SERVER_CERTIFICATE = "false";
    private static final String DEFAULT_ENABLE_CONNECTION_POOL = "false";
    private static final String DEFAULT_LOGIN_TIMEOUT = "30";
    private static final String DEFAULT_QUERY = "SELECT 'select a clob column';";

    @CommandLine.Option(names = {"-h", "--host"}, defaultValue = DEFAULT_HOST, description = "Default: " + DEFAULT_HOST)
    private String host;

    @CommandLine.Option(names = {"-d", "--database"}, required = true)
    private String database;

    @CommandLine.Option(names = {"-u", "--user"}, required = true)
    private String user;

    @CommandLine.Option(names = {"-p", "--password"}, required = true)
    private String password;

    @CommandLine.Option(names = {"-q", "--query"}, defaultValue = DEFAULT_QUERY, description = "Default: " + DEFAULT_QUERY)
    private String query;

    @CommandLine.Option(names = {"--encrypt"}, defaultValue = DEFAULT_ENCRYPT, description = "Default: " + DEFAULT_ENCRYPT)
    private boolean encrypt;

    @CommandLine.Option(names = {"--trustServerCertificate"}, defaultValue = DEFAULT_TRUST_SERVER_CERTIFICATE,
            description = "Default: " + DEFAULT_TRUST_SERVER_CERTIFICATE)
    private boolean trustServerCertificate;

    @CommandLine.Option(names = {"--enableConnectionPool"}, defaultValue = DEFAULT_ENABLE_CONNECTION_POOL,
            description = "Default: " + DEFAULT_ENABLE_CONNECTION_POOL)
    private boolean enableConnectionPool;

    @CommandLine.Option(names = {"--loginTimeout"}, defaultValue = DEFAULT_LOGIN_TIMEOUT, description = "Default: " + DEFAULT_LOGIN_TIMEOUT)
    private int loginTimeout;

    @CommandLine.Option(names = {"--help"}, usageHelp = true, description = "Show Help")
    private boolean helpRequested;

    private DataSource dataSource;

    public static void main(String[] args) {
        ClobExceptionTester exceptionTester = CommandLine.populateCommand(new ClobExceptionTester(), args);

        if (exceptionTester.helpRequested) {
            CommandLine.Help.ColorScheme colorScheme = new CommandLine.Help.ColorScheme()
                    .commands(CommandLine.Help.Ansi.Style.bold, CommandLine.Help.Ansi.Style.underline)
                    .options(CommandLine.Help.Ansi.Style.fg_yellow)
                    .parameters(CommandLine.Help.Ansi.Style.fg_yellow)
                    .optionParams(CommandLine.Help.Ansi.Style.italic);
            CommandLine.usage(new ClobExceptionTester(), System.out, colorScheme);
        } else {
            try {
                exceptionTester.configure();
                exceptionTester.run();
            } finally {
                exceptionTester.stop();
            }
        }
    }

    private void configure() {
        SLF4JBridgeHandler.install();
    }

    private void run() {
        logger.info("Connecting to {}", host);
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            logger.info("Connection to {} established", host);
            logger.info("Executing query {}", query);
            try (ResultSet resultSet = statement.executeQuery(query)) {
                while (resultSet.next()) {
                    logger.info("Row: {}", readRow(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.error("SQL Exception", e);
        }
        logger.info("Connection to {} closed", host);
    }

    private void stop() {
        if (dataSource instanceof Closeable) {
            try {
                ((Closeable) dataSource).close();
            } catch (IOException e) {
                logger.error("Cannot close data source", e);
            }
        }
    }

    @Nonnull
    private Connection getConnection() throws SQLException {
        if (enableConnectionPool) {
            return getDataSource().getConnection();
        } else {
            return DriverManager.getConnection(getJdbcUrl(), user, password);
        }
    }

    @Nonnull
    private DataSource getDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(getJdbcUrl());
            config.setUsername(user);
            config.setPassword(password);
            dataSource = new HikariDataSource(config);
        }

        return dataSource;
    }

    @Nonnull
    private String getJdbcUrl() {
        return String.format("jdbc:sqlserver://%s;database=%s;encrypt=%b;trustServerCertificate=%b;loginTimout=%d;",
                host, database, encrypt, trustServerCertificate, loginTimeout);
    }

    @Nonnull
    private List<String> readRow(@Nonnull ResultSet resultSet) throws SQLException {
        int columns = resultSet.getMetaData().getColumnCount();
        List<String> row = Arrays.asList(new String[columns]);

        for (int column = 1; column <= columns; column++) {
            if (readColumnAsClob(resultSet, column)) {
                try {
                    row.set(column - 1, readFully(resultSet.getClob(column).getCharacterStream()));
                } catch (IOException e) {
                    throw new SQLException("Cannot read column " + column, e);
                }
            } else {
                row.set(column - 1, resultSet.getString(column));
            }
        }

        return row;
    }

    private boolean readColumnAsClob(@Nonnull ResultSet resultSet, int column) throws SQLException {
        int columnType = resultSet.getMetaData().getColumnType(column);
        int precision = resultSet.getMetaData().getPrecision(column);

        switch (columnType) {
            case Types.VARCHAR:                 // 12
            case Types.NVARCHAR:                // -9
                return precision >= 1073741823; // VARCHAR(MAX), NVARCHAR(MAX)
            case Types.CLOB:                    // 2005
            case Types.NCLOB:                   // 2011
                return true;
            default:
                return false;
        }
    }

    @Nonnull
    private String readFully(@Nonnull Reader reader) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            StringBuilder content = new StringBuilder();
            int value;
            while ((value = bufferedReader.read()) != -1) {
                content.append((char) value);
            }
            return content.toString();
        }
    }

}
