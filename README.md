# Video Streaming Service

Service xử lý video streaming với khả năng transcoding và chia sẻ video giữa các đơn vị.

## Tính năng chính

- Upload video lên MinIO
- Tự động detect chất lượng video
- Transcoding video thành nhiều chất lượng (1080p, 720p, 480p)
- Chia sẻ video giữa các đơn vị
- Xử lý bất đồng bộ với Kafka
- Lưu trữ metadata trên MongoDB

## Công nghệ sử dụng

- Spring Boot 2.7
- Java 17
- FFmpeg
- MinIO SDK
- Kafka
- MongoDB
- Docker & Kubernetes
- Maven

## Yêu cầu hệ thống

- JDK 17
- Maven 3.8+
- FFmpeg
- Docker
- Kubernetes cluster
- Git

## Cấu trúc project

```
video-streaming-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── video/
│   │   │           └── transcoding/
│   │   │               ├── config/
│   │   │               ├── controller/
│   │   │               ├── dto/
│   │   │               ├── model/
│   │   │               ├── repository/
│   │   │               ├── service/
│   │   │               └── VideoTranscodingApplication.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
├── k8s/
│   ├── app-deployment.yaml
│   ├── configmap.yaml
│   ├── kafka-deployment.yaml
│   ├── persistent-volumes.yaml
│   ├── secrets.yaml
│   └── zookeeper-deployment.yaml
├── Dockerfile
├── Dockerfile.kafka
├── Dockerfile.zookeeper
├── docker-compose.yml
├── pom.xml
├── README.md
└── Instruction.txt
```

## Hướng dẫn cài đặt

Chi tiết hướng dẫn cài đặt và triển khai được mô tả trong file [Instruction.txt](Instruction.txt)

## API Endpoints

- POST /api/videos/upload: Upload video
- GET /api/videos/{videoId}/url: Lấy URL video theo chất lượng
- POST /api/videos/share: Chia sẻ video
- POST /api/videos/share/sync: Đồng bộ video giữa các đơn vị

## License

MIT 