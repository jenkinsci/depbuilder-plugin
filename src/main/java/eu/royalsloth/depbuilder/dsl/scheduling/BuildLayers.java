package eu.royalsloth.depbuilder.dsl.scheduling;

import eu.royalsloth.depbuilder.dsl.ParsedBuildJob;

import java.util.*;

public class BuildLayers {

    /**
     * Ordered build layers (build should go from first layer to the last)
     */
    private final List<List<BuildJob>> buildLayers;
    /**
     * If there is a cycle in the dependency graph, this list contains the nodes that form the cycle
     */
    private final List<String> buildCycle = new ArrayList<>();

    /**
     * Id: buildNode mapping
     */
    private final Map<String, BuildJob> buildNodes = new HashMap<>();

    /**
     * Mapping that determines which child has which parent. Child id: set of parents TODO: if the build node
     * changes one setting (like build status) the hash will no longer find it in the provided set. We should
     * move back to normal ids?
     */
    private final Map<String, Set<BuildJob>> childIdParentMapping = new HashMap<>();

    /**
     * Class constructor for layers that has build cycle
     */
    public BuildLayers(List<String> buildCycle) {
        this.buildLayers = new ArrayList<>();
        this.buildCycle.addAll(buildCycle);
    }

    public BuildLayers(List<List<String>> buildLayers, List<ParsedBuildJob> parsedNodes) {
        for (ParsedBuildJob node : parsedNodes) {
            // we assume provided build nodes are unique and we don't have duplicates
            buildNodes.put(node.getId(), new BuildJob(node.getId(), node.getBuildSettings()));
        }

        // add children to build nodes (here we turn ids into actual build objects)
        for (ParsedBuildJob node : parsedNodes) {
            BuildJob bn = buildNodes.get(node.getId());
            for (String child : node.getChildren()) {
                BuildJob childBuildJob = buildNodes.get(child);
                assert childBuildJob != null : "Child build node should not be null, id: " + child;
                bn.addChild(childBuildJob);
            }
        }

        for (Map.Entry<String, BuildJob> entry : buildNodes.entrySet()) {
            BuildJob parent = entry.getValue();
            // create empty set for every node, so we don't end up with empty sets for nodes without a parent
            childIdParentMapping.computeIfAbsent(parent.getId(), v -> new HashSet<>());

            for (BuildJob child : parent.getChildren()) {
                Set<BuildJob> parentsOfChild = childIdParentMapping.computeIfAbsent(child.getId(),
                                                                                    v -> new HashSet<>());
                parentsOfChild.add(parent);
            }
        }

        // turn layers into build nodes
        this.buildLayers = new ArrayList<>();
        for (List<String> nodesInLayer : buildLayers) {
            List<BuildJob> buildNodesLayer = new ArrayList<>(nodesInLayer.size());
            for (String node : nodesInLayer) {
                buildNodesLayer.add(buildNodes.get(node));
            }
            this.buildLayers.add(buildNodesLayer);
        }
    }

