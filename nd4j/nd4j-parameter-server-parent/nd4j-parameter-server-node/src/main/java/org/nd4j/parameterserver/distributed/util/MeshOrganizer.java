/*******************************************************************************
 * Copyright (c) 2015-2018 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package org.nd4j.parameterserver.distributed.util;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.nd4j.parameterserver.distributed.enums.MeshBuildMode;


import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class provides methods for ephemeral mesh network management
 *
 * @author raver119@gmail.com
 */
@NoArgsConstructor
@Slf4j
public class MeshOrganizer implements Serializable {
    private static final long serialVersionUID = 1L;

    private MeshBuildMode buildMode = MeshBuildMode.SYMMETRIC_MODE;

    // this value determines max number of direct downstream connections for any given node (affects root node as well)
    public static final int MAX_DOWNSTREAMS = 3;

    // max distance from root
    private static final int MAX_DEPTH = 5;

    // just shortcut to the root node of the tree
    @Getter(AccessLevel.PROTECTED) private Node rootNode = new Node();

    // SortedSet, with sort by number of downstreams
    private List<Node> sortedNodes = new ArrayList<>();

    // flattened map of the tree, ID -> Node
    private Map<String, Node> nodeMap = new HashMap<>();

    // used in DEPTH_MODE
    private Node lastRoot = null;

    public MeshOrganizer(@NonNull MeshBuildMode mode) {
        this.buildMode = mode;
    }

    /**
     * This method adds new node to the network
     *
     * PLEASE NOTE: Default port 40123 is used
     * @param ip
     */
    protected Node  addNode(@NonNull String ip) {
        return addNode(ip, 40123);
    }

    /**
     * This methods adds new node to the network
     */
    public Node addNode(@NonNull String ip, @NonNull int port) {
        val node = Node.builder()
                .ip(ip)
                .port(port)
                .upstream(null)
                .build();

         return this.addNode(node);
    }


    public synchronized Node addNode(@NonNull Node node) {

        // if node isn't mapped yet - in this case we're mapping node automatically here
        if (node.getUpstreamNode() == null) {
            if (rootNode.numberOfDownstreams() < MAX_DOWNSTREAMS) {
                if (lastRoot == null)
                    lastRoot = node;

                rootNode.addDownstreamNode(node);
                sortedNodes.add(node);
            } else
                switch (buildMode) {
                    case DEPTH_FIRST:
                    case WIDTH_FIRST: {
                            // if lastRoot isn't full yet - we'll just add new node to it (this one)
                            if (lastRoot.numberOfDownstreams() < MAX_DOWNSTREAMS)
                                lastRoot.addDownstreamNode(node);
                            else { // or we'll pull next node otherwise
                                val upstream = lastRoot.getUpstreamNode();

                                Node c = upstream.getNextCandidate(lastRoot);

                                if (c == null)
                                    c = upstream.getNextCandidate(null);

                                // if we've maxed out number of downstreams - just step down
                                if (c.numberOfDownstreams() >= MAX_DOWNSTREAMS)
                                    c = c.downstream.get(0);

                                c.addDownstreamNode(node);
                                lastRoot = c;
                            }
                        };
                        break;
                    case SYMMETRIC_MODE: {
                            val f = sortedNodes.get(0);
                            f.addDownstreamNode(node);

                            // we update sorted list, so we always know node with least number of
                            sortedNodes.add(node);
                            Collections.sort(sortedNodes);
                        }
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }
        }

        // we should check if this node has any descendants
        if (node.numberOfDownstreams() > 0) {
            // if true - we should ensure they have their room in this mesh
        }

        // after all we add this node to the flattened map, for future access
        nodeMap.put(node.getId(), node);

        return node;
    }


    /**
     * This method removes  node from tree
     */
    public void removeNode() {
        //
    }


    /**
     * This method returns true, if node is known
     * @return
     */
    public boolean isKnownNode(@NonNull String ip) {
        return nodeMap.containsKey(ip);
    }


