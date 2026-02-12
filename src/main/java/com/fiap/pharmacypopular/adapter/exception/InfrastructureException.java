package com.fiap.pharmacypopular.adapter.exception;

public class InfrastructureException extends RuntimeException{
    public InfrastructureException(String message){
        super(message);
    }

    public InfrastructureException(String message, Throwable e){
        super(message, e);
    }
}
