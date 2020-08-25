package com.example.demo.joanacore.datastructure;

public class Func {
    private String clazz;
    private String method;
    private String sig;
    public Func(){}
    public Func(String clazz, String method, String sig){
        if (clazz.contains("/")){
            throw new IllegalArgumentException("class name must be dot form");
        }
        this.clazz=clazz;
        this.method=method;
        this.sig=sig;
    }

    public String getClazz() {
        return clazz;
    }

    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getSig() {
        return sig;
    }

    public void setSig(String sig) {
        this.sig = sig;
    }
}
