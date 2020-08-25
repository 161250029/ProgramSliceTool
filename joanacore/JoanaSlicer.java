package com.example.demo.joanacore;


import com.example.demo.joanacore.classloader.SliceClassLoaderFactory;
import com.example.demo.joanacore.classloader.SliceJavaClassloader;
import com.example.demo.joanacore.datastructure.Func;
import com.example.demo.joanacore.datastructure.Location;
import com.example.demo.joanacore.exception.NotFoundException;
import com.example.demo.joanacore.exception.RootNodeNotFoundException;
import com.example.demo.joanacore.exception.SlicerException;
import com.ibm.wala.cfg.exc.intra.MethodState;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.pruned.ApplicationLoaderPolicy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.graph.GraphIntegrity;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;
import edu.kit.joana.ifc.sdg.graph.SDG;
import edu.kit.joana.ifc.sdg.graph.SDGNode;
import edu.kit.joana.ifc.sdg.graph.SDGSerializer;
import edu.kit.joana.ifc.sdg.mhpoptimization.CSDGPreprocessor;
import edu.kit.joana.wala.core.ExternalCallCheck;
import edu.kit.joana.wala.core.Main;
import edu.kit.joana.wala.core.SDGBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.demo.joanacore.joana.*;

import java.io.*;
import java.net.URL;
import java.util.*;

import static com.example.demo.joanacore.joana.LoggingOutputStream.LogLevel.INFO;


public class JoanaSlicer implements Slicer {
    private static final Logger LOGGER = LoggerFactory.getLogger(JoanaSlicer.class);
    private SDGBuilder.SDGBuilderConfig config;
    private Map<Func, SDG> sdgCache = new HashMap<>();

    public void clearCache() {
        sdgCache = new HashMap<>();
        config.cache = new AnalysisCacheImpl();
    }

    public List<Integer> computeSlice(Func func, Location line) throws SlicerException {
        List<Integer> slices = new ArrayList<>();
        SDG sdg = null;
        if (sdgCache.containsKey(func)) {
            sdg = sdgCache.get(func);
        } else {
            try {
                sdg = this.buildSDG(func);
                LOGGER.info("Computing Slice...");
            } catch (IOException | GraphIntegrity.UnsoundGraphException | CancelException | NotFoundException | ClassCastException e) {
                throw new SlicerException(e.getMessage(), e);
            }
        }
        // 利用SDG切片
        JoanaSDGSlicer jSlicer = new JoanaSDGSlicer(sdg);
        LOGGER.info("Done...");
        // 根据sink点查找sinknodes
        HashSet<SDGNode> sinkNodes;
        try {
            sinkNodes = jSlicer.getNodesAtLocation(line, func);
        } catch (NotFoundException e) {
            throw new SlicerException(e.getMessage(), e);
        }
        Collection<SDGNode> slice = jSlicer.slice(sinkNodes);
//        List<SDGNode> sortedSlice=new DFSSort(slice, config.cgPruner).getSortedNodes(func.getClazz()+"."+func.getMethod()+func.getSig());
        String result = Formatter.prepareSliceForEncoding(slice);
        for (SDGNode node : slice)
            slices.add(node.getSr());
        return slices;
    }

    public SDG buildSDG(Func func)
            throws IOException, GraphIntegrity.UnsoundGraphException, CancelException, NotFoundException {
        String entryClass = "L" + func.getClazz().replace('.', '/');
        String entryMethod = func.getMethod();
        String entryRef = func.getSig();
        SDG sdg = buildSDG(entryClass, entryMethod, entryRef, null);
        sdgCache.put(func, sdg);
        return sdg;
    }

    public SDG buildSDG(Func func, String pdgFile)
            throws IOException, GraphIntegrity.UnsoundGraphException, CancelException, NotFoundException {
        String entryClass = "L" + func.getClazz().replace('.', '/');
        String entryMethod = func.getMethod();
        String entryRef = func.getSig();
        SDG sdg = buildSDG(entryClass, entryMethod, entryRef, pdgFile);
        sdgCache.put(func, sdg);
        return sdg;
    }

    public SDG buildSDG(String entryClass, String entryMethod, String entryRef, String pdgFile)
            throws IOException, GraphIntegrity.UnsoundGraphException, CancelException, NotFoundException {
        SDG localSdg = null;
        LOGGER.info("Building SDG... ");
        // 根据class, method, ref在classloader中找入口函数
        config.entry = findMethod(this.config, entryClass, entryMethod, entryRef);
        // 构造SDG
        try {
            localSdg = SDGBuilder.build(this.config, new SliceMonitor());
        } catch (NoSuchElementException e) {
            StackTraceElement stackTraceElement = e.getStackTrace()[2];
            if (stackTraceElement.getClassName().equals("edu.kit.joana.wala.core.CallGraph")
                    && stackTraceElement.getMethodName().equals("<init>")) {
                throw new RootNodeNotFoundException(entryClass + "." + entryMethod + entryRef, "call-graph (or it was in primordial jar)");
            }
        }
        LOGGER.info("SDG build done! Optimizing...");
        // 剪枝
        CSDGPreprocessor.preprocessSDG(localSdg);
        // 保存SDG
        if (pdgFile != null) {
            LOGGER.info("Done! Saving to: " + pdgFile);
            SDGSerializer.toPDGFormat(localSdg, new PrintWriter(pdgFile));
        } else {
            LOGGER.info("Done!");
        }
        return localSdg;
    }

