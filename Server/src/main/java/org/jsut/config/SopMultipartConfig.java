package org.jsut.config;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.*;
@Configuration
public class SopMultipartConfig {
    @Bean public MultipartConfigElement multipartConfigElement(){return new MultipartConfigElement("",6L*1024*1024,7L*1024*1024,0);}
}
