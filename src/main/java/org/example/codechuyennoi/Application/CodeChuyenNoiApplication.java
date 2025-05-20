package org.example.codechuyennoi.Application;

import org.example.codechuyennoi.Workflow.WorkflowCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Properties;
import java.util.Scanner;

@SpringBootApplication
public class CodeChuyenNoiApplication {
	private static final Logger logger = LoggerFactory.getLogger(CodeChuyenNoiApplication.class);

	public static void main(String[] args) {
		Properties config = new Properties();
		try {
			config.load(CodeChuyenNoiApplication.class.getClassLoader().getResourceAsStream("application.properties"));
			logger.info("Đã tải cấu hình từ application.properties.");
		} catch (Exception e) {
			logger.error("Không thể tải cấu hình từ application.properties: {}", e.getMessage(), e);
			return;
		}

		Scanner scanner = new Scanner(System.in);
		System.out.print("Nhập URL trang web (để trống dùng mặc định từ cấu hình): ");
		String inputBaseUrl = scanner.nextLine().trim();

		// Nếu người dùng không nhập thì dùng giá trị mặc định từ cấu hình
		String baseUrl = inputBaseUrl.isEmpty()
				? config.getProperty("story.base.url", "https://truyenfull.vision/thay-phong-thuy/chuong-")
				: inputBaseUrl;
		System.out.print("Nhập tên truyện: ");
		String storyName = scanner.nextLine().trim();
		System.out.print("Nhập chương bắt đầu (mặc định 1): ");
		String startInput = scanner.nextLine().trim();
		int startChapter = startInput.isEmpty()
				? Integer.parseInt(config.getProperty("story.start.chapter", "1"))
				: Integer.parseInt(startInput);
		System.out.print("Nhập chương kết thúc (Hoặc nhập * để lấy tất cả): ");
		String endInput = scanner.nextLine().trim();
		int endChapter;
		if ("*".equals(endInput) || "-1".equals(endInput)) {
			endChapter = Integer.MAX_VALUE;
		} else if (endInput.isEmpty()) {
			endChapter = Integer.parseInt(config.getProperty("story.end.chapter", "10"));
		} else {
			endChapter = Integer.parseInt(endInput);
		}
		scanner.close();

		WorkflowCoordinator coordinator = new WorkflowCoordinator(config,storyName);

		// Gọi xử lý batch với thông tin đầu vào người dùng nhập
		coordinator.processMultipleChapters(storyName,baseUrl, startChapter, endChapter);
	}

}
