package com.example.demo.walacore;

import com.example.demo.Resources.JarLocationInfo;
import com.example.demo.walacore.util.EntryPointsUtil;
import com.example.demo.walacore.util.NodeDecoratorUtil;
import com.example.demo.walacore.util.PruneUtil;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.config.FileOfClasses;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.viz.NodeDecorator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

public class WALASlicer {
	private AnalysisScope cachedScope;
	private IClassHierarchy cachedCHA;
	private DataDependenceOptions ddOpt = DataDependenceOptions.NO_BASE_NO_HEAP_NO_EXCEPTIONS;
	private ControlDependenceOptions cdOpt = ControlDependenceOptions.FULL;
	private final String exclusionFile = "apple\\/.*\n" +
			"com\\/apple\\/.*\n" +
			"com\\/ibm\\/.*\n" +
			"com\\/oracle\\/.*\n" +
			"com\\/sun\\/.*\n" +
			"dalvik\\/.*\n" +
			"java\\/beans\\/.*\n" +
			"java\\/io\\/ObjectStreamClass*\n" +
			"java\\/rmi\\/.*\n" +
			"java\\/text\\/.*\n" +
			"java\\/time\\/.*\n" +
			"javafx\\/.*\n" +
			"javafx\\/beans\\/.*\n" +
			"javafx\\/collections\\/.*\n" +
			"javafx\\/scene\\/.*\n" +
			"javax\\/accessibility\\/.*\n" +
			"javax\\/activation\\/.*\n" +
			"javax\\/activity\\/.*\n" +
			"javax\\/annotation\\/.*\n" +
			"javax\\/crypto\\/.*\n" +
			"javax\\/imageio\\/.*\n" +
			"javax\\/jnlp\\/.*\n" +
			"javax\\/jws\\/.*\n" +
			"javax\\/management\\/.*\n" +
			"javax\\/net\\/.*\n" +
			"javax\\/print\\/.*\n" +
			"javax\\/rmi\\/.*\n" +
			"javax\\/script\\/.*\n" +
			"javax\\/smartcardio\\/.*\n" +
			"javax\\/sound\\/.*\n" +
			"javax\\/sql\\/.*\n" +
			"javax\\/tools\\/.*\n" +
			"jdk\\/.*\n" +
			"netscape\\/.*\n" +
			"oracle\\/jrockit\\/.*\n" +
			"org\\/apache\\/xerces\\/.*\n" +
			"org\\/ietf\\/.*\n" +
			"org\\/jcp\\/.*\n" +
			"org\\/netbeans\\/.*\n" +
			"org\\/omg\\/.*\n" +
			"org\\/openide\\/.*\n" +
			"sun\\/.*\n" +
			"sun\\/awt\\/.*\n" +
			"sun\\/swing\\/.*\n" +
			"java\\/net\\/.*\n" +
			"java\\/util\\/.*\n" +
			"java\\/security\\/.*\n" +
			"java\\/math\\/.*\n" +
			"java\\/nio\\/.*\n" +
			"java\\/io\\/.*\n" +
			"java\\/awt\\/.*\n" +
			"org\\/owasp\\/benchmark\\/score\\/.*\n" +
			"org\\/owasp\\/benchmark\\/score\\/parsers\\/.*\n" +
			"org\\/owasp\\/benchmark\\/score\\/report\\/.*\n" +
			"org\\/owasp\\/benchmark\\/score\\/report\\/.*\n" +
			"org\\/owasp\\/benchmark\\/helpers\\/DatabaseHelp.*\n" +
			".*\\/DatabaseHelp.*";
	private String scopeFile;
	private List<String> specificExclusions;
	private boolean debug = false;

	public WALASlicer(String scopeFile, List<String> exclusion) {
		this.scopeFile = scopeFile;
		this.specificExclusions = exclusion;
	}


