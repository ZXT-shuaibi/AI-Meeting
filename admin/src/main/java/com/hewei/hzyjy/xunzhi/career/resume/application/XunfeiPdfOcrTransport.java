package com.hewei.hzyjy.xunzhi.career.resume.application;

public interface XunfeiPdfOcrTransport {

    String start(String appId, String timestamp, String signature, byte[] pdfBytes, String exportFormat) throws Exception;

    String status(String appId, String timestamp, String signature, String taskNo) throws Exception;

    byte[] download(String url) throws Exception;
}
