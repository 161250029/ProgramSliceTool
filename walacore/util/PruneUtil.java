package com.example.demo.walacore.util;

import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;

import java.util.Collection;
import java.util.function.Predicate;

public class PruneUtil {

    public static Graph<Statement> pruneSDG(SDG<?> sdg, final Collection<Statement> slice) {
        Predicate<Statement> f = new Predicate<Statement>() {
            @Override
            public boolean test(Statement o) {
                return slice.contains(o) && !o.toString().contains("Primordial") && o.getKind() == Statement.Kind.NORMAL;
            }
        };
        return GraphSlicer.prune(sdg, f);
    }
}
