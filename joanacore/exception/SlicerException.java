package com.example.demo.joanacore.exception;


public class SlicerException extends Exception {
    private Exception rawException;
    public SlicerException(String s, Exception rawException) {
        super(s);
        this.rawException=rawException;
    }
    public Exception getRawException(){
        return rawException;
    }
}
