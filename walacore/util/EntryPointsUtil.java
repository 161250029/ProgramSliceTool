package com.example.demo.walacore.util;

import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;

import java.util.Iterator;

public class EntryPointsUtil {
    public static Iterable<Entrypoint> computeEntryPoints(final String fileName, final String funcName, final String desc,
                                                    final AnalysisScope scope, final ClassHierarchy cha) {
        return new Iterable<Entrypoint>() {
            public Iterator<Entrypoint> iterator() {
                final Atom mainMethod = Atom.findOrCreateAsciiAtom(funcName);
                return new Iterator<Entrypoint>() {
                    private int index = 0;

                    public boolean hasNext() {
                        return index < 1;
                    }

                    public Entrypoint next() {
                        index++;
                        TypeReference T = TypeReference.findOrCreate(scope.getApplicationLoader(),
                                TypeName.string2TypeName(fileName));
                        // fileName.endsWith("b")
                        // ? fileName.substring(0, fileName.length() - 1) + "a"
                        // :
                        MethodReference mainRef = MethodReference.findOrCreate(T, mainMethod,
                                Descriptor.findOrCreateUTF8(desc));

                        return new DefaultEntrypoint(mainRef, cha);
                    }
                };
            }
        };
    }
}
