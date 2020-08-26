# ProgramSliceTool
基于Wala和Joana实现的两个程序切片工具


### Wala工具使用

#### 添加Maven依赖(项目构建)

```
<dependency>
			<groupId>com.ibm.wala</groupId>
			<artifactId>com.ibm.wala.cast</artifactId>
			<version>1.5.4</version>
		</dependency>
		<dependency>
			<groupId>com.ibm.wala</groupId>
			<artifactId>com.ibm.wala.core</artifactId>
			<version>1.5.4</version>
		</dependency>
		<dependency>
			<groupId>com.ibm.wala</groupId>
			<artifactId>com.ibm.wala.cast.java</artifactId>
			<version>1.5.4</version>
		</dependency>
		<dependency>
			<groupId>com.ibm.wala</groupId>
			<artifactId>com.ibm.wala.util</artifactId>
			<version>1.5.4</version>
		</dependency>

```

#### Wala 分析步骤

##### 第一步 构建分析域

+ ```
  if (cachedScope == null) {
  			cachedScope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(scopeFile,null);
  			cachedScope.setExclusions(new FileOfClasses(new ByteArrayInputStream(exclusionFile.getBytes("UTF-8"))));
  		}
  		if (specificExclusions != null) {
  			for (String exc : specificExclusions) {
  				cachedScope.getExclusions().add(exc);
  			}
  		}
  ```

+ 通过AnalysisScopeReader这个类读取待分析文件，在Wala分析环境建立该文件的分析域。

+ 可以配置exclusionFile选项，删减不感兴趣的相关类。

##### 第二步 构造CallGraph

+ ```
  ClassHierarchy cha = findOrCreateCHA(scope);
  		CallGraphBuilder<InstanceKey> builder;
  		CallGraph cg;
  			Iterable<Entrypoint> entrypoints = EntryPointsUtil.computeEntryPoints(fileName, funcName, desc, scope, cha);
  //			AllApplicationEntrypoints entrypoints = new AllApplicationEntrypoints(scope ,cha);
  			AnalysisOptions opts = new AnalysisOptions(scope , entrypoints);
  			builder = Util.makeZeroOneCFABuilder(Language.JAVA,opts, new AnalysisCacheImpl(), cha, scope);
  			cg = builder.makeCallGraph(opts, null);
  ```

+ 最好设置一下分析的函数入口EntryPoint，如果不想设置直接使用AllApplicationEntrypoints也可，不过分析出来的精度不敢保证，尤其是当分析域特别大的时候。

+ ```
  new Iterable<Entrypoint>() {
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
  ```

+ 本人是这样设置函数入口点EntryPoint的。

##### 第三步 查找待切语句所在函数的CGNode

+ ```
  Descriptor d = Descriptor.findOrCreateUTF8(methodSignature);
  		Atom name = Atom.findOrCreateUnicodeAtom(methodName);
  
  		for (Iterator<? extends CGNode> it = cg.getSuccNodes(cg.getFakeRootNode()); it.hasNext();) {
  			CGNode n = it.next();
  			IMethod method = n.getMethod();
  			if (method.getDeclaringClass().getClassLoader().toString().equals("Application")
  					&& method.getName().equals(name) && method.getDescriptor().equals(d)) {
  				return n;
  			}
  		}
  
  		for (CGNode n : cg) {
  			IMethod method = n.getMethod();
  			if (method.getName().equals(name) && method.getDescriptor().equals(d)) {
  				String declaringClass = method.getDeclaringClass().getName().toString();
  				if (declaringClass.equals(className)) {
  					return n; }
  			}
  		}
  ```

+ CallGraph是由一个个代表函数的CGNode组成的，我们需要找出函数所对应的CGNode。

##### 第四步找到对应的切片点

+ ```
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
  ```

+ 根据得到的CGNode，找出target statement。

##### 第五步后向程序切片

+ ```
  PointerAnalysis<InstanceKey> pA = builder.getPointerAnalysis();
  		Collection<Statement> slice = Slicer.computeBackwardSlice(findCallTo(mthd, callee, lineNumber), cg, pA, ddOpt,
  				cdOpt);
  ```

+ Wala有现成的切片API，直接调用即可。



