package com.example.demo.joanacore.classloader;

import com.ibm.wala.classLoader.ArrayClassLoader;
import com.ibm.wala.classLoader.ClassLoaderFactoryImpl;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.config.SetOfClasses;
import com.ibm.wala.util.warnings.Warning;
import com.ibm.wala.util.warnings.Warnings;

import java.io.IOException;
import java.lang.reflect.Constructor;

public class SliceClassLoaderFactory extends ClassLoaderFactoryImpl {
    /**
     * @param exclusions A set of classes that class loaders should pretend don't exist.
     */
    private SetOfClasses exclusions;
    public SliceClassLoaderFactory(SetOfClasses exclusions) {
        super(exclusions);
        this.exclusions=exclusions;
    }

    protected IClassLoader makeNewClassLoader(
            ClassLoaderReference classLoaderReference,
            IClassHierarchy cha,
            IClassLoader parent,
            AnalysisScope scope)
            throws IOException {
        String implClass = scope.getLoaderImpl(classLoaderReference);
        IClassLoader cl;
        if (implClass == null) {
            cl =
                    new SliceWalaClassLoader(
                            classLoaderReference, scope.getArrayClassLoader(), parent, exclusions, cha);
        } else
            try {
                // this is fragile. why are we doing things this way again?
                Class<?> impl = Class.forName(implClass);
                Constructor<?> ctor =
                        impl.getDeclaredConstructor(
                                new Class[] {
                                        ClassLoaderReference.class,
                                        IClassLoader.class,
                                        SetOfClasses.class,
                                        IClassHierarchy.class
                                });
                cl =
                        (IClassLoader)
                                ctor.newInstance(new Object[] {classLoaderReference, parent, exclusions, cha});
            } catch (Exception e) {
                try {
                    Class<?> impl = Class.forName(implClass);
                    Constructor<?> ctor =
                            impl.getDeclaredConstructor(
                                    new Class[] {
                                            ClassLoaderReference.class,
                                            ArrayClassLoader.class,
                                            IClassLoader.class,
                                            SetOfClasses.class,
                                            IClassHierarchy.class
                                    });
                    cl =
                            (IClassLoader)
                                    ctor.newInstance(
                                            new Object[] {
                                                    classLoaderReference, scope.getArrayClassLoader(), parent, exclusions, cha
                                            });
                } catch (Exception e2) {
                    System.err.println("failed to load impl class " + implClass);
                    e2.printStackTrace(System.err);
                    Warnings.add(InvalidClassLoaderImplementation.create(implClass));
                    cl =
                            new SliceWalaClassLoader(
                                    classLoaderReference, scope.getArrayClassLoader(), parent, exclusions, cha);
                }
            }
        cl.init(scope.getModules(classLoaderReference));
        return cl;
    }

    /** A waring when we fail to load an appropriate class loader implementation */
    private static class InvalidClassLoaderImplementation extends Warning {

        final String impl;

        InvalidClassLoaderImplementation(String impl) {
            super(Warning.SEVERE);
            this.impl = impl;
        }

        @Override
        public String getMsg() {
            return getClass().toString() + " : " + impl;
        }

        public static InvalidClassLoaderImplementation create(String impl) {
            return new InvalidClassLoaderImplementation(impl);
        }
    }
}
