package com.witpath.exception;

import lombok.Data;

@Data
public class BusinessException extends RuntimeException{
    private Integer code;
    public BusinessException(Integer code,String msg){
        super(msg);
        this.code = code;
    }
}