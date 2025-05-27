package org.example.codechuyennoi.Image;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Lớp giả lập việc sinh ảnh AI từ danh sách prompt (câu mô tả).
 * Hiện tại chỉ copy ảnh mẫu (placeholder) cho mỗi prompt.
 *
 * ✅ Ghi chú: Sau này nếu muốn dùng AI thật (ví dụ như Stable Diffusion, DALL·E, Midjourney, v.v.),
 * thay đoạn code trong phương thức `generateImages(...)`
 * và thay thế `copyPlaceholderImage(...)` bằng logic gọi API sinh ảnh.
 */
public class AiImageGenerator {

    // Đường dẫn đến ảnh mẫu giả lập (placeholder)
    private static final String PLACEHOLDER_IMAGE_PATH = "D:\\anhtrutien\\luctuyetki1.jpg"; // ảnh cố định dùng thay thế ảnh AI

    /**
     * Sinh danh sách ảnh ứng với danh sách prompt truyền vào.
     * Hiện tại thay thế bằng cách sao chép ảnh mẫu.
     *
     * @param prompts Danh sách prompt mô tả ảnh (ví dụ: "a dragon flying over a castle at sunset")
     * @return Danh sách đường dẫn tới ảnh được tạo
     */
    public List<String> generateImages(List<String> prompts) {
        List<String> imagePaths = new ArrayList<>();

        // Thư mục đầu ra để lưu ảnh
        File outputDir = new File("output/images");
        if (!outputDir.exists()) outputDir.mkdirs(); // tạo thư mục nếu chưa có

        // Lặp qua từng prompt và tạo ảnh tương ứng
        for (int i = 0; i < prompts.size(); i++) {
            String outputPath = outputDir.getPath() + "/ai_img_" + i + ".jpg";
            try {
                /**
                 * 🔁 Tạm thời: Copy ảnh mẫu cho mỗi prompt.
                 *
                 * ❗ Sau này khi dùng AI thật:
                 * 👉 Thay đoạn gọi `copyPlaceholderImage(outputPath)` bằng đoạn gọi API sinh ảnh thật.
                 * 👉 Ví dụ: gọi Replicate API với prompt, nhận URL ảnh kết quả, tải về và lưu vào outputPath.
                 */
                copyPlaceholderImage(outputPath);

                imagePaths.add(outputPath); // Thêm đường dẫn ảnh vừa tạo vào danh sách
            } catch (IOException e) {
                System.err.println("❌ Lỗi khi tạo ảnh mẫu: " + outputPath);
                e.printStackTrace();
            }
        }

        return imagePaths;
    }

    /**
     * Sao chép ảnh placeholder sang đường dẫn chỉ định.
     * Hiện tại là cách giả lập đơn giản để test toàn bộ pipeline.
     *
     * @param destinationPath Đường dẫn lưu ảnh đầu ra
     * @throws IOException nếu lỗi khi copy
     */
    private void copyPlaceholderImage(String destinationPath) throws IOException {
        File src = new File(PLACEHOLDER_IMAGE_PATH); // File ảnh mẫu nguồn
        File dest = new File(destinationPath);       // File đích mới
        Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING); // Ghi đè nếu tồn tại
    }
}
