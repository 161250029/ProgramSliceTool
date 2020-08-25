package com.example.demo.joanacore.exception;

public class NotFoundException extends Exception {
    final private Object objectNeedToFind;
    final private Object findFrom;

    public Object getFindFrom() {
        return findFrom;
    }

    public Object getObjectNeedToFind() {
        return objectNeedToFind;
    }

    public NotFoundException(Object objectNeedToFind, Object findFrom) {
        super(objectNeedToFind + " not found in "+findFrom);
        this.objectNeedToFind = objectNeedToFind;
        this.findFrom=findFrom;
    }
}
