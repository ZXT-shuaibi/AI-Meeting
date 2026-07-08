package com.hewei.hzyjy.xunzhi.career.resume.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactBO {
    private String phone;
    private String email;
    private String location;
    private String website;
}
