package dev.nezz.chainledger.node;

import dev.nezz.chainledger.ChainledgerApplication;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Starts real nodes on random ports, each with its own PostgreSQL schema in the test database. */
final class TestNodes {

    static final String BASE_URL = System.getenv().getOrDefault("TEST_DATABASE_URL",
            "jdbc:postgresql://localhost:5432/chainledger_test");
    static final String USER = System.getenv().getOrDefault("DATABASE_USER", System.getProperty("user.name"));
    static final String PASSWORD = System.getenv().getOrDefault("DATABASE_PASSWORD", "");

    private TestNodes() {
    }

    static void resetSchema(String schema) {
        try (Connection c = DriverManager.getConnection(BASE_URL, USER, PASSWORD); Statement s = c.createStatement()) {
            s.execute("drop schema if exists " + schema + " cascade");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    static void execute(String schema, String sql) {
        try (Connection c = DriverManager.getConnection(BASE_URL + "?currentSchema=" + schema, USER, PASSWORD);
             Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    static ConfigurableApplicationContext start(String nodeId, String schema) {
        return new SpringApplicationBuilder(ChainledgerApplication.class).run(
                "--server.port=0",
                "--chainledger.node-id=" + nodeId,
                "--chainledger.difficulty-bits=10",
                "--chainledger.mining-threads=2",
                "--spring.datasource.url=" + BASE_URL + "?currentSchema=" + schema,
                "--spring.datasource.username=" + USER,
                "--spring.datasource.password=" + PASSWORD,
                "--spring.flyway.schemas=" + schema,
                "--logging.level.root=WARN");
    }

    static String url(ConfigurableApplicationContext context) {
        return "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
    }
}