	public Statement findCallTo(CGNode n, String methodName, int lineNumber) throws InvalidClassFileException {
		IR ir = n.getIR();
		ArrayList<NormalStatement> callStmts = new ArrayList<NormalStatement>();
		for (Iterator<SSAInstruction> it = ir.iterateAllInstructions(); it.hasNext();) {
			SSAInstruction s = it.next();
			if (s instanceof SSAInvokeInstruction) {
				CallSiteReference callSite = ((SSAInvokeInstruction) s).getCallSite();
				System.out.println(callSite.getDeclaredTarget().getName());
				if (callSite.getDeclaredTarget().getName().toString().equals(methodName)) {
					IntSet indices = ir.getCallInstructionIndices(callSite);
					Assertions.productionAssertion(indices.size() == 1, "expected 1 but got " + indices.size());
					callStmts.add(new NormalStatement(n, indices.intIterator().next()));
				}
			}
		}
		int resultIndx = -1;
		int distance = 999;
		for (int i = 0; i < callStmts.size(); i++) {
			int firstLine = callStmts.get(i).getNode().getMethod()
					.getSourcePosition(callStmts.get(i).getInstructionIndex()).getFirstLine();
			int dist = Math.abs(firstLine - lineNumber);
			if (distance > dist) {
				distance = dist;
				resultIndx = i;
			}
		}
		if (resultIndx == -1) {
			Assertions.UNREACHABLE("failed to find call to " + methodName + " in " + n);
		}
		return callStmts.get(resultIndx);
	}

	private CGNode findMethod(CallGraph cg, String methodSignature, String methodName, String className)
			throws InvalidClassFileException {
		Descriptor d = Descriptor.findOrCreateUTF8(methodSignature);
		Atom name = Atom.findOrCreateUnicodeAtom(methodName);

		for (Iterator<? extends CGNode> it = cg.getSuccNodes(cg.getFakeRootNode()); it.hasNext();) {
			CGNode n = it.next();
			IMethod method = n.getMethod();
//			System.out.println(method.getDeclaringClass().getName().toString());
//			System.out.println(method.getName());
//			System.out.println(method.getDescriptor());
			if (method.getDeclaringClass().getClassLoader().toString().equals("Application")
					&& method.getName().equals(name) && method.getDescriptor().equals(d)) {
				return n;
			}
		}

		for (CGNode n : cg) {
			IMethod method = n.getMethod();
			if (method.getName().equals(name) && method.getDescriptor().equals(d)) {
				String declaringClass = method.getDeclaringClass().getName().toString();
//				if (method.getDescriptor().equals(d))
//					System.out.println(d);
				if (declaringClass.equals(className)) {
//					System.out.println(declaringClass);
					return n; }
			}
		}

		return null;
	}

	private AnalysisScope findOrCreateAnalysisScope() throws IOException {
		if (cachedScope == null) {
			cachedScope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(scopeFile,null);
			cachedScope.setExclusions(new FileOfClasses(new ByteArrayInputStream(exclusionFile.getBytes("UTF-8"))));
		}
		if (specificExclusions != null) {
			for (String exc : specificExclusions) {
				cachedScope.getExclusions().add(exc);
			}
		}
		return cachedScope;
	}

	private ClassHierarchy findOrCreateCHA(AnalysisScope scope) throws ClassHierarchyException {
		if (cachedCHA == null) {
			cachedCHA = ClassHierarchyFactory.make(scope);
		}
		return (ClassHierarchy) cachedCHA;
	}