    private static AnalysisScope makeMinimalScope(List<File> appJars, List<URL> libJars,
                                                  List<String> exclusions, ClassLoader classLoader) throws IOException {

        String scopeFile = "src\\main\\java\\com\\example\\demo\\joanacore\\RtScopeFile.txt";
        String exclusionFile = "src\\main\\java\\com\\example\\demo\\joanacore\\Java60RegressionExclusions.txt";
        final AnalysisScope scope = AnalysisScopeReader.readJavaScope(
                scopeFile, (new FileProvider()).getFile(exclusionFile), classLoader);
        for (File appJar : appJars) {
            scope.addToScope(ClassLoaderReference.Application, new JarStreamModule(new FileInputStream(appJar)));
        }
        if (libJars != null) {
            for (URL lib : libJars) {
                if (appJars.contains(new File(lib.getFile()))) {
                    LOGGER.warn(lib + "in app scope.");
                    continue;
                }
                if (lib.getProtocol().equals("file")) {
                    scope.addToScope(ClassLoaderReference.Primordial, new JarStreamModule(new FileInputStream(lib.getFile())));
                } else {
                    scope.addToScope(ClassLoaderReference.Primordial, new JarStreamModule(JoanaSlicer.class.getResourceAsStream(String.valueOf(lib))));
                }
            }
        }

        if (exclusions != null) {
            for (String exc : exclusions) {
                scope.getExclusions().add(exc);
            }
        }
        return scope;
    }

    public void config(List<File> appJars, List<URL> libJars, List<String> exclusions)
            throws ClassHierarchyException, IOException {
        SDGBuilder.SDGBuilderConfig scfg = new SDGBuilder.SDGBuilderConfig();
        scfg.out = new PrintStream(new LoggingOutputStream(LOGGER, INFO));
        scfg.nativeSpecClassLoader = new SliceJavaClassloader(new File[]{});
        scfg.scope = makeMinimalScope(appJars, libJars, exclusions, scfg.nativeSpecClassLoader);
        scfg.cache = new AnalysisCacheImpl();
        scfg.cha = ClassHierarchyFactory.makeWithRoot(scfg.scope, new SliceClassLoaderFactory(scfg.scope.getExclusions()));
        scfg.ext = ExternalCallCheck.EMPTY;
        scfg.immutableNoOut = Main.IMMUTABLE_NO_OUT;
        scfg.immutableStubs = Main.IMMUTABLE_STUBS;
        scfg.ignoreStaticFields = Main.IGNORE_STATIC_FIELDS;
        scfg.exceptions = SDGBuilder.ExceptionAnalysis.IGNORE_ALL;
        scfg.pruneDDEdgesToDanglingExceptionNodes = true;
        scfg.defaultExceptionMethodState = MethodState.DEFAULT;
        scfg.accessPath = false;
        scfg.sideEffects = null;
        scfg.prunecg = 0;
        scfg.pruningPolicy = ApplicationLoaderPolicy.INSTANCE;
        scfg.pts = SDGBuilder.PointsToPrecision.INSTANCE_BASED;
        scfg.customCGBFactory = null;
        scfg.staticInitializers = SDGBuilder.StaticInitializationTreatment.SIMPLE;
        scfg.fieldPropagation = SDGBuilder.FieldPropagation.OBJ_GRAPH;
        scfg.computeInterference = false;
        scfg.computeAllocationSites = false;
        scfg.computeSummary = false;
        scfg.cgConsumer = null;
        scfg.additionalContextSelector = null;
        scfg.dynDisp = SDGBuilder.DynamicDispatchHandling.IGNORE;
        scfg.debugCallGraphDotOutput = false;
        scfg.debugManyGraphsDotOutput = false;
        scfg.debugAccessPath = false;
        scfg.debugStaticInitializers = false;
        scfg.entrypointFactory = new SliceEntrypointFactory();
        scfg.cgPruner = new SliceCGPruner(50);
        scfg.doParallel = false;
        this.config = scfg;
    }

    static IMethod findMethod(SDGBuilder.SDGBuilderConfig scfg, final String entryClazz, final String entryMethod, String methodRef) throws NotFoundException {
        // debugPrint(scfg.cha);
        final IClass cl = scfg.cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Application, entryClazz));
        if (cl == null) {
            throw new NotFoundException(entryClazz, scfg.cha);
        }
        // final IMethod m = cl.getMethod(Selector.make(entryMethod));
        final IMethod m = cl.getMethod(new Selector(Atom.findOrCreateUnicodeAtom(entryMethod),
                Descriptor.findOrCreateUTF8(Language.JAVA, methodRef)));
        if (m == null) {
            throw new NotFoundException(cl + "." + entryMethod, cl);
        }
        return m;
    }

    public static void main(String[] args) throws ClassHierarchyException, IOException, SlicerException {
        List<File> apps = new ArrayList<>();
        apps.add(new File("D:\\ChromeCoreDownloads\\Benchmark-master\\Benchmark-master\\target\\benchmark.war"));
        JoanaSlicer slicer = new JoanaSlicer();
        slicer.config(apps,null , null);
        System.out.println(slicer.computeSlice(new Func("org.owasp.benchmark.testcode.BenchmarkTest01602" , "doPost" , "(Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)V") ,
                new Location("org/owasp/benchmark/testcode/BenchmarkTest01602.java" ,51 , 51)));
//        System.out.println(slicer.computeSlice(new Func("org.owasp.benchmark.testcode.BenchmarkTest00883" , "doPost" , "(Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)V") ,
//                new Location("org/owasp/benchmark/testcode/BenchmarkTest00883.java" , 57 , 57)));
    }
}
