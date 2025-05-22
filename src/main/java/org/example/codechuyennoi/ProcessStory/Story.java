package org.example.codechuyennoi.ProcessStory;
public class Story {
    private int chapterNumber;
    private String processedText;

    public Story(int chapterNumber,String processedText) {
        this.processedText = processedText;
        this.chapterNumber = chapterNumber;
    }

    public String getProcessedText() {
        return processedText;
    }

    public void setProcessedText(String processedText) {
        this.processedText = processedText;
    }

    public int getChapterNumber() {
        return chapterNumber;
    }

    public void setChapterNumber(int chapterNumber) {
        this.chapterNumber = chapterNumber;
    }
}


