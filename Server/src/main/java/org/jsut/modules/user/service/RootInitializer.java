package org.jsut.modules.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sop.bootstrap.root.enabled", havingValue = "true")
public class RootInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RootInitializer.class);
    private final RootInitializationService service;
    private final String password;

    public RootInitializer(RootInitializationService service,
                           @Value("${sop.bootstrap.root.password:}") String password) {
        this.service = service;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        log.info(service.initialize(password)
                ? "root 初始化完成，请关闭初始化开关并移除初始密码配置"
                : "root 已有密码，本次跳过初始化");
    }
}
