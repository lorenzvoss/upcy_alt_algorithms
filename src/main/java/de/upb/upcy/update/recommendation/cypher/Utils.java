package de.upb.upcy.update.recommendation.cypher;

import de.upb.thetis.graph.GraphModel;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

public class Utils {
  public static String getNodeNameForCypher(GraphModel.Artifact artifact) {
    if (artifact == null) {
      return "n" + UUID.randomUUID().toString().replaceAll("[^A-Za-z0-9]", "_");
    }

    String nodeName;
    if (StringUtils.isNotBlank(artifact.getId())) {
      String id = artifact.getId();
      // replace non printable characters
      nodeName = "n" + id.replaceAll("[^A-Za-z0-9]", "_");
    } else {
      nodeName = "n" + artifact.getNumericId();
    }
    return nodeName;
  }

  public static String getPathName(
      GraphModel.Artifact srcArtifact, GraphModel.Artifact tgtArtifact) {
    return "p" + getNodeNameForCypher(srcArtifact) + "_" + getNodeNameForCypher(tgtArtifact);
  }

  public static String getRelationShipName(
      GraphModel.Artifact srcArtifact, GraphModel.Artifact tgtArtifact) {
    return "r" + getNodeNameForCypher(srcArtifact) + "_" + getNodeNameForCypher(tgtArtifact);
  }
}