	public List<Integer> sliceJuliet(String fileName, String funcName, String desc, String callee, int lineNumber)
			throws IOException, IllegalArgumentException, CancelException, WalaException, InvalidClassFileException {
		AnalysisScope scope = findOrCreateAnalysisScope();
		ClassHierarchy cha = findOrCreateCHA(scope);
		CallGraphBuilder<InstanceKey> builder;
		CallGraph cg;
		try {

			Iterable<Entrypoint> entrypoints = EntryPointsUtil.computeEntryPoints(fileName, funcName, desc, scope, cha);
//			AllApplicationEntrypoints entrypoints = new AllApplicationEntrypoints(scope ,cha);
			AnalysisOptions opts = new AnalysisOptions(scope , entrypoints);
			builder = Util.makeZeroOneCFABuilder(Language.JAVA,opts, new AnalysisCacheImpl(), cha, scope);
			cg = builder.makeCallGraph(opts, null);
		} catch (com.ibm.wala.util.debug.UnimplementedError e) {
			Iterable<Entrypoint> entrypoints = EntryPointsUtil.computeEntryPoints(fileName, funcName,
					"(I)V", scope, cha);
			AnalysisOptions opts = new AnalysisOptions(scope, entrypoints);
			builder = Util.makeZeroOneCFABuilder(Language.JAVA,opts, new AnalysisCacheImpl(), cha, scope);
			cg = builder.makeCallGraph(opts, null);
		}
		CGNode mthd = findMethod(cg, desc, funcName, fileName);
        System.out.println(mthd);
		PointerAnalysis<InstanceKey> pA = builder.getPointerAnalysis();
		Collection<Statement> slice = Slicer.computeBackwardSlice(findCallTo(mthd, callee, lineNumber), cg, pA, ddOpt,
				cdOpt);
		Graph<Statement> graph = PruneUtil.pruneSDG(new SDG<InstanceKey>(cg, pA, ddOpt, cdOpt),slice);
		Set<Integer> result = new TreeSet<>();
		for (Statement s : graph) {
			result.add(getStatementLineNumber(s));
		}
		result.add(lineNumber);
		return new ArrayList<>(result);

	}

	public static int getStatementLineNumber(Statement statement) throws InvalidClassFileException {
		NormalStatement n = (NormalStatement) statement;
		IMethod method = n.getNode().getMethod();
		return method.getSourcePosition(n.getInstructionIndex()).getFirstLine();
	}

	public String computeCFG(String fileName, String funcName, String desc, String sink, int lineNumber)
			throws IOException, IllegalArgumentException, CancelException, WalaException, InvalidClassFileException {
		AnalysisScope scope = findOrCreateAnalysisScope();
		ClassHierarchy cha = findOrCreateCHA(scope);
		Iterable<Entrypoint> entrypoints = EntryPointsUtil.computeEntryPoints(fileName, funcName, desc, scope, cha);
		AnalysisOptions opts = new AnalysisOptions(scope, entrypoints);
		AnalysisCache cache = new AnalysisCacheImpl();
		debugPrint(cha, cache, opts);
		IMethod iMethod = findIMethod(cha, cache, opts, fileName, funcName, desc);
		IR ir = cache.getSSACache().findOrCreateIR(iMethod, Everywhere.EVERYWHERE, opts.getSSAOptions());
		if (ir == null) {
			PropagationCallGraphBuilder builder = Util.makeZeroOneCFABuilder(Language.JAVA,opts, cache, cha, scope);
			CGNode mthd = findMethod(builder.makeCallGraph(opts), desc, funcName, fileName);
			iMethod = mthd.getMethod();
			ir = mthd.getIR();
		}
		String targets = "[";
		Iterator<SSAInstruction> iterateNormalInstructions = ir.iterateNormalInstructions();
		while (iterateNormalInstructions.hasNext()) {
			SSAInstruction ssaIns = (SSAInstruction) iterateNormalInstructions.next();
			System.out.println(ssaIns);
			int firstLine = iMethod.getSourcePosition(ssaIns.iIndex()).getFirstLine();
			System.out.println(firstLine);
			if (ssaIns.toString().contains(sink) && firstLine == lineNumber) {
				BasicBlock blockForInstruction = ir.getControlFlowGraph().getBlockForInstruction(ssaIns.iIndex());
				targets += blockForInstruction.getNumber() + ", ";
			}
		}
		targets = targets.substring(0, targets.lastIndexOf(",")) + "]\n";
		return targets;
	}

