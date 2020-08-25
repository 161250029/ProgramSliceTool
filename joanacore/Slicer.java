package com.example.demo.joanacore;

import com.example.demo.joanacore.datastructure.Func;
import com.example.demo.joanacore.datastructure.Location;
import com.example.demo.joanacore.exception.SlicerException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;


import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public interface Slicer {
    void config(List<File> appJars, List<URL> libJars, List<String> exclusions) throws ClassHierarchyException, IOException;
    List<Integer> computeSlice(Func func, Location line) throws SlicerException;
}
