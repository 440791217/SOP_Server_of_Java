package org.jsut.config;

import org.jsut.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    @Test
    void businessErrorsSetBothHttpStatusAndBodyCode() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler(), new GlobalResponseAdvice())
                .build();
        mvc.perform(get("/test-forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限"));
    }

    @RestController
    static class FailureController {
        @GetMapping("/test-forbidden")
        public Object forbidden() {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权限");
        }
    }
}