    /**
     * Sort build nodes with their specified dependencies into layers. Each layer has to be processed before
     * we can process another layer.
     *
     * @param buildNodes nodes that will be sorted in correct build order
     * @return layers of dependencies
     */
    public static BuildLayers topologicalSort(List<ParsedBuildJob> buildNodes) {
        // node [children]
        Map<String, Set<String>> childrenMapping = new HashMap<>();
        // node [parents]
        Map<String, Set<String>> parentMapping = new HashMap<>();

        // fill both maps with correct data
        for (ParsedBuildJob buildNode : buildNodes) {
            // we don't know which are parents of build node (there could be none), but this ensures that
            // each build node is also added to dependency parent mapping
            parentMapping.computeIfAbsent(buildNode.getId(), s -> new HashSet<>());
            Set<String> childsOfBuildNode = childrenMapping.computeIfAbsent(buildNode.getId(), s -> new HashSet<>());
            childsOfBuildNode.addAll(buildNode.getChildren());

            // create a map of dependency : parent node
            for (String child : buildNode.getChildren()) {
                Set<String> parentOfChild = parentMapping.computeIfAbsent(child, s -> new HashSet<>());
                parentOfChild.add(buildNode.getId());
            }
        }

        final List<List<String>> layers = new ArrayList<>();
        final List<String> thisIterationIds = new ArrayList<>();
        while (parentMapping.size() > 0) {
            thisIterationIds.clear();

            // in every iteration there should be new nodes that are considered
            // leafs (as we remove the dependencies as part of the iteration).
            // If there is a cycle in the graph we won't find a node with 0 dependencies
            for (Map.Entry<String, Set<String>> entry : parentMapping.entrySet()) {
                // nodes which have 0 parent nodes (dependencies) are the leaf nodes and should be used
                // as the next step in build process
                final boolean isLeafNode = entry.getValue() == null || entry.getValue().isEmpty();
                if (isLeafNode) {
                    String leafNode = entry.getKey();
                    thisIterationIds.add(leafNode);
                }
            }

            // we don't have any node that would have 0 dependencies, that means we have a cycle in the graph
            // (or a bug in our code)
            //
            // @FUTURE: rewrite
            // The reason why cycles are checked in layers and not simply by iterating over every node
            // is because at first I thought transitive dependencies will mess things up and make the
            // cycle checker think we have a cycle, but later on I realized that's not the case.
            // Either there is a cycle in the graph or there isn't - transitive dependencies don't matter.
            // Given that number of nodes in this graph is not going to be that big (nobody is going to build
            // 1000 dependencies with this software), I just left the working and debugged code intact.
            // This code would probably be way simpler with a simple depth first search traversal.
            //
            //      A
            //   /  |  \
            //  B   |   C
            //   \  |  /
            //      D
            final boolean cycleInGraph = thisIterationIds.size() == 0;
            if (cycleInGraph) {
                // find the node with min dependencies and go from there.
                int minValue = Integer.MAX_VALUE;
                String parentWithLeastDependencies = "";

                if (layers.isEmpty()) {
                    // a cycle occurred before we even started resolving the build nodes
                    // start with the node with the least amount of dependencies (assuming this is the one
                    // at the top, but it's not guaranteed)
                    for (Map.Entry<String, Set<String>> entry : parentMapping.entrySet()) {
                        int numberOfDependencies = entry.getValue().size();
                        if (numberOfDependencies < minValue) {
                            parentWithLeastDependencies = entry.getKey();
                        }
                    }
                } else {
                    // Here we are trying to detect the first build cycle at the top of the graph.
                    // Without this extra hassle, we could detect a cycle at the bottom of the graph
                    // that may not even be there (due to transitive dependencies that are automatically
                    // resolved when building top to bottom).
                    Map<String, Set<String>> parentChildrenMap = new HashMap<>();
                    for (ParsedBuildJob node : buildNodes) {
                        Set<String> children = parentChildrenMap.computeIfAbsent(node.getId(), s -> new HashSet<>());
                        children.addAll(node.getChildren());
                    }
                    int lastLayer = layers.size() - 1;
                    while (lastLayer >= 0) {
                        // keep selecting the layers until you find the one that has at least some dependencies
                        Set<String> lastBuildLayerChildren = new HashSet<>();
                        List<String> lastBuiltLayer = layers.get(lastLayer);
                        for (String node : lastBuiltLayer) {
                            Set<String> parentChilds = parentChildrenMap.get(node);
                            if (parentChilds != null) {
                                lastBuildLayerChildren.addAll(parentChilds);
                            }
                        }

                        // leaf nodes are the ones at the bottom? or the ones at the top with no other parents
                        // get parent with least dependencies? Do we really care about that?
                        int minChildren = Integer.MAX_VALUE;
                        for (String buildChild : lastBuildLayerChildren) {
                            Set<String> buildChildParents = parentMapping.get(buildChild);
                            final boolean buildChildWasCompletelyBuilt = buildChildParents == null;
                            if (buildChildWasCompletelyBuilt) {
                                // try another child and try to find the one that was not completely
                                // built - there is a cycle happening
                                continue;
                            }
                            int numberOfDependencies = buildChildParents.size();
                            if (numberOfDependencies < minChildren) {
                                parentWithLeastDependencies = buildChild;
                            }
                        }

                        final boolean parentWithChildrenFoundInLayer = !parentWithLeastDependencies.isEmpty();
                        if (parentWithChildrenFoundInLayer) {
                            break;
                        }

                        // backtrack to previous layer and try to find at least one parent with children
                        // that are still not entirely resolved
                        lastLayer--;
                    }

                    // finished looping, but we did not find such parent that would be connected to cycle.
                    // This should never happen, but handling this case just in case
                    if ("".equals(parentWithLeastDependencies)) {
                        throw new IllegalStateException(
                                "Something is wrong in cycle detection layer, parent with least dependencies was not "
                                        + "detected");
                    }
                }

                // We have a build node at the top of the graph build chain, now we have to detect actual
                // nodes in the cycle of the graph below our selected node.
                //
                // With this method we only get the first cycle in the graph. If there are more they will
                // have to be detected after the first cycle is resolved and graph is checked again.
                Set<String> visited = new HashSet<>();
                Stack<String> recursionStack = new Stack<>();
                int iterationCount = 0;
                if (isCyclic(parentMapping, parentWithLeastDependencies, visited, recursionStack, iterationCount)) {
                    // since we use the depth first search we have to reverse the stack
                    // in order to get the node at which we started the traversal to be
                    // present as the first element in the array.
                    ArrayList<String> buildCycle = new ArrayList<>(recursionStack);
                    Collections.reverse(buildCycle);
                    if (!buildCycle.isEmpty()) {
                        // adding the first element to the last position so we form the closed cycle
                        buildCycle.add(buildCycle.get(0));
                    }

                    return new BuildLayers(buildCycle);
                }

                // not throwing an exception just so we don't blow with unexpected exceptions in production
                assert false : "We have a cycle in the graph but graph cycle checker did not detect it";
                System.out.println("We have a cycle in the graph, but our method did not detect it");
                return new BuildLayers(new ArrayList<>(recursionStack));
            }

            List<String> buildLayer = new ArrayList<>();
            for (String parentNodeId : thisIterationIds) {
                // add it to current execution layer (and remove it from mapping so we don't
                // process it once again)
                buildLayer.add(parentNodeId);
                parentMapping.remove(parentNodeId);

                // remove the dependency edges of the parent node
                Set<String> children = childrenMapping.get(parentNodeId);
                if (children != null) {
                    for (String child : children) {
                        Set<String> parentNodesOfChild = parentMapping.get(child);
                        parentNodesOfChild.remove(parentNodeId);
                    }
                }
            }
            layers.add(buildLayer);
        }

        // everything is fine, we have a directed acyclic graph
        // return new BuildLayers(layers);
        return new BuildLayers(layers, buildNodes);
    }

