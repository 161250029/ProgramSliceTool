package com.example.demo.joanacore.classloader;

import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Enumeration;

//论文中的AppJavaClassLoader
public class SliceJavaClassloader extends ClassLoader {
    private File[] classpaths;

    public SliceJavaClassloader(File[] classpaths, ClassLoader parent) {
        super(parent);
        this.classpaths = classpaths;
    }

    public SliceJavaClassloader(File[] classpaths) {
        super();
        this.classpaths = classpaths;
    }

    public static void main(String[] args) throws MalformedURLException, ClassNotFoundException {
        ClassLoader classLoader = new SliceJavaClassloader(
                new File[]{
                        new File("C:\\Users\\x5651\\Documents\\bishe\\mvn_case\\joana.api-1.0.jar")});
        Class clz=classLoader.loadClass("com.ibm.wala.viz.PDFViewLauncher");
        Class clz2=classLoader.loadClass("com.ibm.wala.viz.DotUtil");
        System.out.println(clz);
        System.out.println(clz2);
    }

    //    protected Resource getResource(){
//
//    }
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
}