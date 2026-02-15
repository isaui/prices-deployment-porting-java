package com.prices.api.dto.responses;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Serdeable
@NoArgsConstructor
@AllArgsConstructor
public class ProjectListResponse {
    private List<ProjectResponse> projects;
}
