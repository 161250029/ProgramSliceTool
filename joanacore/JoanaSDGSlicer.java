package com.example.demo.joanacore;

import com.example.demo.joanacore.datastructure.Func;
import com.example.demo.joanacore.datastructure.Location;
import com.example.demo.joanacore.exception.NotFoundException;
import edu.kit.joana.ifc.sdg.graph.SDG;
import edu.kit.joana.ifc.sdg.graph.SDGEdge;
import edu.kit.joana.ifc.sdg.graph.SDGNode;
import edu.kit.joana.ifc.sdg.graph.SDGNode.Kind;
import edu.kit.joana.ifc.sdg.graph.slicer.Slicer;
import edu.kit.joana.ifc.sdg.graph.slicer.SummarySlicerBackward;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.*;

public class JoanaSDGSlicer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JoanaSDGSlicer.class);
    private SDG sdg;
    private Slicer slicer;

    public JoanaSDGSlicer(final SDG sdg) {
        this.sdg = sdg;
        this.slicer = new SummarySlicerBackward(sdg);
    }

    public HashSet<SDGNode> getNodesAtLocation(Location location, Func func) throws NotFoundException {
        HashSet<SDGNode> nodes = new HashSet<>();
        HashSet<SDGNode> successorNodes = new HashSet<>();
        int dist = 987654321;
        final BreadthFirstIterator<SDGNode, SDGEdge> it = new BreadthFirstIterator<SDGNode, SDGEdge>(sdg);
        while (it.hasNext()) {
            final SDGNode node = it.next();
            // TODO node func name==sink func
//			if (node.getSource().equals(line.filename) && node.getKind().equals(Kind.CALL)) {
//            System.out.println(node.getSource());
            if (node.getSource().equals(location.sourceFile)) {
                if (location.startLine <= node.getSr() && node.getSr() <= location.endLine) {//  && !isAbstractNode(node) && !isAbstractNode(node)) {
                    nodes.add(node);
                }
                int currDist = node.getSr() - location.endLine; // 碰到string append可能会错位，但是应该只能错一位
                if (currDist >= 0 && currDist < dist) {
                    successorNodes.clear();
                    successorNodes.add(node);
                    dist = currDist;
                } else if (currDist == dist) {
                    successorNodes.add(node);
                }
            }
        }
        if (nodes.isEmpty()) {
            LOGGER.warn("No code at line: " + location + ", alter to successor nodes");
            if (!successorNodes.isEmpty()) {
                nodes = successorNodes;
            } else {
                throw new NotFoundException(location, func);
            }
        }
        return nodes;
    }

    /**
     * 先调用 SummarySlicerBackward 做切片，再剪枝
     *
     * @param points sink点集合
     * @return
     */
    public Collection<SDGNode> slice(HashSet<SDGNode> points) {
        Collection<SDGNode> result = slicer.slice(points);
        List<SDGNode> toRemove = new ArrayList<>();
        boolean verbose = false;
        for (SDGNode n : result) {
            if (isRemoveNode(n)) {
                toRemove.add(n);
            } else if (verbose) {
                System.out.println(n.getId() + "\t" + n.getLabel() + "\t" + n.getType() + "\t" + n.getKind() + "\t"
                        + n.getOperation() + "\t" + n.getSr() + "\t" + n.getSource() + "\t" + n.getBytecodeIndex());
            }
        }
        result.removeAll(toRemove);
        return result;
    }


    /**
     * 若该节点满足一下条件任意一条，则被删去：
     * 源代码为空
     * Java Rumtime节点
     * Node.getSr()获取代码在源代码的那行，若<0则被删去
     * isExcluded()在黑名单上的类被删去（如wala的node？）
     * Exception的Label
     * node.getBytecodeIndex() 不大懂
     * node.getOperation() != SDGNode.Operation.ASSIGN Expr表达式被删除
     *
     * @param node
     * @return
     */
    public boolean isRemoveNode(final SDGNode node) {
        return node.getSource() == null || (node.getClassLoader() != null && node.getClassLoader().equals("Primordial"))
                || node.getSr() < 0 || isExcluded(node.getSource()) || node.getLabel().contains("_exception_")
                || node.getLabel().contains("fake")
                || (node.getBytecodeIndex() < -2 && node.getOperation() != SDGNode.Operation.ASSIGN) || isAbstractNode(node) ;
    }

    private boolean isExcluded(final String str) {
        return str.contains("java/lang") || str.contains("java/io") || str.contains("java/util")
                || str.contains("com/ibm/wala") || str.contains("sun/reflect") || str.contains("java/security")
                || str.contains("sun/") || str.contains("javax/servlet");
    }

    public boolean isAbstractNode(SDGNode sdgNode) {
        return sdgNode.getKind().equals(Kind.FORMAL_IN) || sdgNode.getKind().equals(Kind.FORMAL_OUT)
                || sdgNode.getKind().equals(Kind.ACTUAL_IN) || sdgNode.getKind().equals(Kind.ACTUAL_OUT)
                || sdgNode.getLabel().equals("many2many") || sdgNode.getLabel().contains("UNIQ(")
                || sdgNode.getLabel().contains("<init>") || sdgNode.getLabel().equals("immutable");
    }
}