package gov.cms.bfd.model.rif.schema;

import com.justdavis.karl.misc.exceptions.BadCodeMonkeyException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.persist.HsqlProperties;
import org.hsqldb.server.ServerAcl.AclFormatException;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Provides utilities for managing the database in integration tests.
 *
 * <p>Note: This is placed in <code>src/main/java</code> (rather than <code>src/test/java</code>)
 * for convenience: test dependencies aren't transitive, which tends to eff things up.
 */
public final class DatabaseTestHelper {
  /**
   * This fake JDBC URL prefix is used for custom database setups only used in integration tests,
   * e.g. {@link #createDataSourceForHsqlEmbeddedWithServer(String)}.
   */
  public static final String JDBC_URL_PREFIX_BLUEBUTTON_TEST = "jdbc:bfd-test:";

  private static final String HSQL_SERVER_USERNAME = "test";
  private static final String HSQL_SERVER_PASSWORD = "test";

  /** @return the JDBC URL for the test DB to use */
  private static String getTestDatabaseJdbcUrl() {
    // Build a default DB URL that uses HSQL, just as it's configured in the parent POM.
    String urlDefault = String.format("%shsqldb:mem", JDBC_URL_PREFIX_BLUEBUTTON_TEST);

    // Pull the DB URL from the system properties.
    String url = System.getProperty("its.db.url", urlDefault);

    return url;
  }

  /** @return the username for the test DB to use */
  private static String getTestDatabaseUsername() {
    // Pull the DB URL from the system properties.
    String username = System.getProperty("its.db.username", null);
    if (username != null && username.trim().isEmpty()) username = null;
    return username;
  }

  /** @return the password for the test DB to use */
  private static String getTestDatabasePassword() {
    // Pull the DB URL from the system properties.
    String password = System.getProperty("its.db.password", null);
    if (password != null && password.trim().isEmpty()) password = null;
    return password;
  }

  /**
   * @return a {@link DataSource} for the test DB, which will <strong>not</strong> be cleaned or
   *     schema-fied first
   */
  public static ComponentDataSource getTestDatabase() {
    String url = getTestDatabaseJdbcUrl();
    String username = getTestDatabaseUsername();
    String password = getTestDatabasePassword();
    return getTestDatabase(url, username, password);
  }

  /**
   * @param url the JDBC URL for the test database to connect to
   * @param username the username for the test database to connect to
   * @param password the password for the test database to connect to
   * @return a {@link DataSource} for the test DB, which will <strong>not</strong> be cleaned or
   *     schema-fied first
   */
  public static ComponentDataSource getTestDatabase(String url, String username, String password) {
    ComponentDataSource dataSource;
    if (url.startsWith(JDBC_URL_PREFIX_BLUEBUTTON_TEST + "hsqldb:mem")) {
      dataSource = createDataSourceForHsqlEmbeddedWithServer(url);
    } else if (url.startsWith("jdbc:hsqldb:hsql://localhost")) {
      dataSource = createDataSourceForHsqlServer(url, username, password);
    } else if (url.startsWith("jdbc:postgresql:")) {
      dataSource = createDataSourceForPostgresql(url, username, password);
    } else {
      throw new BadCodeMonkeyException("Unsupported test DB URL: " + url);
    }

    return dataSource;
  }

  /** @return a {@link DataSource} for the test DB, which will be cleaned (i.e. wiped) first */
  public static ComponentDataSource getTestDatabaseAfterClean() {
    ComponentDataSource dataSource = getTestDatabase();

    // Try to prevent career-limiting moves.
    String url = getTestDatabaseJdbcUrl();
    if (!url.contains("localhost") && !url.contains("127.0.0.1") && !url.contains("hsqldb:mem")) {
      throw new BadCodeMonkeyException("Our builds can only be run against local test DBs.");
    }

    // Clean the DB so that it's fresh and ready for a new test case.
    Flyway flyway = new Flyway();
    flyway.setDataSource(dataSource);
    flyway.clean();
    return dataSource;
  }

  /**
   * @return a {@link DataSource} for the test DB, which will be cleaned (i.e. wiped) and then have
   *     the BFD schema applied to it, first
   */
  public static ComponentDataSource getTestDatabaseAfterCleanAndSchema() {
    ComponentDataSource dataSource = getTestDatabaseAfterClean();

    // Schema-ify it so it's ready to go.
    DatabaseSchemaManager.createOrUpdateSchema(dataSource);

    return dataSource;
  }

