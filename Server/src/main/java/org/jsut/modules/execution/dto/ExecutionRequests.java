package org.jsut.modules.execution.dto;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
public final class ExecutionRequests {
 private ExecutionRequests(){}
 public record Start(@NotNull @Positive Long project_id,@NotNull @Positive Long subproject_id,@NotNull @Positive Long task_id,@NotNull @Positive Long station_id,@NotNull @Positive Long revision_id,@NotNull @Min(0) Integer task_version,@NotBlank @Size(max=128) String product_code,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,64}") String request_key){}
 public record Action(@NotNull @Min(0) Integer version,@Size(max=2000) String reason,@Size(max=64) String clock_session_id){ public Action(Integer version,String reason){this(version,reason,null);} }
 public record Clock(@NotBlank @Pattern(regexp="claim|heartbeat|release") String action,@NotNull @Positive Integer position,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,64}") String session_id,@NotNull @Min(0) Long sequence,@NotNull @Min(0) @Max(15000) Long elapsed_ms){}
 public record Detection(@NotBlank @Size(max=128) String class_name,@NotNull @DecimalMin("0") @DecimalMax("1") Double confidence,@NotNull @Size(min=4,max=4) List<@NotNull Double> bbox_xyxy){}
 public record Evidence(@NotNull @Min(0) Integer version,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,64}") String request_key,@NotNull @Positive Long camera_id,@Size(max=128) String model_id,@Size(max=128) String frame_id,@NotNull @Positive @Max(8192) Integer image_width,@NotNull @Positive @Max(8192) Integer image_height,@NotNull Instant captured_at,boolean automatic,@NotNull @Size(max=1000) List<@Valid Detection> detections,@Size(max=64) String clock_session_id){}
}
