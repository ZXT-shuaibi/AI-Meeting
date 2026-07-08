package com.hewei.hzyjy.xunzhi.career.resume.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormatMetaBO {
    private String alignment;
    private Double lineSpacing;
    private String fontFamily;
    private String datePattern;
    private String hyperlinkStyle;
    private Boolean showAvatar;
    private Boolean showSocial;
    private Boolean twoColumnLayout;
    private LocaleConfigBO localeConfig;
}