  /**
   * Creates an embedded HSQL DB that is also accessible on a local port (via {@link
   * org.hsqldb.server.Server}).
   *
   * @param url the JDBC URL that the application was configured to use
   * @return a HSQL {@link DataSource} for the test DB
   */
  private static ComponentDataSource createDataSourceForHsqlEmbeddedWithServer(String url) {
    if (!url.startsWith(JDBC_URL_PREFIX_BLUEBUTTON_TEST)) {
      throw new IllegalArgumentException();
    }
    if (!url.endsWith(":hsqldb:mem")) {
      throw new IllegalArgumentException();
    }

    /*
     * Select a random local port to run the HSQL DB server on, so that one test run doesn't
     * conflict with another.
     */
    int hsqldbPort = findFreePort();

    HsqlProperties hsqlProperties = new HsqlProperties();
    hsqlProperties.setProperty(
        "server.database.0",
        String.format(
            "mem:test-embedded;user=%s;password=%s", HSQL_SERVER_USERNAME, HSQL_SERVER_PASSWORD));
    hsqlProperties.setProperty("server.dbname.0", "test-embedded");
    hsqlProperties.setProperty("server.port", "" + hsqldbPort);
    hsqlProperties.setProperty("hsqldb.tx", "mvcc");
    org.hsqldb.server.Server server = new org.hsqldb.server.Server();

    try {
      server.setProperties(hsqlProperties);
    } catch (IOException | AclFormatException e) {
      throw new BadCodeMonkeyException(e);
    }

    server.setLogWriter(null);
    server.setErrWriter(null);
    server.start();

    // Create the DataSource to connect to that shiny new DB.
    ComponentDataSource dataSource =
        createDataSourceForHsqlServer(
            String.format("jdbc:hsqldb:hsql://localhost:%d/test-embedded", hsqldbPort),
            HSQL_SERVER_USERNAME,
            HSQL_SERVER_PASSWORD);
    return dataSource;
  }

  /**
   * @param url the JDBC URL that the application was configured to use
   * @param username the username for the test database to connect to
   * @param password the password for the test database to connect to
   * @return a HSQL {@link DataSource} for the test DB
   */
  private static ComponentDataSource createDataSourceForHsqlServer(
      String url, String username, String password) {
    if (!url.startsWith("jdbc:hsqldb:hsql://localhost")) {
      throw new IllegalArgumentException();
    }

    JDBCComponentDataSource dataSource = new JDBCComponentDataSource();
    dataSource.setUrl(url);
    if (username != null) dataSource.setUser(username);
    if (password != null) dataSource.setPassword(password);

    return dataSource;
  }

  /**
   * Note: It's possible for this to result in race conditions, if the random port selected enters
   * use after this method returns and before whatever called this method gets a chance to grab it.
   * It's pretty unlikely, though, and there's not much we can do about it, either. So.
   *
   * @return a free local port number
   */
  private static int findFreePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * @param url the PostgreSQL JDBC URL to use
   * @param username the username for the test database to connect to
   * @param password the password for the test database to connect to
   * @return a PostgreSQL {@link DataSource} for the test DB
   */
  private static ComponentDataSource createDataSourceForPostgresql(
      String url, String username, String password) {
    PGComponentDataSource dataSource = new PGComponentDataSource();
    dataSource.setUrl(url);
    if (username != null) dataSource.setUser(username);
    if (password != null) dataSource.setPassword(password);
    return dataSource;
  }

  /**
   * Represents the components required to construct a {@link DataSource} for our test DBs.
   *
   * <p>This is wildly insufficient for more complicated {@link DataSource}s; we're leaning heavily
   * on the very constrained set of simple {@link DataSource}s that are supported for our tests.
   */
  public static final class DataSourceComponents {
    private final String url;
    private final String username;
    private final String password;

    /**
     * Constructs a {@link DataSourceComponents} instance for the specified test {@link DataSource}
     * (does not support more complicated {@link DataSource}s, as discussed in the class' JavaDoc)
     */
    public DataSourceComponents(ComponentDataSource dataSource) {
      this.url = dataSource.getUrl();
      this.username = dataSource.getUser();
      this.password = dataSource.getPassword(); // no getter available; hardcoded
    }

    /** @return the JDBC URL that should be used to connect to the test DB */
    public String getUrl() {
      return url;
    }

    /** @return the username that should be used to connect to the test DB */
    public String getUsername() {
      return username;
    }

    /** @return the password that should be used to connect to the test DB */
    public String getPassword() {
      return password;
    }
  }

  public interface ComponentDataSource extends DataSource {
    String getUrl();

    String getUser();

    String getPassword();
  }

  public static final class JDBCComponentDataSource extends JDBCDataSource
      implements ComponentDataSource {
    /*
    	public static void setPassword(String password) {
    		this.password = password
    	}
    */
    public String getPassword() {
      return super.password;
    }
    /*
    public String getUsername() {
      return super.getUser();
    }

    public String getUrl() {
      return super.getUrl();
    }*/
  }

  public static final class PGComponentDataSource extends PGSimpleDataSource
      implements ComponentDataSource {}
}
