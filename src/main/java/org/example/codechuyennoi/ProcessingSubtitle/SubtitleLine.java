package org.example.codechuyennoi.ProcessVideo;
public class SubtitleLine {
    private final double start;
    private final double end;
    private final String text;

    public SubtitleLine(double start, double end, String text) {
        this.start = start;
        this.end = end;
        this.text = text;
    }

    public double getStart() {
        return start;
    }

    public double getEnd() {
        return end;
    }

    public String getText() {
        return text;
    }
}

