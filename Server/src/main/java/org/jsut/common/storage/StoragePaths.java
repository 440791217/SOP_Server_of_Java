package org.jsut.common.storage;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Relative business keys; the configured filesystem root is never persisted in the database. */
@Component
public class StoragePaths {
    private Path project(long project) { return Path.of("projects", "project-" + positive(project)); }
    private Path task(long project,long subproject,long task) { return project(project).resolve("subproject-"+positive(subproject)).resolve("task-"+positive(task)); }
    public String annotation(long project,Long subproject,Long task,long sop,int revision) {
        Path base=task==null?project(project).resolve("sop-"+positive(sop)):task(project,subproject,task);
        return key(base.resolve("annotations").resolve(String.format(Locale.ROOT,"v%03d",revision)));
    }
    public String execution(long project,long subproject,long task,LocalDate day,String product,long execution) {
        String safe=product.replaceAll("[^A-Za-z0-9_-]","_");
        safe=safe.substring(0,Math.min(48,safe.length()));if(safe.isBlank())safe="product";
        return key(task(project,subproject,task).resolve("executions").resolve(day.toString()).resolve(safe+"_exec-"+positive(execution)));
    }
    private long positive(long id) { if(id<=0)throw new IllegalArgumentException("Invalid storage identifier");return id; }
    private String key(Path path) { return path.toString().replace('\\','/'); }
}