    /**
     * Check if there is a cycle in the build cycle graph by using depth first search
     *
     * @return true if there is a cycle in the graph and false if graph is acyclic
     */
    private static boolean isCyclic(Map<String, Set<String>> dependencies, String buildNode,
            Set<String> visited, Stack<String> path, int iteration) {
        // make sure we don't blow up with infinite cycles. We assume nobody is going to try and build a system
        // with 10_000 dependencies (unless they are building npm package)
        iteration++;
        if (iteration > 10_000) {
            throw new IllegalStateException(
                    String.format("Infinite cycle detected, there is a bug in the code when checking %s node cycle",
                                  buildNode));
        }

        if (visited.contains(buildNode)) {
            // node is not added to the recursion stack, because we have to
            // reverse the stack before connecting the first and last graph node.
            // Reverse is used since we use depth first search so the bottom
            // of the cycle will appear as the first node in the stack which we don't want.
            // Last node in the stack is actually the first node in the graph
            // that is the node from which we started the traversal
            return true;
        }

        // build node was not part of the recursion stack and was not visited before
        path.add(buildNode);
        visited.add(buildNode);

        // iterate over all the dependencies (depth first traversal) and check if any of those paths contain
        // a cycle - it may be better to go breadth first search to detect earlier cycle instead of the one
        // at the bottom of the graph (in case there are multiple graphs)
        Set<String> rootNodeDeps = dependencies.get(buildNode);
        for (String child : rootNodeDeps) {
            if (isCyclic(dependencies, child, visited, path, iteration)) {
                return true;
            }
        }

        // cycle was not found in graph
        return false;
    }

    public List<List<BuildJob>> getLayers() {
        return this.buildLayers;
    }

    public List<List<String>> getOrderedBuildLayers() {
        List<List<String>> layers = new ArrayList<>();
        for (List<BuildJob> nodesInLayer : this.buildLayers) {
            List<String> layer = new ArrayList<>(nodesInLayer.size());
            for (BuildJob node : nodesInLayer) {
                layer.add(node.getId());
            }
            layers.add(layer);
        }
        return layers;
    }

    public boolean hasCycle() {
        return this.buildCycle.size() > 0;
    }

    public List<String> getBuildCycle() {
        return buildCycle;
    }

    public int getNumberOfBuildNodes() {
        return this.buildNodes.size();
    }

    public BuildJob getBuildNode(String node) {
        return this.buildNodes.get(node);
    }

    public Set<BuildJob> getParents(BuildJob node) {
        Set<BuildJob> nodes = this.childIdParentMapping.get(node.getId());
        assert nodes != null : String.format("Parent nodes for build node %s are null", node.getId());
        return nodes;
    }
}
