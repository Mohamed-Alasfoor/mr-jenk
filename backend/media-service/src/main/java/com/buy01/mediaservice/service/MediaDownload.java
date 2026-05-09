package com.buy01.mediaservice.service;

import com.buy01.mediaservice.model.MediaObject;
import org.springframework.core.io.Resource;

public record MediaDownload(
        MediaObject media,
        Resource resource
) {
}
