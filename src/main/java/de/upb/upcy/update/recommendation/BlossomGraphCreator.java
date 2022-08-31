package de.upb.upcy.update.recommendation;

import de.upb.thetis.graph.GraphModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jgrapht.Graph;
import org.jgrapht.graph.AbstractBaseGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlossomGraphCreator {

  private static final Logger LOGGER = LoggerFactory.getLogger(BlossomGraphCreator.class);

  private final Graph<GraphModel.Artifact, GraphModel.Dependency> depGraph;
  private final GraphModel.Artifact rootNode;

  private final Map<GraphModel.Artifact, GraphModel.Artifact> nodeToBlossom;
  private final Map<GraphModel.Artifact, List<GraphModel.Artifact>> blossomToNode;

  public BlossomGraphCreator(
      Graph<GraphModel.Artifact, GraphModel.Dependency> depGraph, GraphModel.Artifact rootNode) {

    this.depGraph = depGraph;
    this.rootNode = rootNode;
    nodeToBlossom = new HashMap<>();
    blossomToNode = new HashMap<>();
  }

  private Map<String, List<GraphModel.Artifact>> getBlossomNodes(
      Graph<GraphModel.Artifact, GraphModel.Dependency> depGraph, GraphModel.Artifact rootNode) {
    // compute blossoms
    final Map<String, List<GraphModel.Artifact>> blossomNodes =
        depGraph.vertexSet()
            .stream() // ignore the groupId of the project that is analyzed, e.g., multi module
            // projects
            .filter(x -> !StringUtils.equals(x.getGroupId(), rootNode.getGroupId()))
            .collect(Collectors.groupingBy(GraphModel.Artifact::getGroupId));

    // remove blossoms that only contain one node
    for (Iterator<Map.Entry<String, List<GraphModel.Artifact>>> iter =
            blossomNodes.entrySet().iterator();
        iter.hasNext(); ) {
      Map.Entry<String, List<GraphModel.Artifact>> curr = iter.next();
      if (curr.getValue() == null || curr.getValue().size() < 2) {
        iter.remove();
      }
    }
    return blossomNodes;
  }

  public Graph<GraphModel.Artifact, GraphModel.Dependency> buildBlossomDepGraph() {
    // create blossom-Graph for min-cut computation
    @SuppressWarnings("unchecked")
    final Graph<GraphModel.Artifact, GraphModel.Dependency> blossemedDepGraph =
        (Graph<GraphModel.Artifact, GraphModel.Dependency>) ((AbstractBaseGraph) depGraph).clone();

    // compute the blossom nodes
    final Map<String, List<GraphModel.Artifact>> groupBlossom =
        getBlossomNodes(blossemedDepGraph, rootNode);

    // create the blossom nodes
    List<GraphModel.Artifact> artsToRemove = new ArrayList<>();
    for (Map.Entry<String, List<GraphModel.Artifact>> entry : groupBlossom.entrySet()) {
      // create the blossom node and
      GraphModel.Artifact blossomNode = new GraphModel.Artifact();
      blossomNode.setGroupId(entry.getKey());
      blossomNode.setArtifactId("blossomNode");
      blossomNode.setVersion(entry.getValue().get(0).getVersion());
      blossomNode.setId(UUID.randomUUID().toString());

      final Set<String> collect =
          entry.getValue().stream()
              .map(GraphModel.Artifact::getScopes)
              .flatMap(List::stream)
              .collect(Collectors.toSet());
      blossomNode.setScopes(new ArrayList<>(collect));
      blossemedDepGraph.addVertex(blossomNode);

      for (GraphModel.Artifact artifact : entry.getValue()) {

        // initialize the hashmaps for expand operation
        nodeToBlossom.put(artifact, blossomNode);
        final List<GraphModel.Artifact> artifacts =
            blossomToNode.computeIfAbsent(blossomNode, (k) -> new ArrayList<>());
        artifacts.add(artifact);

        // redirect the edges
        final Set<GraphModel.Dependency> dependencies = blossemedDepGraph.edgesOf(artifact);
        for (GraphModel.Dependency depEdge : dependencies) {
          GraphModel.Artifact edgeSource = blossemedDepGraph.getEdgeSource(depEdge);
          GraphModel.Artifact edgeTarget = blossemedDepGraph.getEdgeTarget(depEdge);
          // redirect the edges
          if (edgeTarget == artifact) {
            edgeTarget = blossomNode;
          }
          if (edgeSource == artifact) {
            edgeSource = blossomNode;
          }
          // rm the old edge
          blossemedDepGraph.removeEdge(depEdge);
          artsToRemove.add(artifact);
          blossemedDepGraph.addEdge(edgeSource, edgeTarget, depEdge);
        }
      }
    }

    artsToRemove.forEach(blossemedDepGraph::removeVertex);

    LOGGER.info(
        "Created blossom graph with v:{} and e:{} ",
        blossemedDepGraph.vertexSet().size(),
        blossemedDepGraph.edgeSet().size());

    return blossemedDepGraph;
  }

  /**
   * Returns the expanded blossom nodes or null if it is not a blossom
   *
   * @param blossom
   * @return
   */
  public Collection<GraphModel.Artifact> expandBlossomNode(GraphModel.Artifact blossom) {

    return blossomToNode.get(blossom);
  }

  /**
   * Returns the blossom node or null if this is not part of a blossom
   *
   * @param artifact
   * @return
   */
  public GraphModel.Artifact getBlossomNode(GraphModel.Artifact artifact) {

    return nodeToBlossom.get(artifact);
  }

  public boolean isBlossomNode(GraphModel.Artifact oneNode, GraphModel.Artifact secNode) {
    return isBlossomNode(oneNode.getGroupId(), secNode.getGroupId());
  }

  public boolean isBlossomNode(String oneNodeGroupId, String secNodeGroupId) {
    if (StringUtils.equals(oneNodeGroupId, secNodeGroupId)) {
      LOGGER.trace("Skipped for blossom group");
      return true;
    } // special case for springframework
    if (StringUtils.startsWith(oneNodeGroupId, "org.springframework")
        && StringUtils.startsWith(secNodeGroupId, "org.springframework")) {
      LOGGER.trace("Skipped for blossom group");
      return true;
    }
    return false;
  }
}