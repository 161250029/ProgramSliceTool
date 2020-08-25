package com.example.demo.joanacore.exception;

public class RootNodeNotFoundException extends NotFoundException{
    public RootNodeNotFoundException(Object element, Object container) {
        super(element, container);
    }
}
