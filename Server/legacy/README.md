# 暂存的摄像头代码

camera/ 下的三个 Java 文件从原 src/main/java/org/jsut/task/camera 原样迁出，
只作后续摄像头模块的参考，不参与当前 Maven 编译和 Spring 扫描。

恢复时应放入 modules 下合适的业务模块，重新审查线程与原生资源生命周期，
再引入 JavaCV、Lombok 等实际需要的依赖。不要仅移动回来就启动生产采集。
