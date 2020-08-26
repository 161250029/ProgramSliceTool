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
