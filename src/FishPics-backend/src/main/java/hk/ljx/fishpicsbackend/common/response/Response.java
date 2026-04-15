package hk.ljx.fishpicsbackend.common.response;

import lombok.Data;

@Data
public class Response<T> {

    /**
     * 状态码：1-成功
     */
    private Integer code;

    /**
     * 响应信息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    public Response(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
}
