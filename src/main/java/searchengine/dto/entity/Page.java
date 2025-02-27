package searchengine.dto.entity;

import lombok.Data;

@Data
public class Page {
    private String path;
    private String content;
    private int statusCode;
}
