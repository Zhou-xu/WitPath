package com.witpath.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 后端统一返回结果
 * @param <T>
 */
@Data
public class Result<T> implements Serializable {

    private Integer code; //编码：200成功，400 参数校验失败，500 服务器内部错误
    private String msg; //错误信息
    private T data; //数据

    public static <T> Result<T> success(T object) {
        Result<T> result = new Result<T>();
        result.data = object;
        result.code = 200;
        return result;
    }

    public static <T> Result<T> success(Integer code,T object) {
        Result<T> result = new Result<T>();
        result.data = object;
        result.code = code;
        return result;
    }

    public static <T> Result<T> error(Integer code,String msg) {
        Result result = new Result();
        result.msg = msg;
        result.code = code;
        return result;
    }

}
