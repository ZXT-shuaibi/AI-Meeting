package com.hewei.hzyjy.xunzhi.career.resume.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillBO {
    private String category;
    private String name;
    private String level;
    @Builder.Default
    private List<HighlightBO> highlights = new ArrayList<>();
}
