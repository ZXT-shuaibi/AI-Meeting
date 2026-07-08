package com.hewei.hzyjy.xunzhi.career.resume.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HighlightBO {
    private String type;
    private String relatedId;
    private String highlight;
}
