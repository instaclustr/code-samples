package com.example.a2a.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** A2A file part (bytes or URI). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilePart implements Part {
    public static final String KIND = "file";

    private String kind = KIND;
    private FileContent file;
    private Map<String, Object> metadata;

    public FilePart() {}

    @Override
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public FileContent getFile() { return file; }
    public void setFile(FileContent file) { this.file = file; }
    @Override
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FileContent {
        private String name;
        private String mimeType;
        private String bytes;   // base64
        private String uri;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        public String getBytes() { return bytes; }
        public void setBytes(String bytes) { this.bytes = bytes; }
        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }
    }
}
