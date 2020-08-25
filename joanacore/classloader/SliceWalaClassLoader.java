package com.example.demo.joanacore.classloader;

import com.ibm.wala.classLoader.*;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.ClassReader;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.config.SetOfClasses;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.shrike.ShrikeClassReaderHandle;
import com.ibm.wala.util.warnings.Warning;
import com.ibm.wala.util.warnings.Warnings;

import java.io.*;
import java.util.*;
import java.util.jar.JarFile;

public class SliceWalaClassLoader extends ClassLoaderImpl {

    private static final boolean OPTIMIZE_JAR_FILE_IO = true;
    /**
     * @param loader           class loader reference identifying this loader
     * @param arrayClassLoader
     * @param parent           parent loader for delegation
     * @param exclusions       set of classes to exclude from loading
     * @param cha
     */
    private SetOfClasses myExclusions;
    public SliceWalaClassLoader(ClassLoaderReference loader, ArrayClassLoader arrayClassLoader, IClassLoader parent, SetOfClasses exclusions, IClassHierarchy cha) {
        super(loader, arrayClassLoader, parent, exclusions, cha);
        this.myExclusions=exclusions;
    }

    @Override
    public void init(List<Module> modules) throws IOException {

        if (modules == null) {
            throw new IllegalArgumentException("modules is null");
        }

        // module are loaded according to the given order (same as in Java VM)
        Set<ModuleEntry> classModuleEntries = HashSetFactory.make();
        Set<ModuleEntry> sourceModuleEntries = HashSetFactory.make();
        for (Module archive : modules) {
            boolean isJMODType = false;
            if (archive instanceof JarFileModule) {
                JarFile jarFile = ((JarFileModule) archive).getJarFile();
                isJMODType = (jarFile != null) && jarFile.getName().endsWith(".jmod");
            }
            if (DEBUG_LEVEL > 0) {
                System.err.println("add archive: " + archive);
            }
            // byte[] jarFileContents = null;
            if (OPTIMIZE_JAR_FILE_IO && archive instanceof JarFileModule) {
                // if we have a jar file, we read the whole thing into memory and operate on that; enables
                // more
                // efficient sequential I/O
                // this is work in progress; for now, we read the file into memory and throw away the
                // contents, which
                // still gives a speedup for large jar files since it reads sequentially and warms up the FS
                // cache. we get a small slowdown
                // for smaller jar files or for jar files already in the FS cache. eventually, we should
                // actually use the bytes read and eliminate the slowdown
                // 11/22/10: I can't figure out a way to actually use the bytes without hurting performance.
                //  Apparently,
                // extracting files from a jar stored in memory via a JarInputStream is really slow compared
                // to using
                // a JarFile.  Will leave this as is for now.  --MS
                // jarFileContents = archive instanceof JarFileModule ? getJarFileContents((JarFileModule)
                // archive) : null;
                getJarFileContents((JarFileModule) archive);
            }
            Set<ModuleEntry> classFiles = getClassFiles(archive);
            removeClassFiles(classFiles, classModuleEntries);
            Set<ModuleEntry> sourceFiles = getSourceFiles(archive);
            Map<String, Object> allClassAndSourceFileContents = null;
            if (OPTIMIZE_JAR_FILE_IO) {
                // work in progress --MS
                // if (archive instanceof JarFileModule) {
                // final JarFileModule jfModule = (JarFileModule) archive;
                // final String name = jfModule.getJarFile().getName();
                // Map<String, Map<String, Long>> entrySizes = getEntrySizes(jfModule, name);
                // allClassAndSourceFileContents = getAllClassAndSourceFileContents(jarFileContents, name,
                // entrySizes);
                // }
                // jarFileContents = null;
            }
            loadAllClasses(classFiles, allClassAndSourceFileContents, isJMODType);
            loadAllSources(sourceFiles);
            classModuleEntries.addAll(classFiles);
            sourceModuleEntries.addAll(sourceFiles);
        }
    }

    /** get the contents of a jar file. if any IO exceptions occur, catch and return null. */
    private static void getJarFileContents(JarFileModule archive) {
        String jarFileName = archive.getJarFile().getName();
        InputStream s = null;
        try {
            File jarFile = (new FileProvider()).getFile(jarFileName);
            int bufferSize = 65536;
            s = new BufferedInputStream(new FileInputStream(jarFile), bufferSize);
            byte[] b = new byte[1024];
            int n = s.read(b);
            while (n != -1) {
                n = s.read(b);
            }
        } catch (IOException e) {
        } finally {
            try {
                if (s != null) {
                    s.close();
                }
            } catch (IOException e) {
            }
        }
    }

    private Set<ModuleEntry> getClassFiles(Module M) throws IOException {
        if (DEBUG_LEVEL > 0) {
            System.err.println("Get class files for " + M);
        }
        HashSet<ModuleEntry> result = HashSetFactory.make();
        for (ModuleEntry entry : Iterator2Iterable.make(M.getEntries())) {
            if (DEBUG_LEVEL > 0) {
                System.err.println("ClassLoaderImpl.getClassFiles:Got entry: " + entry);
            }
            if (entry.isClassFile()) {
                if (DEBUG_LEVEL > 0) {
                    System.err.println("result contains: " + entry);
                }
                result.add(entry);
            } else if (entry.isModuleFile()) {
                Set<ModuleEntry> s = getClassFiles(entry.asModule());
                removeClassFiles(s, result);
                result.addAll(s);
            } else {
                if (DEBUG_LEVEL > 0) {
                    System.err.println("Ignoring entry: " + entry);
                }
            }
        }
        return result;
    }

