package com.example.demo.joanacore.joana;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import edu.kit.joana.wala.core.SDGBuilder;
import edu.kit.joana.wala.core.prune.CGPruner;

import java.util.*;

public class SliceCGPruner implements CGPruner {

    final private int nodeLimit;

    public SliceCGPruner() {
        this.nodeLimit = Integer.MAX_VALUE;
    }

    public SliceCGPruner(int nodeLimit) {
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
        CGNode rootNode=head;

        marked.addAll(cg.getEntrypointNodes());
        keep.addAll(cg.getEntrypointNodes());
        queue.addAll(cg.getEntrypointNodes());

        int limit = nodeLimit + keep.size();
        boolean rootNodeAdded=false;
        while (!queue.isEmpty()) {
            if (keep.size() >= limit)
                break;
            head = queue.poll();
            keep.add(head);

            for (Iterator<CGNode> it = cg.getSuccNodes(head); it.hasNext(); ) {
                CGNode childNode = it.next();
                if (!marked.contains(childNode)) {
                    marked.add(childNode);
                    if (cfg.pruningPolicy.check(childNode)) {
                        queue.add(childNode);
                    }
                }
            }
            if (!rootNodeAdded){
                rootNodeAdded=true;
                queue.add(rootNode);
            }
        }

        return keep;
    }
}
