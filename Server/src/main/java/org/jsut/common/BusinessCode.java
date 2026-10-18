package org.jsut.common;

import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.time.format.*;

/** Validates client-generated codes; never allocates or rewrites identifiers. */
public final class BusinessCode {
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("uuuuMMddHHmm").withResolverStyle(ResolverStyle.STRICT);
    private BusinessCode(){}
    public static String validate(String value,String prefix){
        if(value==null||!value.matches(prefix+"-[0-9]{12}-[0-9A-F]{6}"))throw invalid();
        try{LocalDateTime.parse(value.substring(4,16),TIME);}catch(DateTimeParseException e){throw invalid();}
        return value;
    }
    private static BusinessException invalid(){return new BusinessException(HttpStatus.BAD_REQUEST,"编号格式不正确，应为类型前缀-年月日小时分钟-6位大写十六进制随机码");}
}