    /**
     * This method reconnects given node to another node
     */
    public void remapNode() {
        //
    }

    /**
     * This method returns upstream connection for a given node
     */
    public void getUpstreamForNode() {
        //
    }

    /**
     * This method returns downstream connections for a given node
     */
    public void getDownstreamsForNode() {
        //
    }

    /**
     * This method returns total number of nodes below given one
     * @return
     */
    public long numberOfDescendantsOfNode() {
        return rootNode.numberOfDescendants();
    }

    /**
     * This method returns total number of nodes in this mesh
     *
     * PLESE NOTE: this method INCLUDES root node
     * @return
     */
    public long totalNodes() {
        return rootNode.numberOfDescendants() + 1;
    }

    /**
     * This method returns size of flattened map of nodes.
     * Suited for tests.
     *
     * @return
     */
    protected long flatSize() {
        return (long) nodeMap.size();
    }

    /**
     * This method returns our mesh as collection of nodes
     * @return
     */
    protected Collection<Node> flatNodes() {
        return nodeMap.values();
    }

    /**
     * This method returns Node representing given Id
     * @return
     */
    protected Node getNodeById() {
        return null;
    }

    /**
     * This method returns Node representing given IP
     * @return
     */
    protected Node getNodeByIp() {
        return null;
    }

    /**
     * This class represents basic tree node
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Data
    public static class Node implements Serializable, Comparable<Node> {
        private static final long serialVersionUID = 1L;

        @Getter(AccessLevel.PUBLIC)
        @Setter(AccessLevel.PROTECTED)
        @Builder.Default
        private boolean rootNode = false;

        @Getter
        private String id;

        @Getter
        private String ip;

        @Getter
        private int port;

        @Getter(AccessLevel.NONE)
        @Setter(AccessLevel.NONE)
        private Node upstream;

        @Getter(AccessLevel.NONE)
        @Setter(AccessLevel.NONE)
        private final List<Node> downstream = new ArrayList<>();


        protected Node getNextCandidate(Node node) {
            // if there's no candidates - just connect to this node
            if (downstream.size() == 0)
                return this;

            if (node == null)
                return downstream.get(0);

            // TODO: we can get rid of flat scan here, but it's one-off step anyway...

            // we return next node node after this node
            boolean b = false;
            for (val v: downstream) {
                if (b)
                    return v;

                if (Objects.equals(node, v))
                    b = true;
            }

            return null;
        }

        protected Node(boolean rootNode) {
            this.rootNode = rootNode;
        }

        /**
         * This method adds downstream node to the list of connections
         * @param node
         * @return
         */
        protected Node addDownstreamNode(@NonNull Node node) {
            this.downstream.add(node);
            node.setUpstreamNode(this);
            return node;
        }

        /**
         * This method allows to set master node for this node
         * @param node
         * @return
         */
        protected Node setUpstreamNode(@NonNull Node node) {
            this.upstream = node;
            return node;
        }

        /**
         * This method returns the node this one it connected to
         * @return
         */
        protected Node getUpstreamNode() {
            return upstream;
        }

        /**
         * This method returns number of downstream nodes connected to this node
         * @return
         */
        public long numberOfDescendants() {
            val cnt = new AtomicLong(downstream.size());

            for (val n: downstream)
                cnt.addAndGet(n.numberOfDescendants());

            return cnt.get();
        }

        /**
         * This method returns number of nodes that has direct connection for this node
         * @return
         */
        public long numberOfDownstreams() {
            return downstream.size();
        }

        /**
         * This method returns collection of nodes that have direct connection to this node
         * @return
         */
        public Collection<Node> getDownstreamNodes() {
            return downstream;
        }

        /**
         * This method returns number of hops between
         * @return
         */
        public int distanceFromRoot() {
            if (upstream.isRootNode())
                return 1;
            else
                return upstream.distanceFromRoot() + 1;
        }

        @Override
        public int compareTo(@NonNull Node o) {
            return Long.compare(this.numberOfDownstreams(), o.numberOfDownstreams());
        }
    }
}