	public String sliceOwasp(String fileName, String funcName, String desc, String callee, int lineNumber)
			throws IOException, IllegalArgumentException, CancelException, WalaException, InvalidClassFileException {
		AnalysisScope scope = findOrCreateAnalysisScope();
		ClassHierarchy cha = findOrCreateCHA(scope);
		AnalysisCache cache = new AnalysisCacheImpl();
		Iterable<Entrypoint> entrypoints = EntryPointsUtil.computeEntryPoints(fileName, funcName, desc, scope, cha);
		AnalysisOptions opts = new AnalysisOptions(scope, entrypoints);
		debugPrint(cha, cache, opts);
		CallGraphBuilder<InstanceKey> builder = Util.makeZeroOneCFABuilder(Language.JAVA ,opts, cache, cha, scope);
		CallGraph cg = builder.makeCallGraph(opts, null);
		CGNode mthd = findMethod(cg, desc, funcName, fileName);

		PointerAnalysis<InstanceKey> pA = builder.getPointerAnalysis();
		Collection<Statement> slice = Slicer.computeBackwardSlice(findCallTo(mthd, callee, lineNumber), cg, pA, ddOpt,
				cdOpt);
		return printSDG(cg, pA, slice);
	}

	public String slice(String fileName, String funcName, String desc, String callee, int lineNumber)
			throws IOException, IllegalArgumentException, CancelException, WalaException, InvalidClassFileException {
		System.out.println("[WarningSlicer=>slice=>fileName:" + fileName + ",funcName:" + funcName + ",desc:" + desc
				+ ",callee:" + callee + ",lineNumber:" + lineNumber + "]");
		AnalysisScope scope = findOrCreateAnalysisScope();
		ClassHierarchy cha = findOrCreateCHA(scope);
		//debugPrint(cha);
		Iterable<Entrypoint> entrypoints = EntryPointsUtil.computeEntryPoints(fileName, funcName, desc, scope, cha);
		AnalysisOptions opts = new AnalysisOptions(scope, entrypoints);
		CallGraphBuilder<InstanceKey> builder = Util.makeZeroOneCFABuilder(Language.JAVA,opts, new AnalysisCacheImpl(), cha, scope);
		CallGraph cg = builder.makeCallGraph(opts, null);

		CGNode mthd = findMethod(cg, desc, funcName, fileName);

		PointerAnalysis<InstanceKey> pA = builder.getPointerAnalysis();
		Collection<Statement> slice = Slicer.computeBackwardSlice(findCallTo(mthd, callee, lineNumber), cg, pA, ddOpt,
				cdOpt);
		return printSDG(cg, pA, slice);
	}

	public String dumpSlice(Collection<Statement> slice, String delimiter) {
		String rslt = "";
		int i = 1;
		for (Statement s : slice)
			rslt += (i++) + " " + s + delimiter;
		return rslt.trim();
	}

	public IMethod findIMethod(ClassHierarchy cha, AnalysisCache cache, AnalysisOptions opts, String clazz,
			String method, String desc) {
		for (Iterator<IClass> it = cha.iterator(); it.hasNext();) {
			ShrikeClass next = (ShrikeClass) it.next();
			if (!next.getClassLoader().getName().toString().equals("Primordial")) {
				if (next.getReference().getName().toString().equals(clazz)) {
					for (IMethod iMethod : next.getAllMethods()) {
						if (iMethod.getName().toString().contains(method)
								&& iMethod.getDescriptor().toString().contains(desc)) {
							return iMethod;
						}
					}
				}
			}
		}
		return null;
	}

	private void debugPrint(ClassHierarchy cha, AnalysisCache cache, AnalysisOptions opts) {
		if (debug) { // debug
			for (Iterator<IClass> it = cha.iterator(); it.hasNext();) {
				ShrikeClass next = (ShrikeClass) it.next();
				if (!next.getClassLoader().getName().toString().equals("Primordial")) {
					Collection<IMethod> allMethods = next.getAllMethods();
					for (IMethod iMethod : allMethods) {
						System.out.println(" " + iMethod);
						for (IField iField : next.getAllFields()) {
							if (!iField.toString().contains("Primordial")) {
								System.out.println("\t" + iField.getName());
							}
						}
					}
				}
			}
		}
	}


