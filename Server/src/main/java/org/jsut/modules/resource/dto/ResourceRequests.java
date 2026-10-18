package org.jsut.modules.resource.dto;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.util.List;
public final class ResourceRequests {
 private ResourceRequests(){}
 public record Item(@Size(max=64) String code,@NotBlank @Size(max=255) String name,@Size(max=255) String location,
  @Positive Long owner_user_id,@NotNull @Min(0) @Max(2) Integer status,@Size(max=2000) String description,
  @Size(max=64) String type_code,@Size(max=128) String model,@Size(max=1000) String specification,@Size(max=32) String unit,
  @Positive Long model_id,@Positive Long station_id,@Size(max=128) String serial_number,@Min(0) Integer version,@Positive Long location_id,@Size(max=45) String ip_address,@Valid ConnectionSettings connection){}
 public record Version(@NotNull @Min(0) Integer version){}
 public record ConnectionSettings(@NotBlank String transport,@NotBlank String protocol,@Size(max=255) String host,@Min(1) @Max(65535) Integer port,
  @Size(max=1000) String path,@Size(max=128) String host_key,@Size(max=255) String device_key,@NotNull Boolean enabled,@Size(max=1300) String rtsp_url){}
 public record Connection(@NotBlank String transport,@NotBlank String protocol,@Size(max=255) String host,@Min(1) @Max(65535) Integer port,
  @Size(max=1000) String path,@Size(max=128) String host_key,@Size(max=255) String device_key,@NotNull Boolean enabled,@NotNull @Min(0) Integer device_version,@Size(max=1300) String rtsp_url){
  public ConnectionSettings settings(){return new ConnectionSettings(transport,protocol,host,port,path,host_key,device_key,enabled,rtsp_url);}
 }
 public record Task(@Size(max=64) String code,@NotBlank @Size(max=128) String name,@Positive Long station_id,
  @NotNull @Positive Long owner_user_id,@NotNull @Min(0) @Max(1000000) Integer sort_order,
  @Min(0) @Max(2) Integer status,@Size(max=2000) String description,@Positive Long sop_id,@Positive Long revision_id,@Min(0) Integer version){}
 public record TaskContent(@Size(max=64) String code,@NotNull @Min(0) Integer version){}
 public record PublishTask(@NotNull @Positive Long sop_id,@NotNull @Positive Long revision_id,@NotNull @Min(0) Integer revision_version,@NotNull @Min(0) Integer task_version){}
 public record Requirement(@NotNull @Positive Long model_id,@NotNull @Min(1) @Max(9999) Integer quantity,@Size(max=2000) String description){}
 public record Requirements(@Size(max=2000) String workplace_requirements,@NotNull @Size(max=100) List<@NotNull @Valid Requirement> items){}
}
