package com.hewei.hzyjy.xunzhi.career.resume.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationBO {
    private String school;
    private String major;
    private String degree;
    private LocalDate startDate;
    private LocalDate endDate;
    private String description;
}