	public Graph<Statement> pruneSDG(SDG<?> sdg, final Collection<Statement> slice) {
		Predicate<Statement> f = new Predicate<Statement>() {
			@Override
			public boolean test(Statement o) {
				return slice.contains(o) && !o.toString().contains("Primordial") && o.getKind() == Statement.Kind.NORMAL;
			}
		};
		return GraphSlicer.prune(sdg, f);
	}

	public String printSDG(CallGraph cg, PointerAnalysis<InstanceKey> pointerAnalysis, Collection<Statement> slice)
			throws WalaException {
		Graph<Statement> g = pruneSDG(new SDG<InstanceKey>(cg, pointerAnalysis, ddOpt, cdOpt), slice);
		NodeDecorator<Statement> decorator = NodeDecoratorUtil.makeNodeDecorator();
		Iterator<Statement> iter = g.iterator();
		String sdgStr = "";
		while (iter.hasNext()) {
			Statement stmt = iter.next();
			sdgStr += decorator.getLabel(stmt) + "\n";
		}
		return sdgStr;
	}

	public void debugPrintCallMethod(CGNode n) throws InvalidClassFileException {
		IR ir = n.getIR();
		ArrayList<NormalStatement> callStmts = new ArrayList<NormalStatement>();
		for (Iterator<SSAInstruction> it = ir.iterateAllInstructions(); it.hasNext();) {
			SSAInstruction s = it.next();
			if (s instanceof SSAInvokeInstruction) {
				CallSiteReference callSite = ((SSAInvokeInstruction) s).getCallSite();
				System.out.print(callSite.getDeclaredTarget().getName() + " ");
			}
		}
		return;
	}

	public static void main(String[] args) throws IOException, WalaException, CancelException, InvalidClassFileException {
//        findAllMethod("src\\main\\java\\CWE15_External_Control_of_System_or_Configuration_Setting.jar" , "CWE15_External_Control_of_System_or_Configuration_Setting__connect_tcp_02");

//        System.out.println(doSlicing("src\\main\\java\\CWE15_External_Control_of_System_or_Configuration_Setting.jar" , "goodG2B1" , "CWE15_External_Control_of_System_or_Configuration_Setting__connect_tcp_02" ,166));

//        System.out.println(doSlicing(JarLocationInfo.Jar_Directory_prefix + "\\CWE369_Divide_by_Zero_s04.jar" , "badSink" , "testcases/CWE369_Divide_by_Zero/s04/CWE369_Divide_by_Zero__int_zero_divide_75b" , 39));
//        System.out.println(doSlicing("C:\\Users\\admin\\Desktop\\Main.jar" , "sink" , "LMain" , 13));
//        System.out.println(doSlicing("C:\\Users\\admin\\Desktop\\Main.jar" , "main" , "LMain" , 7));  forward

		WALASlicer walaSlicer = new WALASlicer(JarLocationInfo.Jar_Directory_prefix + "\\CWE369_Divide_by_Zero_s04.jar" , null);
		System.out.println(walaSlicer.sliceJuliet("Ltestcases/CWE369_Divide_by_Zero/s04/CWE369_Divide_by_Zero__int_zero_divide_75b" , "badSink" , "([B)V" , "readObject",39));
		System.out.println(walaSlicer.sliceJuliet("Ltestcases/CWE369_Divide_by_Zero/s04/CWE369_Divide_by_Zero__int_zero_divide_75b" , "badSink" , "([B)V" , "writeLine",43));

//          System.out.println(doSlicing("D:\\upload\\CWE259_Hard_Coded_Password\\CWE259_Hard_Coded_Password.jar" , "bad" , "testcases/CWE259_Hard_Coded_Password/CWE259_Hard_Coded_Password__driverManager_05" , 59));

	}
}