    // @Anemone support for springboot，该类会根据jar包下的文件名装载所有的类，因此遇到classes/开头的文件需要replace
    private void loadAllClasses(
            Collection<ModuleEntry> moduleEntries, Map<String, Object> fileContents, boolean isJMODType) {
        for (ModuleEntry entry : moduleEntries) {
            // java11 support for jmod files
            if (!entry.isClassFile()
                    || (isJMODType && entry.getClassName().startsWith("classes/module-info"))) {
                continue;
            }

            @SuppressWarnings("NonConstantStringShouldBeStringBuffer")
            String className = entry.getClassName().replace('.', '/');

            // java11 support for jmod files
            if (isJMODType && className.startsWith("classes/")) {
                className = className.replace("classes/", "");
            }

            // springboot jar or war support
            if (className.contains("/classes/")) {
                className = className.replaceFirst(".*?/classes/", "");
            }

            if (DEBUG_LEVEL > 0) {
                System.err.println("Consider " + className);
            }

            if (myExclusions != null && myExclusions.contains(className)) {
                if (DEBUG_LEVEL > 0) {
                    System.err.println("Excluding " + className);
                }
                continue;
            }

            ShrikeClassReaderHandle entryReader = new ShrikeClassReaderHandle(entry);

            className = 'L' + className;
            if (DEBUG_LEVEL > 0) {
                System.err.println("Load class " + className);
            }
            try {
                // 使用classloader装载该类
                TypeName T = TypeName.string2TypeName(className);
                if (loadedClasses.get(T) != null) {
                    Warnings.add(MultipleImplementationsWarning.create(className));
                } else if (super.getParent() != null && super.getParent().lookupClass(T) != null) {
                    Warnings.add(MultipleImplementationsWarning.create(className));
                } else {
                    // try to read from memory
                    ShrikeClassReaderHandle reader = entryReader;
                    if (fileContents != null) {
                        final Object contents = fileContents.get(entry.getName());
                        if (contents != null) {
                            // reader that uses the in-memory bytes
                            reader = new ByteArrayReaderHandle(entry, (byte[]) contents);
                        }
                    }
                    ShrikeClass tmpKlass = new ShrikeClass(reader, this, cha);
                    if (tmpKlass.getReference().getName().equals(T)) {
                        // always used the reader based on the entry after this point,
                        // so we can null out and re-read class file contents
                        loadedClasses.put(T, new ShrikeClass(entryReader, this, cha));
                        if (DEBUG_LEVEL > 1) {
                            System.err.println("put " + T + ' ');
                        }
                    } else {
                        Warnings.add(InvalidClassFile.create(className));
                    }
                }
            } catch (InvalidClassFileException e) {
                if (DEBUG_LEVEL > 0) {
                    System.err.println("Ignoring class " + className + " due to InvalidClassFileException");
                }
                Warnings.add(InvalidClassFile.create(className));
            }
        }
    }

    private static class InvalidClassFile extends Warning {

        final String className;

        InvalidClassFile(String className) {
            super(Warning.SEVERE);
            this.className = className;
        }

        @Override
        public String getMsg() {
            return getClass().toString() + " : " + className;
        }

        public static InvalidClassFile create(String className) {
            return new InvalidClassFile(className);
        }
    }

    static class ByteArrayReaderHandle extends ShrikeClassReaderHandle {
        public ByteArrayReaderHandle(ModuleEntry entry, byte[] contents) {
            super(entry);
            assert contents != null && contents.length > 0;
            this.contents = contents;
        }

        private byte[] contents;

        private boolean cleared;

        @Override
        public ClassReader get() throws InvalidClassFileException {
            if (cleared) {
                return super.get();
            } else {
                return new ClassReader(contents);
            }
        }

        @Override
        public void clear() {
            if (cleared) {
                super.clear();
            } else {
                contents = null;
                cleared = true;
            }
        }
    }

    /** A warning when we find more than one implementation of a given class name */
    private static class MultipleImplementationsWarning extends Warning {

        final String className;

        MultipleImplementationsWarning(String className) {
            super(Warning.SEVERE);
            this.className = className;
        }

        @Override
        public String getMsg() {
            return getClass().toString() + " : " + className;
        }

        public static MultipleImplementationsWarning create(String className) {
            return new MultipleImplementationsWarning(className);
        }
    }

    /** Remove from s any class file module entries which already are in t */
    private static void removeClassFiles(Set<ModuleEntry> s, Set<ModuleEntry> t) {
        s.removeAll(t);
    }

    /**
     * Return the Set of (ModuleEntry) source files found in a module.
     *
     * @param M the module
     * @return the Set of source files in the module
     */
    @SuppressWarnings("unused")
    private Set<ModuleEntry> getSourceFiles(Module M) throws IOException {
        if (DEBUG_LEVEL > 0) {
            System.err.println("Get source files for " + M);
        }
        HashSet<ModuleEntry> result = HashSetFactory.make();
        for (ModuleEntry entry : Iterator2Iterable.make(M.getEntries())) {
            if (DEBUG_LEVEL > 0) {
                System.err.println("consider entry for source information: " + entry);
            }
            if (entry.isSourceFile()) {
                if (DEBUG_LEVEL > 0) {
                    System.err.println("found source file: " + entry);
                }
                result.add(entry);
            } else if (entry.isModuleFile()) {
                result.addAll(getSourceFiles(entry.asModule()));
            }
        }
        return result;
    }
}
