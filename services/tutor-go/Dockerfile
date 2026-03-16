FROM golang:1.24-alpine AS builder
WORKDIR /app
COPY services/tutor-go /app/services/tutor-go
COPY content /app/content
RUN cd /app/services/tutor-go && go build -o /app/tutor-go .

FROM alpine:3.20
WORKDIR /app
COPY --from=builder /app/tutor-go /app/tutor-go
COPY --from=builder /app/content /app/content
EXPOSE 8080
ENV PORT=8080
ENV CONTENT_DIR=/app/content
CMD ["/app/tutor-go"]
