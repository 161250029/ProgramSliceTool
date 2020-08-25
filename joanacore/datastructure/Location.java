package com.example.demo.joanacore.datastructure;


import java.util.Objects;

public class Location {

    final public String sourceFile;
    final public Integer startLine;
    final public Integer endLine;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location)) return false;
        Location location = (Location) o;
        return Objects.equals(sourceFile, location.sourceFile) &&
                Objects.equals(startLine, location.startLine) &&
                Objects.equals(endLine, location.endLine);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFile, startLine, endLine);
    }

    public Location(String sourceFile, Integer startLine, Integer endLine) {
        this.sourceFile = sourceFile;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    @Override
    public String toString() {
        return "Location{sourceFile='" + sourceFile + '\'' + ", [" + startLine + ", " + endLine + "]}";
    }

}
