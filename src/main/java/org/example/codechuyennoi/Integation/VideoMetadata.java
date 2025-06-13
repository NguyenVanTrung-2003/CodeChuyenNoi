package org.example.codechuyennoi.Integation;

import lombok.AllArgsConstructor;
import lombok.Data;
@AllArgsConstructor
@Data
public class VideoMetadata {
    private String title;
    private String description;
    public String getFullMetadata() {
        return title + " - " + description;
    }
}
