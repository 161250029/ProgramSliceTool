package com.example.demo.walacore.util;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.viz.NodeDecorator;

public class NodeDecoratorUtil {

    public static NodeDecorator<Statement> makeNodeDecorator() {
        return new NodeDecorator<Statement>() {
            public String getLabel(Statement s) throws WalaException {
                switch (s.getKind()) {
                    case HEAP_PARAM_CALLEE:
                    case HEAP_PARAM_CALLER:
                    case HEAP_RET_CALLEE:
                    case HEAP_RET_CALLER:
                        HeapStatement h = (HeapStatement) s;
                        return s.getKind() + "\\n" + h.getNode() + "\\n" + h.getLocation();
                    case NORMAL:
                        NormalStatement n = (NormalStatement) s;
                        SSAInstruction instruction = n.getInstruction();

                        String lineStr = "";
                        try {
                            IMethod method = n.getNode().getMethod();
                            lineStr += "in method:" + method.getName() + ", at line:"
                                    + method.getSourcePosition(n.getInstructionIndex()).getFirstLine();
                        } catch (InvalidClassFileException e) {
                            // TODO
                        }

                        lineStr += ", inst:" + instruction;
                        return lineStr;
                    case PARAM_CALLEE:
                        ParamCallee paramCallee = (ParamCallee) s;
                        return s.getKind() + " " + paramCallee.getValueNumber() + "\\n" + s.getNode().getMethod().getName();
                    case PARAM_CALLER:
                        ParamCaller paramCaller = (ParamCaller) s;
                        return s.getKind() + " " + paramCaller.getValueNumber() + "\\n" + s.getNode().getMethod().getName()
                                + "\\n" + paramCaller.getInstruction().getCallSite().getDeclaredTarget().getName();
                    case EXC_RET_CALLEE:
                    case EXC_RET_CALLER:
                    case NORMAL_RET_CALLEE:
                    case NORMAL_RET_CALLER:
                    case PHI:
                    default:
                        return s.toString();
                }
            }
        };
    }
}
