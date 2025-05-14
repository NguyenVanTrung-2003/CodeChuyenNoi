package org.example.codechuyennoi.Application;

import org.example.codechuyennoi.Workflow.WorkflowCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Properties;

@SpringBootApplication
public class CodeChuyenNoiApplication {
	private static final Logger logger = LoggerFactory.getLogger(CodeChuyenNoiApplication.class);

	public static void main(String[] args) {
		// Cấu hình thủ công (nếu chưa dùng bean của Spring)
		Properties config = new Properties();
		try {
			config.load(CodeChuyenNoiApplication.class.getClassLoader().getResourceAsStream("application.properties"));
			logger.info("Đã tải cấu hình từ application.properties.");
		} catch (Exception e) {
			logger.error("Không thể tải cấu hình từ application.properties: {}", e.getMessage(), e);
			return;
		}

		WorkflowCoordinator coordinator = new WorkflowCoordinator(config);
		coordinator.processStory();
	}
}
