package vip.toby.rpc.entity;

public interface ICode {
    int getCode();

    String getMessage();

    static ICode build(int code, String message) {
        return new ICode() {
            @Override
            public int getCode() {
                return code;
            }

            @Override
            public String getMessage() {
                return message;
            }
        };
    }
}
