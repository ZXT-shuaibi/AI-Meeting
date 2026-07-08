package com.hewei.hzyjy.xunzhi.career.resume.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceBO {
    private String company;
    private String industry;
    private String role;
    private LocalDate startDate;
    private LocalDate endDate;
    private String description;
    @Builder.Default
    private List<HighlightBO> highlights = new ArrayList<>();
}
