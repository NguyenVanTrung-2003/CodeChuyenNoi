package org.example.codechuyennoi.ProcessStory;
public class Story {
    private int chapterNumber;
    private String processedText;
    private  String storyName;

    public Story(String storyName,int chapterNumber,String processedText) {
        this.processedText = processedText;
        this.chapterNumber = chapterNumber;
        this.storyName = storyName;
    }

    public String getStoryName() {
        return storyName;
    }

    public void setStoryName(String storyName) {
        this.storyName = storyName;
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


