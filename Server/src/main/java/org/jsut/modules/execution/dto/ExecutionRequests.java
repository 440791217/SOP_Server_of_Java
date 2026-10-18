package org.jsut.modules.execution.dto;
import jakarta.validation.constraints.*;
public final class ExecutionRequests {
 private ExecutionRequests(){}
 public record Start(@NotNull @Positive Long project_id,@NotNull @Positive Long subproject_id,@NotNull @Positive Long task_id,@NotNull @Positive Long station_id,@NotNull @Positive Long revision_id,@NotNull @Min(0) Integer task_version,@NotBlank @Size(max=128) String product_code,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,64}") String request_key){}
 public record Action(@NotNull @Min(0) Integer version,@Size(max=2000) String reason){}
}
