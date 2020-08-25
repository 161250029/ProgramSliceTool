package com.example.demo.joanacore.joana;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.impl.AbstractRootMethod;
import com.ibm.wala.ipa.callgraph.impl.SubtypesEntrypoint;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.analysis.typeInference.ConeType;
import com.ibm.wala.analysis.typeInference.PrimitiveType;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.util.collections.HashSetFactory;

import java.util.Collection;
import java.util.Set;

public class SliceEntrypoint extends SubtypesEntrypoint {
    public SliceEntrypoint(MethodReference method, IClassHierarchy cha) {
        super(method, cha);
    }

    public SliceEntrypoint(IMethod method, IClassHierarchy cha) {
        super(method, cha);
    }

    protected int makeArgument(AbstractRootMethod m, int i) {
        TypeReference[] p = getParameterTypes(i);
        switch (p.length) {
            case 0:
                // FIXME @Anemone, may cause problem
                NewSiteReference nonceRef=NewSiteReference.make(m.getStatements().length,TypeReference.JavaLangClass);
                return m.addPhi(new int[]{nonceRef.getProgramCounter()});
            case 1:
                if (p[0].isPrimitiveType()) {
                    return m.addLocal();
                } else {
                    SSANewInstruction n = m.addAllocation(p[0]);
                    NewSiteReference ref=NewSiteReference.make(m.getStatements().length,p[0]);
                    return (n == null) ? m.addPhi(new int[]{ref.getProgramCounter()}) : n.getDef();
                }
            default:
                int[] values = new int[p.length];
                int countErrors = 0;
                for (int j = 0; j < p.length; j++) {
                    SSANewInstruction n = m.addAllocation(p[j]);
                    int value = (n == null) ? -1 : n.getDef();
                    if (value == -1) {
                        countErrors++;
                    } else {
                        values[j - countErrors] = value;
                    }
                }
                if (countErrors > 0) {
                    int[] oldValues = values;
                    values = new int[oldValues.length - countErrors];
                    System.arraycopy(oldValues, 0, values, 0, values.length);
                }

                TypeAbstraction a;
                if (p[0].isPrimitiveType()) {
                    a = PrimitiveType.getPrimitive(p[0]);
                    for (i = 1; i < p.length; i++) {
                        a = a.meet(PrimitiveType.getPrimitive(p[i]));
                    }
                } else {
                    IClassHierarchy cha = m.getClassHierarchy();
                    IClass p0 = cha.lookupClass(p[0]);
                    a = new ConeType(p0);
                    for (i = 1; i < p.length; i++) {
                        IClass pi = cha.lookupClass(p[i]);
                        a = a.meet(new ConeType(pi));
                    }
                }

                return m.addPhi(values);
        }
    }

    @Override
    protected TypeReference[] makeParameterTypes(IMethod method, int i) {
        TypeReference nominal = method.getParameterType(i);
        if (nominal.isPrimitiveType() || nominal.isArrayType())
            return new TypeReference[] { nominal };
        else {
            IClass nc = getCha().lookupClass(nominal);
            if (nc == null) {
                return new TypeReference[] { nominal };
            }
            // 否则返回非抽象非子类的集合
            Collection<IClass> subcs = nc.isInterface() ? getCha().getImplementors(nominal) : getCha().computeSubClasses(nominal);
            Set<TypeReference> subs = HashSetFactory.make();
            for (IClass cs : subcs) {
                if (!cs.isAbstract() && !cs.isInterface()) {
                    subs.add(cs.getReference());
                }
            }
            return subs.toArray(new TypeReference[subs.size()]);
        }
    }
}
