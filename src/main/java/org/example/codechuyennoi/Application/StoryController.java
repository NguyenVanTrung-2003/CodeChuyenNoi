package org.example.codechuyennoi.Application;

import org.example.codechuyennoi.Workflow.WorkflowCoordinator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Controller
public class StoryController {
    private final WorkflowCoordinator workflowCoordinator;

    @Value("${story.start.chapter:1}")
    private int defaultStartChapter;

    @Value("${story.end.chapter:10}")
    private int defaultEndChapter;

    public StoryController(WorkflowCoordinator workflowCoordinator) {
        this.workflowCoordinator = workflowCoordinator;
    }
    @GetMapping("/login")
    public String loginPage() {
        return "login"; // Trỏ tới templates/login.html
    }


    @GetMapping("/start")
    public String showForm(Model model) {
        model.addAttribute("defaultStartChapter", defaultStartChapter);
        model.addAttribute("defaultEndChapter", defaultEndChapter);
        return "start"; // -> templates/start.html
    }

    @PostMapping("/start")
    public String processStory(
            @RequestParam @NotBlank(message = "URL không được để trống") String baseUrl,
            @RequestParam @NotBlank(message = "Tên truyện không được để trống") String storyName,
            @RequestParam @Positive(message = "Chương bắt đầu phải là số dương") int startChapter,
            @RequestParam String endChapterInput,
            @RequestParam(required = false) String videoTitle,
            @RequestParam(required = false) String videoDescription,
            Model model
    ) {
        try {
            // Kiểm tra endChapterInput
            int endChapter;
            if (endChapterInput == null || endChapterInput.trim().isEmpty()) {
                model.addAttribute("error", "Chương kết thúc không được để trống");
                return "start";
            }

            if ("*".equals(endChapterInput) || "-1".equals(endChapterInput)) {
                endChapter = 1000; // Giới hạn tối đa hợp lý (có thể điều chỉnh)
            } else {
                try {
                    endChapter = Integer.parseInt(endChapterInput);
                } catch (NumberFormatException e) {
                    model.addAttribute("error", "Chương kết thúc phải là số hợp lệ");
                    return "start";
                }
            }

            // Kiểm tra logic nghiệp vụ
            if (startChapter > endChapter) {
                model.addAttribute("error", "Chương bắt đầu phải nhỏ hơn hoặc bằng chương kết thúc");
                return "start";
            }
            if (endChapter <= 0 || startChapter <= 0) {
                model.addAttribute("error", "Chương phải là số dương");
                return "start";
            }
            workflowCoordinator.setVideoTitle(videoTitle);
            workflowCoordinator.setVideoDescription(videoDescription);
            workflowCoordinator.processMultipleChapters(startChapter, endChapter);
            workflowCoordinator.start(storyName, baseUrl);

            model.addAttribute("message", "Đã xử lý truyện thành công và đang theo dõi chương mới!");
            return "Result";

        } catch (Exception e) {
            model.addAttribute("error", "Đã xảy ra lỗi: " + e.getMessage());
            return "start";
        }
    }
}