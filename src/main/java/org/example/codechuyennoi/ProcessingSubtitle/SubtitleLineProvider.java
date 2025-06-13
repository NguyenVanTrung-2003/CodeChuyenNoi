package org.example.codechuyennoi.ProcessingSubtitle;

import java.util.List;

public interface SubtitleLineProvider {
    List<SubtitleLine> getLinesForChapter(String storyName, int chapterNumber);
}