##### 第六步剪枝

+ ```
  public static Graph<Statement> pruneSDG(SDG<?> sdg, final Collection<Statement> slice) {
          Predicate<Statement> f = new Predicate<Statement>() {
              @Override
              public boolean test(Statement o) {
                  return slice.contains(o) && !o.toString().contains("Primordial") && o.getKind() == Statement.Kind.NORMAL;
              }
          };
          return GraphSlicer.prune(sdg, f);
      }
  ```

+ 根据sdg，对slice结果进行删减。



#### Wala应用

##### Wala测试实例：

![image-20200826105200070](C:\Users\admin\AppData\Roaming\Typora\typora-user-images\image-20200826105200070.png)

+ 源自Juliet数据集中的一个例子：Juliet数据集是带有注释标签的人工漏洞测试数据集，故可以从源代码层面看出IO.writeLine第43行存在潜在的漏洞。为了保证程序切片的精确度，我们需要如下几个参数：分析域文件、漏洞代码所在函数、漏洞代码所在函数签名、漏洞代码所在代码行、漏洞代码调用的函数、漏洞所在文件名。
+ 最终Wala给出的切片结果是：37，38，39，43.


### Joana工具使用

#### Joana项目构建

+ ```
  <dependency>
  			<groupId>top.anemone.joana</groupId>
  			<artifactId>joana-core</artifactId>
  			<version>RELEASE-2020-04-22</version>
  		</dependency>
  ```

+ 除了要引入wala的相关依赖，还需要引入joana自己的jar包

#### Joana分析步骤

##### 第一步Joana配置

+ ```
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
  ```

+ 配置定制可细化成类加载机制、分析域设置、函数入口设定和剪枝四部分。

  + 类加载机制，继承ClassLoader，重写findClass方法：

    + ```
      protected Class<?> findClass(final String name)
                  throws ClassNotFoundException {
              String targetFile = name.replace(".", "/") + ".class";
              byte[] classByte = null;
              for (File file : classpaths) {
                  ZipFile zipFile = null;
                  try {
                      zipFile = new ZipFile(file);
                  } catch (IOException e) {
                      continue;
                  }
      
                  Enumeration<?> entries = zipFile.getEntries();
                  while (entries.hasMoreElements()) {
                      ZipEntry entry = (ZipEntry) entries.nextElement();
                      if (entry.getName().endsWith(".class") && entry.getName().endsWith(targetFile)) {
      //                    String entryName = entry.getName();
                          try {
                              byte[] b = new byte[(int) entry.getSize()];
                              // 将压缩文件内容写入到这个文件中
                              InputStream is = zipFile.getInputStream(entry);
                              BufferedInputStream bis = new BufferedInputStream(is);
                              bis.read(b);
                              classByte = b;
                              // 关流顺序，先打开的后关闭
                              is.close();
                          } catch (IOException e) {
                              e.printStackTrace();
                          }
                          break;
                      }
                  }
              }
              if (classByte == null) {
                  throw new ClassNotFoundException(name);
              }
              return defineClass(name, classByte, 0, classByte.length);
          }
      ```

  + 分析域设置，最小化分析域（提高分析精度和速度）

    + ```
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
      ```

  + 函数入口设定，继承SubtypesEntryPoint，重写makeParameterTypes

    + ```
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
      ```

  + 剪枝，实现CGPruner接口

    + ```
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
      ```

##### 第二步sdg程序切片

+ 构建sdg

  + ```
    String entryClass = "L" + func.getClazz().replace('.', '/');
            String entryMethod = func.getMethod();
            String entryRef = func.getSig();
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
    ```

+ 查找seed语句的node(数量不止一个)

  + ```
    HashSet<SDGNode> nodes = new HashSet<>();
            HashSet<SDGNode> successorNodes = new HashSet<>();
            int dist = 987654321;
            final BreadthFirstIterator<SDGNode, SDGEdge> it = new BreadthFirstIterator<SDGNode, SDGEdge>(sdg);
            while (it.hasNext()) {
                final SDGNode node = it.next();
                // TODO node func name==sink func
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
    ```

  + 在sdg上做后向切片

    + ```
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
      ```
