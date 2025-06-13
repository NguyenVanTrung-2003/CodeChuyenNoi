package org.example.codechuyennoi.Notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service  // Đánh dấu đây là Spring Bean kiểu Service
public class NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    public void sendCompletionNotification(boolean success, String message) {
        logger.info("Gửi thông báo: Thành công = {}, Tin nhắn = {}", success, message);
        // TODO: Triển khai gửi thông báo qua email, API hoặc hệ thống khác
    }
}
