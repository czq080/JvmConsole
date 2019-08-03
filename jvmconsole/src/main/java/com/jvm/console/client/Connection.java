package com.jvm.console.client;

import java.io.IOException;

/**
 * @Author:xyt
 * @Description:
 * @Date: 11:33 2019/8/2
 * @Modified By:
 */
public interface Connection {
    void connect() throws IOException;

    void close() throws IOException;
}
