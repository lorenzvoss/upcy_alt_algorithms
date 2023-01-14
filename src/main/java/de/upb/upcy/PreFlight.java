package de.upb.upcy;

import de.upb.maven.ecosystem.persistence.dao.Neo4JConnector;
import de.upb.upcy.base.sigtest.db.MongoDBHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;

/**
 * @author adann
 */
public final class PreFlight {
  private static final Logger LOGGER = LoggerFactory.getLogger(PreFlight.class);

  private static boolean reachableMongo() {
    LOGGER.info("Check if Mongo is up and running");
    try (Socket ignored = new Socket(MongoDBHandler.getMongoHostFromEnvironment(), 27017)) {
      return true;
    } catch (IOException ignored) {
      LOGGER.error("Cannot reach MongoDB {}", MongoDBHandler.getMongoHostFromEnvironment());
      return false;
    }
  }

  private static boolean reachableNeo4j() {
    LOGGER.info("Check if Neo4j is up and running");
    final String neo4jURL = Neo4JConnector.getNeo4jURL();
    final String cleardURL = neo4jURL.replace("bolt://", "");
    final String host = cleardURL.split(":")[0];
    final String port = cleardURL.split(":")[1];
    try (Socket ignored = new Socket(host, Integer.parseInt(port))) {
      return true;
    } catch (IOException ignored) {
      LOGGER.error("Cannot reach Neo4j: {}", host);
      return false;
    }
  }

  public static boolean preFlightCheck() {
    if (!reachableMongo()) {
      return false;
    }
    if (!reachableNeo4j()) {
      return false;
    }
    return true;
  }
}
