package com.example.demo.joanacore.joana;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import edu.kit.joana.wala.core.SDGBuilder;

import java.util.*;

public class CGPruner implements edu.kit.joana.wala.core.prune.CGPruner {

    final private int nodeLimit;

    public CGPruner() {
        this.nodeLimit = Integer.MAX_VALUE;
    }

    public CGPruner(int nodeLimit) {
        if (nodeLimit < 0)
            nodeLimit = 0;
        this.nodeLimit = nodeLimit;
    }

    @Override
    public Set<CGNode> prune(final SDGBuilder.SDGBuilderConfig cfg, final CallGraph cg) {
        Set<CGNode> keep = new HashSet<>();
        Set<CGNode> marked = new HashSet<>();

        // BFS
        Queue<CGNode> queue = new LinkedList<>();
        CGNode head = cg.getFakeRootNode();
        keep.add(head);
        marked.add(head);
        queue.add(head);

        int limit = nodeLimit + keep.size();
        while (!queue.isEmpty()) {
            if (keep.size() >= limit)
                break;
            head = queue.poll();
            keep.add(head);

            for (Iterator<CGNode> it = cg.getSuccNodes(head); it.hasNext(); ) {
                CGNode childNode = it.next();
                if (!marked.contains(childNode)) {
                    marked.add(childNode);
//                    if (cfg.pruningPolicy.check(childNode)) {
                        queue.add(childNode);
//                    }
                }
            }
        }

        return keep;
    }
}
