package com.jvm.console.machine;

import java.io.IOException;

/**
 * @Author:xyt
 * @Description:
 * @Date: 11:09 2019/8/2
 * @Modified By:
 */
public interface MachinePower {

    void open() throws IOException;

    void close();
}
