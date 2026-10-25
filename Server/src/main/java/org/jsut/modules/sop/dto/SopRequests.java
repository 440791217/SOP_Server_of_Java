package org.jsut.modules.sop.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public final class SopRequests {
    private SopRequests() {}
    public record Create(@NotBlank @Size(max=64) @Pattern(regexp="SOP-[0-9]{12}-[0-9A-F]{6}",message="SOP编号格式不正确") String code,@NotBlank @Size(max=128) String name,
                         @NotNull @Positive Long managing_project_id,@NotNull @Positive Long owner_user_id) {}
    public record DocumentEdit(@NotBlank @Size(max=128) String name,@NotNull @Min(0) Integer version) {}
    public record RevisionEdit(@NotBlank @Size(max=128) String title,@Size(max=1000) String applicable_scope,
                               @Size(max=5000) String purpose,@Size(max=5000) String precautions,
                               @Size(max=1000) String revision_note,@NotNull @Min(0) Integer version) {}
    public record Draft(@NotNull @Positive Long source_revision_id,
                        @Schema(description="SOP基本资料version，不是源版本version") @NotNull @Min(0) Integer version) {}
    public record Version(@NotNull @Min(0) Integer version) {}
    public record Owner(@NotNull @Positive Long owner_user_id,@NotNull @Min(0) Integer version) {}
    public record StepCreate(@NotBlank @Size(max=128) String title,@NotBlank @Size(max=10000) String instruction,
                             @Size(max=5000) String precautions,@Size(max=5000) String check_requirements,
                             @DecimalMin("0") @Digits(integer=8,fraction=2) BigDecimal expected_duration_seconds,
                             @Schema(description="所属草稿的version") @NotNull @Min(0) Integer version,
                             @DecimalMin("0") @DecimalMax("3600") @Digits(integer=4,fraction=1) BigDecimal detection_wait_seconds,
                             Boolean voice_enabled,@Size(max=1000) String voice_text) {}
    public record StepEdit(@NotBlank @Size(max=128) String title,@NotBlank @Size(max=10000) String instruction,
                           @Size(max=5000) String precautions,@Size(max=5000) String check_requirements,
                           @DecimalMin("0") @Digits(integer=8,fraction=2) BigDecimal expected_duration_seconds,
                           @Schema(description="步骤version") @NotNull @Min(0) Integer version,
                           @Schema(description="所属草稿version") @NotNull @Min(0) Integer revision_version,
                           @DecimalMin("0") @DecimalMax("3600") @Digits(integer=4,fraction=1) BigDecimal detection_wait_seconds,
                           Boolean voice_enabled,@Size(max=1000) String voice_text) {}
    public record Visual(@NotNull @Positive Long attachment_id,@NotNull @Min(1) @Max(1) Integer schema_version,
                         @NotNull @Min(1) @Max(8192) Integer image_width,@NotNull @Min(1) @Max(8192) Integer image_height,
                         @NotNull @Size(max=100) List<java.util.Map<String,Object>> guide_items,
                         @NotNull @Size(max=100) List<java.util.Map<String,Object>> detection_regions) {}
    public record Detection(@NotNull Boolean enabled,@Positive Long camera_id,@Positive Long attachment_id,@NotNull Boolean confirmed) {}
    public record Authoring(@NotNull @Valid StepEdit step,@Size(max=20) List<@NotNull @Valid Visual> visuals,@Valid RevisionEdit revision,@Valid org.jsut.modules.resource.dto.ResourceRequests.Requirements resources,@Valid Detection detection,@Valid Visual visual) {}
    public record StepDelete(@NotNull @Min(0) Integer version,@NotNull @Min(0) Integer revision_version) {}
    public record Sort(@NotNull @Size(max=1000) List<@NotNull @Positive Long> ids,
                       @Schema(description="所属草稿version") @NotNull @Min(0) Integer version) {}
    public record Binding(@NotNull @Positive Long sop_id,@NotNull @Positive Long revision_id,
                          @NotNull @Min(0) Integer target_version,@Min(0) Integer version) {}
    public record BindingVersion(@NotNull @Min(0) Integer version,@NotNull @Min(0) Integer target_version) {}
    public record BindingSort(@NotNull @Size(max=1000) List<@NotNull @Positive Long> ids,@NotNull @Min(0) Integer target_version) {}
}
